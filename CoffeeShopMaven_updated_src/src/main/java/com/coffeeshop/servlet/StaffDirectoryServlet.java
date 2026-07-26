package com.coffeeshop.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

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
 * GET  /api/staff  -> lists employee profiles for manager_dashboard.html's "Staff Operations" tab
 * POST /api/staff   body: {"staffId","firstName","lastName","age","phoneNo"} -> adds one
 *
 * This is a separate HR-style roster (table `staff_directory`) from the
 * `staff` table used for login (staff_id/username/password/role). The
 * manager dashboard's "Add Staff Member" form only collects a name/age/phone,
 * with no username or password, so it can't write into the login table
 * without breaking its NOT NULL/UNIQUE constraints -- hence the separate table.
 * Requires a MANAGER (or OWNER) bearer token from AuthServlet.
 */
@WebServlet("/api/staff")
public class StaffDirectoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "MANAGER") == null) {
            return;
        }

        JSONArray staffList = new JSONArray();
        String sql = "SELECT staff_ref_id, first_name, last_name, age, phone_no FROM staff_directory ORDER BY staff_ref_id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject s = new JSONObject();
                s.put("staffId", rs.getString("staff_ref_id"));
                s.put("firstName", rs.getString("first_name"));
                s.put("lastName", rs.getString("last_name"));
                s.put("age", rs.getInt("age"));
                s.put("phoneNo", rs.getString("phone_no"));
                staffList.put(s);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(staffList.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "MANAGER") == null) {
            return;
        }

        JSONObject body = readJsonBody(request);
        String staffId = body.optString("staffId", "").trim();
        String firstName = body.optString("firstName", "").trim();
        String lastName = body.optString("lastName", "").trim();
        String phoneNo = body.optString("phoneNo", "").trim();
        int age = body.optInt("age", 0);

        JSONObject result = new JSONObject();
        if (staffId.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || phoneNo.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "staffId, firstName, lastName and phoneNo are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "INSERT INTO staff_directory (staff_ref_id, first_name, last_name, age, phone_no) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, staffId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setInt(4, age);
            ps.setString(5, phoneNo);
            ps.executeUpdate();

            result.put("staffId", staffId);
            result.put("firstName", firstName);
            result.put("lastName", lastName);
            result.put("age", age);
            result.put("phoneNo", phoneNo);

        } catch (SQLIntegrityConstraintViolationException e) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            result.put("message", "A staff member with ID '" + staffId + "' already exists.");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("message", "Server error: " + e.getMessage());
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
