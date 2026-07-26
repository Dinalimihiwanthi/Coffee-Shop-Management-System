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

import org.json.JSONObject;

import com.coffeeshop.util.DBUtil;
import com.coffeeshop.util.StaffPrincipal;
import com.coffeeshop.util.TokenStore;

/**
 * POST /api/auth/login
 * Body: {"username": "...", "password": "..."}
 *
 * Shared login endpoint for cashier, barista, and warehouse staff (and owner/admin).
 * Used by pos_cashier_login.html, barista_kds_login.html, warehouse_inventory_login.html.
 * Returns a bearer token the frontend stores in localStorage as "jwtToken".
 *
 * Role mapping (DB enum -> frontend role string):
 *   OWNER     -> ROLE_ADMIN
 *   CASHIER   -> ROLE_CASHIER
 *   BARISTA   -> ROLE_KITCHEN_STAFF
 *   WAREHOUSE -> ROLE_WAREHOUSE_STAFF
 *   MANAGER   -> ROLE_MANAGER (not in the current staff enum -- add it with an
 *                ALTER TABLE if/when you need real manager accounts; until then
 *                use an OWNER account for manager-only pages)
 */
@WebServlet("/api/auth/login")
public class AuthServlet extends HttpServlet {

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
            result.put("message", "Username and password are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "SELECT staff_id, full_name, username, role FROM staff WHERE username = ? AND password = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int staffId = rs.getInt("staff_id");
                    String dbRole = rs.getString("role");
                    String frontendRole = mapRole(dbRole);

                    StaffPrincipal principal = new StaffPrincipal(staffId, rs.getString("username"), dbRole);
                    String token = TokenStore.issueToken(principal);

                    result.put("token", token);
                    result.put("role", frontendRole);
                    result.put("username", rs.getString("username"));
                    result.put("staffId", staffId);
                    result.put("fullName", rs.getString("full_name"));
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    result.put("message", "Invalid username or password.");
                }
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("message", "Server error: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    private String mapRole(String dbRole) {
        switch (dbRole) {
            case "OWNER": return "ROLE_ADMIN";
            case "CASHIER": return "ROLE_CASHIER";
            case "BARISTA": return "ROLE_KITCHEN_STAFF";
            case "WAREHOUSE": return "ROLE_WAREHOUSE_STAFF";
            case "MANAGER": return "ROLE_MANAGER";
            default: return dbRole;
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