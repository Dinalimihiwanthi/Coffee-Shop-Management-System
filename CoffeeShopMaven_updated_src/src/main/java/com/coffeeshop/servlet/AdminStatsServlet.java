package com.coffeeshop.servlet;

import com.coffeeshop.util.AuthGuard;
import com.coffeeshop.util.DBUtil;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 * GET /api/admin/stats
 * Returns the four KPI numbers the admin dashboard shows on load:
 * today's sales total, total orders, active staff count, low stock item count.
 * Requires an OWNER session (see AdminLoginServlet).
 */
@WebServlet("/api/admin/stats")
public class AdminStatsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!AuthGuard.requireAdminSession(request, response)) {
            return;
        }

        JSONObject stats = new JSONObject();

        String sql =
            "SELECT " +
            " (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE DATE(order_date) = CURDATE()) AS todaySales, " +
            " (SELECT COUNT(*) FROM orders) AS totalOrders, " +
            " (SELECT COUNT(*) FROM staff) AS activeStaff, " +
            " (SELECT COUNT(*) FROM inventory WHERE quantity <= reorder_level) AS lowStockCount";

        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            if (rs.next()) {
                stats.put("todaySales", rs.getDouble("todaySales"));
                stats.put("totalOrders", rs.getInt("totalOrders"));
                stats.put("activeStaff", rs.getInt("activeStaff"));
                stats.put("lowStockCount", rs.getInt("lowStockCount"));
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            stats.put("error", e.getMessage());
        }

        response.getWriter().write(stats.toString());
    }
}