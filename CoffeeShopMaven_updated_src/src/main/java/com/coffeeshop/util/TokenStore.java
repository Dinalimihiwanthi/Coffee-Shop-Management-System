package com.coffeeshop.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

/**
 * Simple in-memory bearer token store. Not JWT, not persistent across server
 * restarts -- fine for a coursework/local-dev project. If this needs to survive
 * restarts or scale to multiple server instances, swap this for real JWTs later.
 */
public class TokenStore {

    private static final Map<String, StaffPrincipal> tokens = new ConcurrentHashMap<>();

    public static String issueToken(StaffPrincipal principal) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, principal);
        return token;
    }

    /** Reads the "Authorization: Bearer xxx" header and resolves it, or null if missing/invalid. */
    public static StaffPrincipal resolve(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return tokens.get(token);
    }

    public static void revoke(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokens.remove(header.substring("Bearer ".length()).trim());
        }
    }
}