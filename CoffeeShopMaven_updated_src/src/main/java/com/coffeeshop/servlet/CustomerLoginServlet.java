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
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

import com.coffeeshop.util.DBUtil;

/**
 * POST /api/system/login  (application/x-www-form-urlencoded: phone_number, password)
 * Used by customer_login.html. Checks the customers table and starts a session.
 */
@WebServlet("/api/system/login")
public class CustomerLoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject result = new JSONObject();

        String phone = request.getParameter("phone_number");
        String password = request.getParameter("password");

        if (phone == null || password == null || phone.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("status", "fail");
            result.put("message", "Phone number and password are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "SELECT customer_id, full_name FROM customers WHERE phone = ? AND password = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, phone.trim());
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    HttpSession session = request.getSession(true);
                    session.setAttribute("customerId", rs.getInt("customer_id"));
                    session.setAttribute("customerName", rs.getString("full_name"));

                    result.put("status", "success");
                    result.put("message", "Welcome back, " + rs.getString("full_name") + "!");
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    result.put("status", "fail");
                    result.put("message", "Invalid phone number or password.");
                }
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("status", "fail");
            result.put("message", "Server error: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }
}