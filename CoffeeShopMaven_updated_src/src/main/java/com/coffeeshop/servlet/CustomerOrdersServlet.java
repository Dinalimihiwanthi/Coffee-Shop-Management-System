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
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coffeeshop.util.DBUtil;

/**
 * POST /api/customer/orders
 * Body: {"items": [{"id": "3", "name": "...", "price": 380.00}, ...], "paymentMethod": "Counter Cash" | "Credit/Debit Card"}
 *
 * Used by pay.html after menu.html builds the cart. Uses the logged-in
 * customer's session (set by CustomerLoginServlet) if present; otherwise
 * falls back to the same "Walk-in Customer" placeholder row the POS uses,
 * so checkout still works for guests who skipped login/signup.
 */
@WebServlet("/api/customer/orders")
public class CustomerOrdersServlet extends HttpServlet {

    private static final String WALKIN_PHONE = "0000000000";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject result = new JSONObject();

        JSONObject body = readJsonBody(request);
        JSONArray items = body.optJSONArray("items");
        String paymentMethodRaw = body.optString("paymentMethod", "Counter Cash");
        String paymentMethod = paymentMethodRaw.toLowerCase().contains("card") ? "CARD" : "CASH";

        if (items == null || items.length() == 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "No items selected.");
            response.getWriter().write(result.toString());
            return;
        }

        HttpSession session = request.getSession(false);
        Integer sessionCustomerId = (session != null) ? (Integer) session.getAttribute("customerId") : null;

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int customerId = (sessionCustomerId != null) ? sessionCustomerId : getOrCreateWalkInCustomer(conn);

                double total = 0;
                for (int i = 0; i < items.length(); i++) {
                    total += items.getJSONObject(i).getDouble("price");
                }

                int orderId;
                String insertOrder = "INSERT INTO orders (customer_id, order_status, total_amount) VALUES (?, 'PENDING', ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setDouble(2, total);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getInt(1);
                    }
                }

                String insertItem = "INSERT INTO order_items (order_id, menu_id, quantity, subtotal) VALUES (?, ?, 1, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        ps.setInt(1, orderId);
                        ps.setInt(2, Integer.parseInt(it.get("id").toString()));
                        ps.setDouble(3, it.getDouble("price"));
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

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "Could not place order: " + e.getMessage());
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