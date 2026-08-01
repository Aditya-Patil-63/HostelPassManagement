package com.shirpur.hostelpassmanagement.models;

public class Pass {
    public String passId;
    public String studentId;
    public String studentName;
    public String studentPhotoUrl;

    public String type; // "leave" | "outing"
    public String studentYear;
    public String status;             // APPROVED | OUT | IN
    public String validity;            // date range or time range
    public long issuedAt;
    public long checkinTimestamp;      // set when security marks IN (0 = not yet IN)
    public long expectedReturnMillis;  // epoch ms of expected return (for overdue detection)

    public Pass() {}

    public Pass(String studentId, String studentName, String studentPhotoUrl,
                String studentYear, String type, String validity) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentPhotoUrl = studentPhotoUrl;
        this.studentYear = studentYear;

        this.type = type;
        this.validity = validity;
        this.status = "APPROVED";
        this.issuedAt = System.currentTimeMillis();
        this.checkinTimestamp = 0;
        this.expectedReturnMillis = 0;
    }
}
