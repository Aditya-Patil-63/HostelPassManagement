package com.shirpur.hostelpassmanagement.models;

public class OutPassRequest {
    public String requestId;
    public String studentId;
    public String studentName;
    public String studentPhotoUrl;
    public String studentYear;
    public String parentPhone;
    public String reason;
    public String timeOut;
    public String timeIn;
    public String date;
    public String status; // PENDING | APPROVED | REJECTED
    public String rejectionReason;
    public long timestamp;

    public OutPassRequest() {}

    public OutPassRequest(String studentId, String studentName, String studentPhotoUrl,
                          String studentYear, String parentPhone, String reason, String timeOut, String timeIn,
                          String date) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentPhotoUrl = studentPhotoUrl;
        this.studentYear = studentYear;
        this.parentPhone = parentPhone;
        this.reason = reason;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.date = date;
        this.status = "PENDING";
        this.timestamp = System.currentTimeMillis();
    }
}
