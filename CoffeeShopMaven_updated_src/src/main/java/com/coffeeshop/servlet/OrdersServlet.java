package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
                int customerId = getOrCreateWalkInCustomer(conn);

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