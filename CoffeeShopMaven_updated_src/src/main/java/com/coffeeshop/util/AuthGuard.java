package com.coffeeshop.util;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

/**
 * Central place for the two auth patterns this app uses:
 *
 *  - Session-based, for the Owner/Admin dashboard (AdminLoginServlet sets
 *    session attributes "staffId"/"username"/"role" on success).
 *  - Bearer-token-based, for the Cashier/Barista/Warehouse dashboards
 *    (AuthServlet issues a token via TokenStore keyed to a StaffPrincipal).
 *
 * Every admin/staff servlet should call one of these before doing any work,
 * so an endpoint can't be hit by someone who never logged in.
 */
public class AuthGuard {

    /** Writes a 401 JSON body and returns false if there is no OWNER session. */
    public static boolean requireAdminSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object role = (session != null) ? session.getAttribute("role") : null;

        if (session == null || !"OWNER".equals(role)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(new JSONObject().put("message", "Admin login required.").toString());
            return false;
        }
        return true;
    }

    /**
     * Resolves the bearer token and checks the staff's DB role is one of
     * allowedRoles (OWNER is always allowed, since an owner can operate any
     * terminal). Writes 401/403 JSON and returns null on failure.
     */
    public static StaffPrincipal requireRole(HttpServletRequest request, HttpServletResponse response, String... allowedRoles) throws IOException {
        StaffPrincipal staff = TokenStore.resolve(request);

        if (staff == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(new JSONObject().put("message", "Not authenticated. Please log in again.").toString());
            return null;
        }

        if ("OWNER".equals(staff.role)) {
            return staff;
        }
        for (String allowed : allowedRoles) {
            if (allowed.equals(staff.role)) {
                return staff;
            }
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new JSONObject().put("message", "You do not have access to this resource.").toString());
        return null;
    }
}