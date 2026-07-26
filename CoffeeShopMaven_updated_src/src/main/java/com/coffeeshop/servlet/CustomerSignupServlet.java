package com.coffeeshop.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.json.JSONObject;

import com.coffeeshop.util.DBUtil;

/**
 * POST /api/system/signup  (multipart/form-data: full_name, phone_number, delivery_address, password)
 * Used by sign_up.html (submitted via `new FormData(formElement)`, which fetch
 * always sends as multipart/form-data regardless of the form's enctype attribute).
 * Inserts a new row into customers.
 */
@WebServlet("/api/system/signup")
@MultipartConfig
public class CustomerSignupServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject result = new JSONObject();

        String fullName = partAsString(request, "full_name");
        String phone = partAsString(request, "phone_number");
        String address = partAsString(request, "delivery_address");
        String password = partAsString(request, "password");

        if (isBlank(fullName) || isBlank(phone) || isBlank(password)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("status", "fail");
            result.put("message", "Full name, phone number and password are required.");
            response.getWriter().write(result.toString());
            return;
        }

        String sql = "INSERT INTO customers (full_name, phone, password, address) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fullName.trim());
            ps.setString(2, phone.trim());
            ps.setString(3, password.trim());
            ps.setString(4, address);
            ps.executeUpdate();

            result.put("status", "success");
            result.put("message", "Account created! You can now log in.");

        } catch (SQLIntegrityConstraintViolationException e) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            result.put("status", "fail");
            result.put("message", "That phone number is already registered.");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("status", "fail");
            result.put("message", "Server error: " + e.getMessage());
        }

        response.getWriter().write(result.toString());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String partAsString(HttpServletRequest request, String name) throws IOException, ServletException {
        Part part = request.getPart(name);
        if (part == null) return null;
        try (InputStream is = part.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) out.write(buf, 0, len);
            return out.toString("UTF-8");
        }
    }
}