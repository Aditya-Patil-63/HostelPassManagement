package com.shirpur.hostelpassmanagement.helpers;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;
import com.shirpur.hostelpassmanagement.models.Notification;
import com.shirpur.hostelpassmanagement.models.OutPassRequest;
import com.shirpur.hostelpassmanagement.models.Pass;
import com.shirpur.hostelpassmanagement.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised Firestore CRUD helper.
 */
public class FirebaseHelper {

    private static final String COLLECTION_USERS         = "Users";
    private static final String COLLECTION_LEAVE         = "LeaveRequests";
    private static final String COLLECTION_OUTPASS       = "OutPassRequests";
    private static final String COLLECTION_PASSES        = "Passes";
    private static final String COLLECTION_NOTIFICATIONS = "Notifications";
    private static final String COLLECTION_CONFIG        = "Config";

    private final FirebaseFirestore db;

    public FirebaseHelper() {
        db = FirebaseFirestore.getInstance();
    }

    // ──── USERS ────────────────────────────────────────────────

    public void saveUser(User user,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
          .document(user.userId)
          .set(user)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getUser(String uid,
                        OnSuccessListener<DocumentSnapshot> onSuccess,
                        OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
          .document(uid)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void updateUserAttendance(String uid, int attendance,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
          .document(uid)
          .update("attendancePercent", attendance)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getUserBySapId(String sapId,
                                  OnSuccessListener<QuerySnapshot> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
          .whereEqualTo("studentSapId", sapId)
          .limit(1)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    /**
     * Fetches the UID of the first warden account found in Firestore.
     * Used when sending notifications to the warden.
     */
    public void getWardenUid(OnSuccessListener<String> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
          .whereEqualTo("role", "warden")
          .limit(1)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              if (!querySnapshot.isEmpty()) {
                  onSuccess.onSuccess(querySnapshot.getDocuments().get(0).getId());
              } else {
                  onSuccess.onSuccess(null);
              }
          })
          .addOnFailureListener(onFailure);
    }

    // ──── SAP-STYLE STUDENT ID ────────────────────────────────

    /**
     * Generates the next sequential SAP-style student ID (e.g., 70012300001).
     * Uses a Firestore transaction on Config/sapCounter to ensure uniqueness.
     */
    public void generateSapId(OnSuccessListener<String> onSuccess,
                               OnFailureListener onFailure) {
        DocumentReference counterRef = db.collection(COLLECTION_CONFIG)
                                         .document("sapCounter");
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(counterRef);
            long nextId;
            if (snapshot.exists() && snapshot.contains("lastId")) {
                Long lastId = snapshot.getLong("lastId");
                nextId = (lastId != null ? lastId : 70012300000L) + 1;
            } else {
                nextId = 70012300001L;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("lastId", nextId);
            transaction.set(counterRef, data);
            return String.valueOf(nextId);
        }).addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    // ──── LEAVE REQUESTS ───────────────────────────────────────

    public void submitLeaveRequest(LeaveRequest req,
                                   OnSuccessListener<Void> onSuccess,
                                   OnFailureListener onFailure) {
        String id = db.collection(COLLECTION_LEAVE).document().getId();
        req.requestId = id;
        db.collection(COLLECTION_LEAVE)
          .document(id)
          .set(req)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public ListenerRegistration listenPendingLeaveRequests(EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_LEAVE)
          .whereEqualTo("status", "PENDING")
          .addSnapshotListener(listener);
    }

    public void getPendingLeaveRequests(OnSuccessListener<QuerySnapshot> onSuccess,
                                        OnFailureListener onFailure) {
        db.collection(COLLECTION_LEAVE)
          .whereEqualTo("status", "PENDING")
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getAllLeaveRequests(OnSuccessListener<QuerySnapshot> onSuccess,
                                   OnFailureListener onFailure) {
        db.collection(COLLECTION_LEAVE)
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getLeaveRequestsByStudent(String studentId,
                                          OnSuccessListener<QuerySnapshot> onSuccess,
                                          OnFailureListener onFailure) {
        db.collection(COLLECTION_LEAVE)
          .whereEqualTo("studentId", studentId)
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .limit(20)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    /**
     * Returns ALL leave requests for a student (no limit) — used for history count.
     */
    public void getStudentLeaveHistory(String studentId,
                                       OnSuccessListener<QuerySnapshot> onSuccess,
                                       OnFailureListener onFailure) {
        db.collection(COLLECTION_LEAVE)
          .whereEqualTo("studentId", studentId)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getLeaveRequestById(String requestId,
                                    OnSuccessListener<DocumentSnapshot> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(COLLECTION_LEAVE)
          .document(requestId)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void updateLeaveStatus(String requestId, String status,
                                  String rejectionReason,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        if (rejectionReason != null) update.put("rejectionReason", rejectionReason);
        db.collection(COLLECTION_LEAVE)
          .document(requestId)
          .update(update)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    // ──── OUT PASS REQUESTS ────────────────────────────────────

    public void submitOutPassRequest(OutPassRequest req,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        String id = db.collection(COLLECTION_OUTPASS).document().getId();
        req.requestId = id;
        db.collection(COLLECTION_OUTPASS)
          .document(id)
          .set(req)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public ListenerRegistration listenPendingOutPassRequests(EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_OUTPASS)
          .whereEqualTo("status", "PENDING")
          .addSnapshotListener(listener);
    }

    public void getPendingOutPassRequests(OnSuccessListener<QuerySnapshot> onSuccess,
                                          OnFailureListener onFailure) {
        db.collection(COLLECTION_OUTPASS)
          .whereEqualTo("status", "PENDING")
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getAllOutPassRequests(OnSuccessListener<QuerySnapshot> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(COLLECTION_OUTPASS)
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getOutPassRequestsByStudent(String studentId,
                                            OnSuccessListener<QuerySnapshot> onSuccess,
                                            OnFailureListener onFailure) {
        db.collection(COLLECTION_OUTPASS)
          .whereEqualTo("studentId", studentId)
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .limit(20)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getOutPassRequestById(String requestId,
                                      OnSuccessListener<DocumentSnapshot> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection(COLLECTION_OUTPASS)
          .document(requestId)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void updateOutPassStatus(String requestId, String status,
                                    String rejectionReason,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        if (rejectionReason != null) update.put("rejectionReason", rejectionReason);
        db.collection(COLLECTION_OUTPASS)
          .document(requestId)
          .update(update)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    // ──── PASSES ───────────────────────────────────────────────

    public void savePass(Pass pass,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {
        String id = db.collection(COLLECTION_PASSES).document().getId();
        pass.passId = id;
        db.collection(COLLECTION_PASSES)
          .document(id)
          .set(pass)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getPassByStudentId(String studentId,
                                   OnSuccessListener<QuerySnapshot> onSuccess,
                                   OnFailureListener onFailure) {
        db.collection(COLLECTION_PASSES)
          .whereEqualTo("studentId", studentId)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void getPassById(String passId,
                            OnSuccessListener<DocumentSnapshot> onSuccess,
                            OnFailureListener onFailure) {
        db.collection(COLLECTION_PASSES)
          .document(passId)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void updatePassStatus(String passId, String status,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(COLLECTION_PASSES)
          .document(passId)
          .update("status", status)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    /**
     * Marks the student as checked-IN: sets status to "IN" and records the check-in timestamp.
     */
    public void markStudentIn(String passId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "IN");
        update.put("checkinTimestamp", System.currentTimeMillis());
        db.collection(COLLECTION_PASSES)
          .document(passId)
          .update(update)
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    /**
     * Returns all passes currently marked as OUT (student is outside).
     * The warden uses this to detect overdue students.
     */
    public void getOutPasses(OnSuccessListener<QuerySnapshot> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(COLLECTION_PASSES)
          .whereEqualTo("status", "OUT")
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    public void checkAnyPendingRequest(String studentId,
                                       OnSuccessListener<Boolean> onResult,
                                       OnFailureListener onFailure) {
        // We check Leave first, then Outpass
        db.collection(COLLECTION_LEAVE)
          .whereEqualTo("studentId", studentId)
          .whereEqualTo("status", "PENDING")
          .limit(1)
          .get()
          .addOnSuccessListener(leaveSnapshot -> {
              if (!leaveSnapshot.isEmpty()) {
                  onResult.onSuccess(true);
              } else {
                  db.collection(COLLECTION_OUTPASS)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("status", "PENDING")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(outpassSnapshot -> {
                        onResult.onSuccess(!outpassSnapshot.isEmpty());
                    })
                    .addOnFailureListener(onFailure);
              }
          })
          .addOnFailureListener(onFailure);
    }

    public void getRecentPasses(OnSuccessListener<QuerySnapshot> onSuccess,
                                OnFailureListener onFailure) {
        db.collection(COLLECTION_PASSES)
          .orderBy("issuedAt", Query.Direction.DESCENDING)
          .limit(20)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    // ──── NOTIFICATIONS ────────────────────────────────────────

    /**
     * Sends an in-app notification to a specific user (stored in Firestore).
     */
    public void sendNotification(String recipientId, String message, String type,
                                 String relatedId) {
        sendNotification(recipientId, message, type, relatedId, "", "");
    }

    public void sendNotification(String recipientId, String message, String type,
                                 String relatedId, String wardenName, String rejectionReason) {
        if (recipientId == null || recipientId.isEmpty()) return;
        Notification notif = new Notification(recipientId, message, type, relatedId);
        notif.wardenName = wardenName != null ? wardenName : "";
        notif.rejectionReason = rejectionReason != null ? rejectionReason : "";
        db.collection(COLLECTION_NOTIFICATIONS)
          .add(notif);
    }

    /**
     * Sends an overdue alert notification to the warden. Uses passId as document ID
     * to prevent duplicate overdue notifications for the same pass.
     */
    public void sendOverdueAlert(String wardenUid, String studentName,
                                 String parentPhone, String passId) {
        if (wardenUid == null || wardenUid.isEmpty()) return;
        Map<String, Object> notif = new HashMap<>();
        notif.put("recipientId", wardenUid);
        notif.put("message", "⚠️ " + studentName + " has NOT returned to hostel yet!");
        notif.put("type", "STUDENT_OVERDUE");
        notif.put("relatedId", passId);
        notif.put("parentPhone", parentPhone != null ? parentPhone : "");
        notif.put("isRead", false);
        notif.put("timestamp", System.currentTimeMillis());
        // Use "overdue_<passId>" as document ID — prevents creating duplicates
        db.collection(COLLECTION_NOTIFICATIONS)
          .document("overdue_" + passId)
          .set(notif, SetOptions.merge());
    }

    /**
     * Fetches all notifications for a given user, newest first.
     */
    public void getNotificationsForUser(String uid,
                                        OnSuccessListener<QuerySnapshot> onSuccess,
                                        OnFailureListener onFailure) {
        db.collection(COLLECTION_NOTIFICATIONS)
          .whereEqualTo("recipientId", uid)
          .limit(100)
          .get()
          .addOnSuccessListener(onSuccess)
          .addOnFailureListener(onFailure);
    }

    /**
     * Listens to unread notifications for a user — used for the badge counter.
     */
    public ListenerRegistration listenUnreadNotifications(String uid,
                                                          EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_NOTIFICATIONS)
          .whereEqualTo("recipientId", uid)
          .whereEqualTo("isRead", false)
          .addSnapshotListener(listener);
    }

    /**
     * Listens specifically for ADDED (new) notifications to trigger system alerts.
     */
    public ListenerRegistration listenNewNotifications(String uid,
                                                        EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_NOTIFICATIONS)
          .whereEqualTo("recipientId", uid)
          .whereEqualTo("isRead", false)
          .whereGreaterThan("timestamp", System.currentTimeMillis() - 10000) // Only very recent ones
          .addSnapshotListener(listener);
    }

    /**
     * Marks all unread notifications for a user as read.
     */
    public void markAllNotificationsRead(String uid) {
        db.collection(COLLECTION_NOTIFICATIONS)
          .whereEqualTo("recipientId", uid)
          .whereEqualTo("isRead", false)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              WriteBatch batch = db.batch();
              for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                  batch.update(doc.getReference(), "isRead", true);
              }
              batch.commit();
          });
    }
}
