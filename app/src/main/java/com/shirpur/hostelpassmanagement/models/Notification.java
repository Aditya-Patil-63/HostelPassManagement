package com.shirpur.hostelpassmanagement.models;

public class Notification {
    public String notificationId;
    public String recipientId;
    public String message;
    // Types: NEW_LEAVE_REQUEST | NEW_OUTPASS_REQUEST | LEAVE_APPROVED | LEAVE_REJECTED
    //        | OUTPASS_APPROVED | OUTPASS_REJECTED | STUDENT_ARRIVED | STUDENT_OVERDUE
    public String type;
    public String relatedId;   // requestId or passId for deep-linking
    public String parentPhone; // for STUDENT_OVERDUE notifications
    public String wardenName;
    public String rejectionReason;
    public boolean isRead;
    public long timestamp;

    public Notification() {}

    public Notification(String recipientId, String message, String type, String relatedId) {
        this.recipientId = recipientId;
        this.message = message;
        this.type = type;
        this.relatedId = relatedId != null ? relatedId : "";
        this.parentPhone = "";
        this.wardenName = "";
        this.rejectionReason = "";
        this.isRead = false;
        this.timestamp = System.currentTimeMillis();
    }
}
