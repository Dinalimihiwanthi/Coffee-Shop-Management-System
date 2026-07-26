package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/**
 * GET   /api/orders/kitchen                -> live queue (PENDING/PREPARING/READY orders)
 * PATCH /api/orders/kitchen/{orderId}/status  body: {"status": "PREPARING"}  -> updates orders.order_status
 *
 * Used by barista_kds.html. HttpServlet has no built-in doPatch, so PATCH is
 * routed manually in service(). Requires a BARISTA (or OWNER) bearer token.
 */
@WebServlet("/api/orders/kitchen/*")
public class OrdersKitchenServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) {
            handleStatusUpdate(request, response);
        } else {
            super.service(request, response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "BARISTA") == null) {
            return;
        }

        JSONArray orders = new JSONArray();

        String sql = "SELECT o.order_id, o.order_status, o.order_date, p.payment_method " +
                     "FROM orders o LEFT JOIN payments p ON p.order_id = o.order_id " +
                     "WHERE o.order_status IN ('PENDING','PREPARING','READY') " +
                     "ORDER BY o.order_date ASC";

        String itemsSql = "SELECT oi.quantity, mi.item_name FROM order_items oi " +
                           "JOIN menu_items mi ON mi.menu_id = oi.menu_id WHERE oi.order_id = ?";

        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm a");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            try (PreparedStatement itemPs = conn.prepareStatement(itemsSql)) {
                while (rs.next()) {
                    JSONObject order = new JSONObject();
                    int orderId = rs.getInt("order_id");
                    order.put("orderId", String.valueOf(orderId));
                    order.put("status", rs.getString("order_status"));
                    order.put("orderTime", fmt.format(rs.getTimestamp("order_date")));
                    order.put("paymentMethod", rs.getString("payment_method") == null ? "CASH" : rs.getString("payment_method"));

                    JSONArray itemsArr = new JSONArray();
                    itemPs.setInt(1, orderId);
                    try (ResultSet itemsRs = itemPs.executeQuery()) {
                        while (itemsRs.next()) {
                            JSONObject item = new JSONObject();
                            item.put("quantity", itemsRs.getInt("quantity"));
                            item.put("itemName", itemsRs.getString("item_name"));
                            itemsArr.put(item);
                        }
                    }
                    order.put("items", itemsArr);
                    orders.put(order);
                }
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(orders.toString());
    }

    private void handleStatusUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "BARISTA") == null) {
            return;
        }

        JSONObject result = new JSONObject();

        // pathInfo looks like "/12/status"
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "Missing order id in path.");
            response.getWriter().write(result.toString());
            return;
        }
        String[] parts = pathInfo.split("/");
        // parts[0] is "" because pathInfo starts with "/"
        String orderIdStr = parts.length > 1 ? parts[1] : null;

        JSONObject body = readJsonBody(request);
        String newStatus = body.optString("status", "").toUpperCase();

        if (orderIdStr == null || newStatus.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "orderId and status are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "UPDATE orders SET order_status = ? WHERE order_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, Integer.parseInt(orderIdStr));
            int rows = ps.executeUpdate();
            result.put("success", rows > 0);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        response.getWriter().write(result.toString());
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