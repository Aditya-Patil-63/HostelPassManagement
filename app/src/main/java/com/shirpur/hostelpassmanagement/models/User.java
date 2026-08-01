package com.shirpur.hostelpassmanagement.models;

public class User {
    public String userId;
    public String name;
    public String email;
    public String phone;
    public String role; // "student" | "warden" | "security"
    public String photoUrl;
    // Student-specific
    public String year;

    public String parentPhone;
    // Student-specific (extended)
    public String studentSapId;   // e.g. 70012300001 (SAP-style ID)
    public int attendancePercent; // 0–100, self-declared at registration
    // Security-specific
    public String employeeId;

    public User() {}

    public User(String userId, String name, String email, String phone,
                String role, String photoUrl) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.photoUrl = photoUrl;
    }
}
