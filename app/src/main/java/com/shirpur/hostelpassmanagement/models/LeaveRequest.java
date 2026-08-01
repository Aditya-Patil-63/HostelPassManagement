package com.shirpur.hostelpassmanagement.models;

public class LeaveRequest {
    public String requestId;
    public String studentId;
    public String studentName;
    public String studentPhotoUrl;
    public String studentYear;
    public String parentPhone;
    public String reason;
    public String fromDate;
    public String toDate;
    public String status; // PENDING | APPROVED | REJECTED
    public String rejectionReason;
    public int attendancePercent;
    public long timestamp;

    public LeaveRequest() {}

    public LeaveRequest(String studentId, String studentName, String studentPhotoUrl, String studentYear,
                        String parentPhone, String reason, String fromDate, String toDate, int attendancePercent) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentPhotoUrl = studentPhotoUrl;
        this.studentYear = studentYear;
        this.parentPhone = parentPhone;
        this.reason = reason;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.attendancePercent = attendancePercent;
        this.status = "PENDING";
        this.timestamp = System.currentTimeMillis();
    }
}
