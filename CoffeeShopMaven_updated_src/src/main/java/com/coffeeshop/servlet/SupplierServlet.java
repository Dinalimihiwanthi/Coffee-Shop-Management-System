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
 * GET  /api/suppliers  -> lists supplier contacts for manager_dashboard.html's "Suppliers & Orders" tab
 * POST /api/suppliers   body: {"supplierId","company","firstName","lastName","phoneNo"} -> adds one
 *
 * Backed by the new `suppliers` table. Requires a MANAGER (or OWNER) bearer
 * token from AuthServlet.
 */
@WebServlet("/api/suppliers")
public class SupplierServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (AuthGuard.requireRole(request, response, "MANAGER") == null) {
            return;
        }

        JSONArray suppliers = new JSONArray();
        String sql = "SELECT supplier_ref_id, company, first_name, last_name, phone_no FROM suppliers ORDER BY supplier_ref_id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JSONObject sup = new JSONObject();
                sup.put("supplierId", rs.getString("supplier_ref_id"));
                sup.put("company", rs.getString("company"));
                sup.put("firstName", rs.getString("first_name"));
                sup.put("lastName", rs.getString("last_name"));
                sup.put("phoneNo", rs.getString("phone_no"));
                suppliers.put(sup);
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
            return;
        }

        response.getWriter().write(suppliers.toString());
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
        String supplierId = body.optString("supplierId", "").trim();
        String company = body.optString("company", "").trim();
        String firstName = body.optString("firstName", "").trim();
        String lastName = body.optString("lastName", "").trim();
        String phoneNo = body.optString("phoneNo", "").trim();

        JSONObject result = new JSONObject();
        if (supplierId.isEmpty() || company.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || phoneNo.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("message", "supplierId, company, firstName, lastName and phoneNo are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "INSERT INTO suppliers (supplier_ref_id, company, first_name, last_name, phone_no) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, supplierId);
            ps.setString(2, company);
            ps.setString(3, firstName);
            ps.setString(4, lastName);
            ps.setString(5, phoneNo);
            ps.executeUpdate();

            result.put("supplierId", supplierId);
            result.put("company", company);
            result.put("firstName", firstName);
            result.put("lastName", lastName);
            result.put("phoneNo", phoneNo);

        } catch (SQLIntegrityConstraintViolationException e) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            result.put("message", "A supplier with ID '" + supplierId + "' already exists.");
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
