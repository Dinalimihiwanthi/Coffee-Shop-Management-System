package com.coffeeshop.util;

public class StaffPrincipal {
    public final int staffId;
    public final String username;
    public final String role; // DB role: OWNER, CASHIER, BARISTA, WAREHOUSE

    public StaffPrincipal(int staffId, String username, String role) {
        this.staffId = staffId;
        this.username = username;
        this.role = role;
    }
}