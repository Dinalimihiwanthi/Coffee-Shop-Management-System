package com.coffeeshop.servlet;

import com.coffeeshop.util.DBUtil;
import java.io.IOException;
import java.io.BufferedReader;
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

/**
 * POST /api/admin/login
 * Body: {"username": "admin", "password": "admin123"}
 *
 * Checks the staff table for a matching username + password with role = OWNER.
 * On success starts a session and returns staff info as JSON.
 *
 * NOTE: staff.password is stored in plain text in the current schema.
 * That's fine to get things working end-to-end, but before this goes anywhere
 * real, passwords should be hashed (e.g. BCrypt) and compared with a hash check
 * instead of a plain "=" in SQL.
 */
@WebServlet("/api/admin/login")
public class AdminLoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JSONObject body = readJsonBody(request);
        String username = body.optString("username", "").trim();
        String password = body.optString("password", "");

        JSONObject result = new JSONObject();

        if (username.isEmpty() || password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "Username and password are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "SELECT staff_id, full_name, username, role FROM staff "
                + "WHERE username = ? AND password = ? AND role = 'OWNER'";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    HttpSession session = request.getSession(true);
                    session.setAttribute("staffId", rs.getInt("staff_id"));
                    session.setAttribute("username", rs.getString("username"));
                    session.setAttribute("role", rs.getString("role"));

                    result.put("success", true);
                    result.put("staffId", rs.getInt("staff_id"));
                    result.put("fullName", rs.getString("full_name"));
                    result.put("role", rs.getString("role"));
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    result.put("success", false);
                    result.put("message", "Invalid username or password, or account is not an admin.");
                }
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "Server error: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    private JSONObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String raw = sb.toString().trim();
        return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }
}
