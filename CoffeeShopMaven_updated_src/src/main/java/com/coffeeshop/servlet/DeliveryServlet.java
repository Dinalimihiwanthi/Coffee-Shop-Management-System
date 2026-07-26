package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.coffeeshop.util.DBUtil;

/**
 * POST /api/customer/delivery
 * Body: {"orderId": 12, "deliveryMethod": "Store Pickup" | "PickMe" | "Uber", "address": "..."}
 *
 * Used by deliver.html's "Confirm & Generate Invoice" button. Inserts one row
 * into `deliveries`, linked to the order created earlier by
 * CustomerOrdersServlet. "Store Pickup" maps to delivery_type=PICKUP with a
 * null address; anything else maps to DELIVERY with the given address.
 */
@WebServlet("/api/customer/delivery")
public class DeliveryServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject result = new JSONObject();

        JSONObject body = readJsonBody(request);
        int orderId;
        try {
            orderId = body.getInt("orderId");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "orderId is required.");
            response.getWriter().write(result.toString());
            return;
        }

        String deliveryMethod = body.optString("deliveryMethod", "Store Pickup");
        String address = body.optString("address", null);
        boolean isPickup = "Store Pickup".equalsIgnoreCase(deliveryMethod);

        String sql = "INSERT INTO deliveries (order_id, delivery_type, delivery_address, delivery_status) " +
                     "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ps.setString(2, isPickup ? "PICKUP" : "DELIVERY");
            ps.setString(3, isPickup ? null : address);
            ps.setString(4, isPickup ? "READY_FOR_PICKUP" : "PENDING");
            ps.executeUpdate();

            result.put("success", true);

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "Could not save delivery details: " + e.getMessage());
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
