package com.coffeeshop.servlet;

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
 * GET /api/admin/reports/sales
 * Returns the most recent orders (with payment method + item count) for the
 * admin dashboard's "Financial & Sales Reports" ledger. Requires an OWNER
 * session. Limited to the most recent 200 orders so the ledger stays fast.
 */
@WebServlet("/api/admin/reports/sales")
public class AdminSalesReportServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!AuthGuard.requireAdminSession(request, response)) {
            return;
        }

        JSONArray sales = new JSONArray();

        String sql =
            "SELECT o.order_id, o.order_date, o.total_amount, p.payment_method, " +
            " (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.order_id) AS item_count " +
            "FROM orders o LEFT JOIN payments p ON p.order_id = o.order_id " +
            "ORDER BY o.order_date DESC LIMIT 200";

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd hh:mm a");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject row = new JSONObject();
                row.put("orderId", rs.getInt("order_id"));
                row.put("timestamp", fmt.format(rs.getTimestamp("order_date")));
                row.put("paymentMethod", rs.getString("payment_method") == null ? "N/A" : rs.getString("payment_method"));
                row.put("itemCount", rs.getInt("item_count"));
                row.put("totalAmount", rs.getDouble("total_amount"));
                sales.put(row);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(sales.toString());
    }
}