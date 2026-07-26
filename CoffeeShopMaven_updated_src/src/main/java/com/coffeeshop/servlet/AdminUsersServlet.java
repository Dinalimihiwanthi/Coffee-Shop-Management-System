package com.coffeeshop.servlet;

import com.coffeeshop.util.AuthGuard;
import com.coffeeshop.util.DBUtil;
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

/**
 * GET    /api/admin/users        -> list all staff accounts
 * POST   /api/admin/users        -> create a new staff account
 * DELETE /api/admin/users/{id}   -> revoke (delete) a staff account
 *
 * All three require an OWNER session (see AdminLoginServlet).
 *
 * The frontend's role dropdown (ROLE_CUSTOMER, ROLE_KITCHEN_STAFF, ROLE_CASHIER,
 * ROLE_WAREHOUSE_STAFF, ROLE_MANAGER, ROLE_ADMIN) is broader than the current
 * `staff.role` enum ('OWNER','CASHIER','BARISTA','WAREHOUSE') and the separate
 * `customers` table. This servlet maps the frontend roles onto the existing
 * staff enum (CUSTOMER accounts go in the customers table instead) -- see the
 * README for the roles decision that still needs to be made (MANAGER isn't in
 * the schema yet).
 */
@WebServlet("/api/admin/users/*")
public class AdminUsersServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!AuthGuard.requireAdminSession(request, response)) {
            return;
        }

        JSONArray users = new JSONArray();

        String sql = "SELECT staff_id, full_name, username, role FROM staff ORDER BY staff_id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject u = new JSONObject();
                u.put("userId", rs.getInt("staff_id"));
                u.put("username", rs.getString("username"));
                u.put("role", rs.getString("role"));
                users.put(u);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(users.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!AuthGuard.requireAdminSession(request, response)) {
            return;
        }

        JSONObject body = readJsonBody(request);
        String username = body.optString("username", "").trim();
        String password = body.optString("password", "");
        String role = body.optString("role", "").trim();

        JSONObject result = new JSONObject();

        if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "username, password and role are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String dbRole = mapFrontendRole(role);
        if (dbRole == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "Role '" + role + "' has no matching staff role yet.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "INSERT INTO staff (full_name, username, password, role) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.setString(4, dbRole);
            ps.executeUpdate();

            result.put("success", true);

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!AuthGuard.requireAdminSession(request, response)) {
            return;
        }

        JSONObject result = new JSONObject();

        // pathInfo looks like "/7"
        String pathInfo = request.getPathInfo();
        String idStr = (pathInfo != null && pathInfo.length() > 1) ? pathInfo.substring(1) : null;

        int staffId;
        try {
            staffId = Integer.parseInt(idStr);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "A valid staff id is required in the URL, e.g. /api/admin/users/7");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "DELETE FROM staff WHERE staff_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, staffId);
            int rows = ps.executeUpdate();
            result.put("success", rows > 0);
            if (rows == 0) {
                result.put("message", "No staff account found with that id.");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            // A staff row referenced by existing orders can't be deleted (FK constraint).
            result.put("message", "Could not remove this account -- it may still be referenced by past orders: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    private String mapFrontendRole(String frontendRole) {
        switch (frontendRole) {
            case "ROLE_ADMIN": return "OWNER";
            case "ROLE_CASHIER": return "CASHIER";
            case "ROLE_KITCHEN_STAFF": return "BARISTA";
            case "ROLE_WAREHOUSE_STAFF": return "WAREHOUSE";
            case "ROLE_MANAGER": return "MANAGER";
            default: return null; // ROLE_CUSTOMER isn't a staff account -- customers sign up via /api/system/signup instead
        }
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