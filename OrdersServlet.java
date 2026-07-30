package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coffeeshop.util.AuthGuard;
import com.coffeeshop.util.DBUtil;
import com.coffeeshop.util.StaffPrincipal;

/**
 * GET  /api/orders
 * Returns currently open orders (PENDING/PREPARING/READY), one entry per
 * order, each carrying its customer's name and its own item list/total so
 * the cashier terminal can display orders one customer at a time instead of
 * a single shared cart.
 *
 * POST /api/orders
 * Body: {"paymentMethod": "CASH", "items": [{"itemId": "1", "quantity": 2, "unitPrice": 250.00}, ...]}
 *
 * Cashier POS checkout. Requires a valid bearer token from AuthServlet with
 * role CASHIER (or OWNER). Since orders.customer_id is a required FK and the
 * POS has no logged-in customer, walk-in sales are attached to a single
 * auto-created "Walk-in Customer" row (phone 0000000000).
 */
@WebServlet("/api/orders")
public class OrdersServlet extends HttpServlet {

    private static final String WALKIN_PHONE = "0000000000";
    private static final double TAX_RATE = 0.08;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "CASHIER") == null) {
            return;
        }

        String sql = "SELECT o.order_id, o.order_status, o.order_date, " +
                     "c.customer_id, c.full_name, c.phone " +
                     "FROM orders o JOIN customers c ON c.customer_id = o.customer_id " +
                     "WHERE o.order_status IN ('PENDING','PREPARING','READY') " +
                     "ORDER BY o.order_id ASC";

        String itemsSql = "SELECT oi.quantity, oi.subtotal, mi.item_name, mi.price " +
                           "FROM order_items oi JOIN menu_items mi ON mi.menu_id = oi.menu_id " +
                           "WHERE oi.order_id = ?";

        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm a");
        JSONArray ordersOut = new JSONArray();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             PreparedStatement itemPs = conn.prepareStatement(itemsSql)) {

            while (rs.next()) {
                int orderId = rs.getInt("order_id");

                JSONObject order = new JSONObject();
                order.put("orderId", orderId);
                order.put("customerId", rs.getInt("customer_id"));
                order.put("customerName", rs.getString("full_name"));
                order.put("phone", rs.getString("phone"));
                order.put("status", rs.getString("order_status"));
                order.put("orderTime", fmt.format(rs.getTimestamp("order_date")));

                JSONArray itemsArr = new JSONArray();
                double subtotal = 0;

                itemPs.setInt(1, orderId);
                try (ResultSet itemsRs = itemPs.executeQuery()) {
                    while (itemsRs.next()) {
                        JSONObject item = new JSONObject();
                        item.put("itemName", itemsRs.getString("item_name"));
                        item.put("quantity", itemsRs.getInt("quantity"));
                        item.put("unitPrice", itemsRs.getDouble("price"));
                        double lineSubtotal = itemsRs.getDouble("subtotal");
                        item.put("subtotal", lineSubtotal);
                        subtotal += lineSubtotal;
                        itemsArr.put(item);
                    }
                }

                double tax = subtotal * TAX_RATE;
                order.put("items", itemsArr);
                order.put("subtotal", subtotal);
                order.put("tax", tax);
                order.put("total", subtotal + tax);

                ordersOut.put(order);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("message", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(ordersOut.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject result = new JSONObject();

        StaffPrincipal staff = AuthGuard.requireRole(request, response, "CASHIER");
        if (staff == null) {
            return;
        }

        JSONObject body = readJsonBody(request);
        String paymentMethod = body.optString("paymentMethod", "CASH").toUpperCase();
        String customerName = body.optString("customerName", "").trim();
        JSONArray items = body.optJSONArray("items");

        if (items == null || items.length() == 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "Cart is empty.");
            response.getWriter().write(result.toString());
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int customerId = customerName.isEmpty()
                        ? getOrCreateWalkInCustomer(conn)
                        : createNamedWalkInCustomer(conn, customerName);

                double total = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    total += it.getInt("quantity") * it.getDouble("unitPrice");
                }

                int orderId;
                String insertOrder = "INSERT INTO orders (customer_id, staff_id, order_status, total_amount) VALUES (?, ?, 'PENDING', ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setInt(2, staff.staffId);
                    ps.setDouble(3, total);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getInt(1);
                    }
                }

                String insertItem = "INSERT INTO order_items (order_id, menu_id, quantity, subtotal) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        int menuId = Integer.parseInt(it.getString("itemId"));
                        int qty = it.getInt("quantity");
                        double unitPrice = it.getDouble("unitPrice");
                        ps.setInt(1, orderId);
                        ps.setInt(2, menuId);
                        ps.setInt(3, qty);
                        ps.setDouble(4, qty * unitPrice);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                String insertPayment = "INSERT INTO payments (order_id, amount, payment_method, payment_status) VALUES (?, ?, ?, 'PAID')";
                try (PreparedStatement ps = conn.prepareStatement(insertPayment)) {
                    ps.setInt(1, orderId);
                    ps.setDouble(2, total);
                    ps.setString(3, paymentMethod);
                    ps.executeUpdate();
                }

                conn.commit();

                result.put("success", true);
                result.put("orderId", orderId);
                result.put("totalAmount", total);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "Checkout failed: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    /**
     * Creates a fresh customer row for a named walk-in so each one shows up
     * individually in the Customer orders panel, instead of collapsing into
     * the single shared "Walk-in Customer" row. Uses a synthetic unique
     * phone value since `customers.phone` has a UNIQUE constraint and named
     * walk-ins don't provide a real one.
     */
    private int createNamedWalkInCustomer(Connection conn, String fullName) throws SQLException {
        String syntheticPhone = "WI-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
        String insert = "INSERT INTO customers (full_name, phone, password) VALUES (?, ?, 'N/A')";
        try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, syntheticPhone);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private int getOrCreateWalkInCustomer(Connection conn) throws SQLException {
        String find = "SELECT customer_id FROM customers WHERE phone = ?";
        try (PreparedStatement ps = conn.prepareStatement(find)) {
            ps.setString(1, WALKIN_PHONE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("customer_id");
            }
        }
        String insert = "INSERT INTO customers (full_name, phone, password) VALUES ('Walk-in Customer', ?, 'N/A')";
        try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, WALKIN_PHONE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private JSONObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        String raw = sb.toString().trim();
        return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }
}
