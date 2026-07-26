package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
 * GET  /api/inventory          -> real inventory table (raw ingredients: Coffee Beans, Milk, ...)
 * POST /api/inventory/adjust   body: {"itemId": "1", "adjustmentType": "IN"|"OUT", "quantity": 5, "notes": "..."}
 *
 * Used by warehouse_inventory.html (its JS constant was renamed from the
 * colliding "/api/menu/stock" to point here -- see project README).
 * Requires a WAREHOUSE (or OWNER) bearer token from AuthServlet.
 */
@WebServlet("/api/inventory/*")
public class InventoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "WAREHOUSE") == null) {
            return;
        }

        JSONArray items = new JSONArray();

        String sql = "SELECT inventory_id, item_name, quantity, unit, reorder_level FROM inventory ORDER BY inventory_id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("itemId", String.valueOf(rs.getInt("inventory_id")));
                item.put("itemName", rs.getString("item_name"));
                item.put("category", rs.getString("unit")); // no dedicated category column; unit shown instead
                item.put("stockQuantity", rs.getDouble("quantity"));
                item.put("reorderLevel", rs.getDouble("reorder_level"));
                items.put(item);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(items.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "WAREHOUSE") == null) {
            return;
        }

        JSONObject result = new JSONObject();

        String pathInfo = request.getPathInfo(); // expect "/adjust"
        if (pathInfo == null || !pathInfo.equals("/adjust")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            result.put("message", "Unknown inventory endpoint.");
            response.getWriter().write(result.toString());
            return;
        }

        JSONObject body = readJsonBody(request);
        int itemId;
        try {
            itemId = Integer.parseInt(body.optString("itemId", ""));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "Invalid itemId.");
            response.getWriter().write(result.toString());
            return;
        }
        String adjustmentType = body.optString("adjustmentType", "IN").toUpperCase();
        double qty = body.optDouble("quantity", 0);
        double delta = "OUT".equals(adjustmentType) ? -qty : qty;

        String sql = "UPDATE inventory SET quantity = GREATEST(quantity + ?, 0) WHERE inventory_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, itemId);
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