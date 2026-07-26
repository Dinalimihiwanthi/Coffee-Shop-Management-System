package com.coffeeshop.servlet;

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

import com.coffeeshop.util.DBUtil;

/**
 * GET /api/menu/stock -> real menu_items, shaped for pos_cashier.html / menu.html / admin dashboard.
 *
 * NOTE: menu_items has no per-item stock count in the schema (only inventory,
 * which tracks raw ingredients like "Coffee Beans" / "Milk", with no
 * recipe-to-ingredient mapping). Until that link exists, stockQuantity is
 * derived from is_available: 999 if available, 0 if not. Good enough to make
 * the POS/menu UI functional; a real per-item stock count would need a new
 * table linking menu_items to inventory with quantities-per-recipe.
 */
@WebServlet("/api/menu/stock")
public class MenuStockServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONArray items = new JSONArray();

        String sql = "SELECT menu_id, item_name, category, price, image_path, is_available FROM menu_items ORDER BY menu_id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("itemId", String.valueOf(rs.getInt("menu_id")));
                item.put("itemName", rs.getString("item_name"));
                item.put("category", rs.getString("category"));
                item.put("price", rs.getDouble("price"));
                item.put("imageUrl", rs.getString("image_path"));
                item.put("stockQuantity", rs.getBoolean("is_available") ? 999 : 0);
                items.put(item);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(items.toString());
    }
}