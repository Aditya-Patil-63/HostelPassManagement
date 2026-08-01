package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;
import com.shirpur.hostelpassmanagement.models.OutPassRequest;
import com.shirpur.hostelpassmanagement.models.Pass;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class RequestDetailActivity extends AppCompatActivity {

    private String requestId;
    private String type; // "LEAVE" or "OUTPASS"

    private FirebaseHelper fbHelper;
    private com.shirpur.hostelpassmanagement.helpers.SessionManager sessionManager;

    private TextView tvStudentName, tvStatus, tvPassType, tvReason, tvDates, tvTimes, tvParentPhone, tvRejectionReason;
    private CircleImageView ivStudentPhoto;
    private LinearLayout rowTimes, rowDates, rowParentPhone, wardenActions,
                         rowRejectionReason, cardStudentHistory;
    private Button btnApprove, btnReject, btnConfirmReject, btnViewAttendance;
    private EditText etRejectionReason;
    private ProgressBar progressBar;

    private LeaveRequest leaveRequestObj;
    private OutPassRequest outPassRequestObj;

    // Holds the attendance % of the student — loaded from Firestore
    private int studentAttendancePercent = -1;

    private boolean startApproveOnResume = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_detail);

        fbHelper = new FirebaseHelper();
        sessionManager = new com.shirpur.hostelpassmanagement.helpers.SessionManager(this);

        if (getIntent() != null) {
            requestId = getIntent().getStringExtra("requestId");
            type      = getIntent().getStringExtra("type");
        }

        initViews();
        loadRequestDetails();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (startApproveOnResume) {
            startApproveOnResume = false;
            approveRequest();
        }
    }

    private void initViews() {
        tvStudentName      = findViewById(R.id.tvStudentName);
        tvStatus           = findViewById(R.id.tvStatus);
        tvPassType         = findViewById(R.id.tvPassType);
        tvReason           = findViewById(R.id.tvReason);
        tvDates            = findViewById(R.id.tvDates);
        tvTimes            = findViewById(R.id.tvTimes);
        tvParentPhone      = findViewById(R.id.tvParentPhone);
        tvRejectionReason  = findViewById(R.id.tvRejectionReason);
        ivStudentPhoto     = findViewById(R.id.ivStudentPhoto);

        rowDates           = findViewById(R.id.rowDates);
        rowTimes           = findViewById(R.id.rowTimes);
        rowParentPhone     = findViewById(R.id.rowParentPhone);
        rowRejectionReason = findViewById(R.id.rowRejectionReason);
        wardenActions      = findViewById(R.id.wardenActions);
        cardStudentHistory = findViewById(R.id.cardStudentHistory);

        btnApprove         = findViewById(R.id.btnApprove);
        btnReject          = findViewById(R.id.btnReject);
        btnConfirmReject   = findViewById(R.id.btnConfirmReject);
        btnViewAttendance  = findViewById(R.id.btnViewAttendance);
        etRejectionReason  = findViewById(R.id.etRejectionReason);
        progressBar        = findViewById(R.id.progressBar);

        if ("OUTPASS".equals(type)) {
            rowDates.setVisibility(View.GONE);
            rowTimes.setVisibility(View.VISIBLE);
        }
        rowParentPhone.setVisibility(View.VISIBLE);

        // Confirm Reject disabled until reason typed
        btnConfirmReject.setEnabled(false);
        btnConfirmReject.setAlpha(0.5f);

        btnApprove.setOnClickListener(v -> handleApproveClick());
        btnReject.setOnClickListener(v -> showRejectField());
        btnConfirmReject.setOnClickListener(v -> rejectRequest());

        btnViewAttendance.setOnClickListener(v -> showAttendanceDialog());

        etRejectionReason.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasReason = s.toString().trim().length() > 0;
                btnConfirmReject.setEnabled(hasReason);
                btnConfirmReject.setAlpha(hasReason ? 1.0f : 0.5f);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  LOAD REQUEST — Direct document fetch
    // ══════════════════════════════════════════════════════════════

    private void loadRequestDetails() {
        setLoading(true);

        if ("LEAVE".equals(type)) {
            fbHelper.getLeaveRequestById(requestId, documentSnapshot -> {
                setLoading(false);
                if (documentSnapshot.exists()) {
                    leaveRequestObj = documentSnapshot.toObject(LeaveRequest.class);
                    if (leaveRequestObj != null) {
                        populateLeaveData();
                        // Show attendance from the request itself
                        updateAttendanceUI(leaveRequestObj.attendancePercent);
                        loadStudentHistoryOnly(leaveRequestObj.studentId);
                    }
                } else {
                    showToast("Request not found");
                    finish();
                }
            }, e -> {
                setLoading(false);
                showToast("Failed to load: " + e.getMessage());
            });

        } else if ("OUTPASS".equals(type)) {
            fbHelper.getOutPassRequestById(requestId, documentSnapshot -> {
                setLoading(false);
                if (documentSnapshot.exists()) {
                    outPassRequestObj = documentSnapshot.toObject(OutPassRequest.class);
                    if (outPassRequestObj != null) {
                        populateOutPassData();
                        // For Outpass, we skip loading the student history card as requested
                    }
                } else {
                    showToast("Request not found");
                    finish();
                }
            }, e -> {
                setLoading(false);
                showToast("Failed to load: " + e.getMessage());
            });
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STUDENT HISTORY + ATTENDANCE
    // ══════════════════════════════════════════════════════════════

    private void loadStudentHistoryOnly(String studentId) {
        fbHelper.getStudentLeaveHistory(studentId, querySnapshot -> {
            cardStudentHistory.setVisibility(View.VISIBLE);
        }, e -> {
            cardStudentHistory.setVisibility(View.VISIBLE);
        });
    }

    private void loadStudentHistoryAndAttendance(String studentId) {
        loadStudentHistoryOnly(studentId);

        // For Outpass (or legacy), still load from User profile
        fbHelper.getUser(studentId, doc -> {
            if (doc.exists()) {
                Long att = doc.getLong("attendancePercent");
                int percent = (att != null) ? att.intValue() : 0;
                updateAttendanceUI(percent);
            }
        }, e -> {});
    }

    private void updateAttendanceUI(int percent) {
        studentAttendancePercent = percent;
    }

    private void showAttendanceDialog() {
        String studentName = (leaveRequestObj != null)
                ? leaveRequestObj.studentName : (outPassRequestObj != null
                ? outPassRequestObj.studentName : "Student");

        String attMsg = studentAttendancePercent >= 0
                ? "Attendance: " + studentAttendancePercent + "%\n\n"
                  + (studentAttendancePercent >= 80
                        ? "✅ Student has sufficient attendance (≥ 80%)"
                        : "⚠️ Student has LOW attendance (< 80%)")
                : "Attendance data not available.";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📊 " + studentName + "'s Attendance")
                .setMessage(attMsg)
                .setPositiveButton("OK", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════
    //  POPULATE DATA
    // ══════════════════════════════════════════════════════════════

    private void populateLeaveData() {
        tvStudentName.setText(leaveRequestObj.studentName);
        tvPassType.setText("LEAVE PASS");
        tvReason.setText(leaveRequestObj.reason);
        tvDates.setText(leaveRequestObj.fromDate + " → " + leaveRequestObj.toDate);
        tvParentPhone.setText(leaveRequestObj.parentPhone);

        updateStatusUI(leaveRequestObj.status, leaveRequestObj.rejectionReason);
        loadStudentPhoto(leaveRequestObj.studentPhotoUrl);
    }

    private void populateOutPassData() {
        tvStudentName.setText(outPassRequestObj.studentName);
        tvPassType.setText("OUT PASS");
        tvReason.setText(outPassRequestObj.reason);
        tvTimes.setText(outPassRequestObj.date + "  " + outPassRequestObj.timeOut
                + " → " + outPassRequestObj.timeIn);
        tvParentPhone.setText(outPassRequestObj.parentPhone);

        updateStatusUI(outPassRequestObj.status, outPassRequestObj.rejectionReason);
        loadStudentPhoto(outPassRequestObj.studentPhotoUrl);
    }

    private void loadStudentPhoto(String photoData) {
        PhotoHelper.loadWithGlide(this, photoData, ivStudentPhoto);
    }

    // ══════════════════════════════════════════════════════════════
    //  STATUS UI
    // ══════════════════════════════════════════════════════════════

    private void updateStatusUI(String status, String rejectReason) {
        tvStatus.setText(status);

        if ("PENDING".equals(status)) {
            wardenActions.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending));
            tvStatus.setBackgroundResource(R.drawable.chip_pending);
            rowRejectionReason.setVisibility(View.GONE);
        } else {
            wardenActions.setVisibility(View.GONE);

            if ("APPROVED".equals(status)) {
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_approved));
                tvStatus.setBackgroundResource(R.drawable.chip_approved);
                rowRejectionReason.setVisibility(View.GONE);
            } else {
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_rejected));
                tvStatus.setBackgroundResource(R.drawable.chip_rejected);

                if (!TextUtils.isEmpty(rejectReason)) {
                    rowRejectionReason.setVisibility(View.VISIBLE);
                    tvRejectionReason.setText(rejectReason);
                } else {
                    rowRejectionReason.setVisibility(View.GONE);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  APPROVE FLOW
    // ══════════════════════════════════════════════════════════════

    private void handleApproveClick() {
        if ("OUTPASS".equals(type)) {
            approveRequest();
            return;
        }

        String phone = null;
        if (leaveRequestObj != null) phone = leaveRequestObj.parentPhone;

        if (!TextUtils.isEmpty(phone)) {
            startApproveOnResume = true;
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));
            startActivity(intent);
            showToast("Approval pending call. Return to app to complete pass generation.");
        } else {
            showToast("No parent phone available. Approving directly...");
            approveRequest();
        }
    }

    private void approveRequest() {
        setLoading(true);
        btnApprove.setEnabled(false);
        btnReject.setEnabled(false);

        if ("LEAVE".equals(type)) {
            fbHelper.updateLeaveStatus(requestId, "APPROVED", null, unused -> {
                // ── Notify student ──────────────────────────────
                fbHelper.sendNotification(
                        leaveRequestObj.studentId,
                        "✅ Your Leave Pass has been APPROVED! You may proceed to go home.",
                        "LEAVE_APPROVED",
                        requestId,
                        sessionManager.getName(),
                        "");
                generateDigitalPass(
                        leaveRequestObj.studentId,
                        leaveRequestObj.studentName,
                        leaveRequestObj.studentPhotoUrl,
                        leaveRequestObj.studentYear,
                        "leave",
                        leaveRequestObj.fromDate + " - " + leaveRequestObj.toDate,
                        leaveRequestObj.toDate, null);
            }, e -> {
                setLoading(false);
                showToast("Failed to approve: " + e.getMessage());
                btnApprove.setEnabled(true);
                btnReject.setEnabled(true);
            });
        } else {
            fbHelper.updateOutPassStatus(requestId, "APPROVED", null, unused -> {
                // ── Notify student ──────────────────────────────
                fbHelper.sendNotification(
                        outPassRequestObj.studentId,
                        "✅ Your Outpass has been APPROVED! You may go out.",
                        "OUTPASS_APPROVED",
                        requestId,
                        sessionManager.getName(),
                        "");
                generateDigitalPass(
                        outPassRequestObj.studentId,
                        outPassRequestObj.studentName,
                        outPassRequestObj.studentPhotoUrl,
                        outPassRequestObj.studentYear,
                        "outing",
                        outPassRequestObj.date + " " + outPassRequestObj.timeOut
                                + " - " + outPassRequestObj.timeIn,
                        null, outPassRequestObj.date + " " + outPassRequestObj.timeIn);
            }, e -> {
                setLoading(false);
                showToast("Failed to approve: " + e.getMessage());
                btnApprove.setEnabled(true);
                btnReject.setEnabled(true);
            });
        }
    }

    /**
     * After approval, a digital pass is created in Firestore "Passes" collection.
     * Includes expectedReturnMillis so the warden can detect overdue students.
     *
     * @param returnDate  "dd/MM/yyyy" for leave pass (end of toDate)
     * @param returnDateTime "dd/MM/yyyy HH:mm" for outpass (date + timeIn)
     */
    private void generateDigitalPass(String studentId, String sName, String sPhoto, String sYear,
                                     String passType, String validity,
                                     String returnDate, String returnDateTime) {
        fbHelper.getUser(studentId, doc -> {
            Pass pass = new Pass(studentId, sName, sPhoto, sYear, passType, validity);
            pass.expectedReturnMillis = parseExpectedReturn(returnDate, returnDateTime);

            fbHelper.savePass(pass, unused -> {
                setLoading(false);
                showToast("✅ Pass generated successfully for security review!");
                finish();
            }, e -> {
                setLoading(false);
                showToast("Approved but pass generation failed: " + e.getMessage());
                finish();
            });

        }, e -> {
            setLoading(false);
            showToast("Approved but failed to fetch hostel ID: " + e.getMessage());
            finish();
        });
    }

    /** Converts a "dd/MM/yyyy" return date to end-of-day millis, or a "dd/MM/yyyy HH:mm" to exact millis. */
    private long parseExpectedReturn(String dateStr, String dateTimeStr) {
        try {
            if (dateStr != null && !dateStr.isEmpty()) {
                // Leave pass — return by end of toDate
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date d = sdf.parse(dateStr);
                if (d != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    return cal.getTimeInMillis();
                }
            } else if (dateTimeStr != null && !dateTimeStr.isEmpty()) {
                // Outpass — return by exact timeIn
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date d = sdf.parse(dateTimeStr);
                if (d != null) return d.getTime();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // ══════════════════════════════════════════════════════════════
    //  REJECT FLOW
    // ══════════════════════════════════════════════════════════════

    private void showRejectField() {
        btnApprove.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);
        etRejectionReason.setVisibility(View.VISIBLE);
        btnConfirmReject.setVisibility(View.VISIBLE);
        etRejectionReason.requestFocus();
    }

    private void rejectRequest() {
        String reason = etRejectionReason.getText().toString().trim();
        if (TextUtils.isEmpty(reason)) {
            showToast("Please enter a reason for rejection");
            return;
        }

        setLoading(true);
        btnConfirmReject.setEnabled(false);

        if ("LEAVE".equals(type)) {
            fbHelper.updateLeaveStatus(requestId, "REJECTED", reason, unused -> {
                // ── Notify student with reason ──────────────────
                fbHelper.sendNotification(
                        leaveRequestObj.studentId,
                        "❌ Your Leave Pass was REJECTED.\nReason: " + reason,
                        "LEAVE_REJECTED",
                        requestId,
                        sessionManager.getName(),
                        reason);
                setLoading(false);
                showToast("❌ Request rejected. Reason sent to student.");
                finish();
            }, e -> {
                setLoading(false);
                showToast("Failed to reject: " + e.getMessage());
                btnConfirmReject.setEnabled(true);
            });
        } else {
            fbHelper.updateOutPassStatus(requestId, "REJECTED", reason, unused -> {
                // ── Notify student with reason ──────────────────
                fbHelper.sendNotification(
                        outPassRequestObj.studentId,
                        "❌ Your Outpass was REJECTED.\nReason: " + reason,
                        "OUTPASS_REJECTED",
                        requestId,
                        sessionManager.getName(),
                        reason);
                setLoading(false);
                showToast("❌ Request rejected. Reason sent to student.");
                finish();
            }, e -> {
                setLoading(false);
                showToast("Failed to reject: " + e.getMessage());
                btnConfirmReject.setEnabled(true);
            });
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
