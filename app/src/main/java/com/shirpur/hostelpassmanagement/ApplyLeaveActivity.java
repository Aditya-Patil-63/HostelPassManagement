package com.shirpur.hostelpassmanagement;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ApplyLeaveActivity extends AppCompatActivity {

    private EditText etStudentName, etParentPhone, etFromDate, etToDate, etAttendance, etOtherReason;
    private Spinner spinnerReason;
    private Button btnSubmitLeave;
    private ProgressBar progressBar;

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apply_leave);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();

        initViews();
        setupListeners();
    }

    private void initViews() {
        etStudentName = findViewById(R.id.etStudentName);
        etParentPhone = findViewById(R.id.etParentPhone);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        spinnerReason = findViewById(R.id.spinnerReason);
        btnSubmitLeave = findViewById(R.id.btnSubmitLeave);
        etAttendance = findViewById(R.id.etAttendance);
        etOtherReason = findViewById(R.id.etOtherReason);
        progressBar = findViewById(R.id.progressBar);

        etStudentName.setText(sessionManager.getName());
        etParentPhone.setText(sessionManager.getParentPhone());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.leave_reasons, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate, System.currentTimeMillis() - 1000));
        etToDate.setOnClickListener(v -> {
            long minDate = System.currentTimeMillis() - 1000;
            String fromDateStr = etFromDate.getText().toString().trim();
            if (!fromDateStr.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    Date fromDate = sdf.parse(fromDateStr);
                    if (fromDate != null) minDate = fromDate.getTime();
                } catch (Exception ignored) {}
            }
            showDatePicker(etToDate, minDate);
        });
        
        spinnerReason.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = spinnerReason.getSelectedItem().toString();
                if ("Other".equalsIgnoreCase(selected)) {
                    etOtherReason.setVisibility(View.VISIBLE);
                } else {
                    etOtherReason.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnSubmitLeave.setOnClickListener(v -> submitLeave());
    }

    private void showDatePicker(EditText targetEditText, long minDate) {
        final Calendar c = Calendar.getInstance();
        // If the field already has a date, initialize the picker with it
        String currentVal = targetEditText.getText().toString().trim();
        if (!currentVal.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date d = sdf.parse(currentVal);
                if (d != null) c.setTime(d);
            } catch (Exception ignored) {}
        }

        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String formattedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, monthOfYear + 1, year1);
                    targetEditText.setText(formattedDate);
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(minDate);
        datePickerDialog.show();
    }

    private void submitLeave() {
        String parentPhone = etParentPhone.getText().toString().trim();
        String reason = spinnerReason.getSelectedItem().toString();
        if ("Other".equalsIgnoreCase(reason)) {
            reason = etOtherReason.getText().toString().trim();
            if (TextUtils.isEmpty(reason)) {
                Toast.makeText(this, "Please specify your reason", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String fromDate = etFromDate.getText().toString().trim();
        String toDate = etToDate.getText().toString().trim();
        String attendanceStr = etAttendance.getText().toString().trim();

        if (TextUtils.isEmpty(parentPhone) || TextUtils.isEmpty(fromDate) || TextUtils.isEmpty(toDate) || TextUtils.isEmpty(attendanceStr)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate date range
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date dFrom = sdf.parse(fromDate);
            Date dTo = sdf.parse(toDate);
            if (dFrom != null && dTo != null && dTo.before(dFrom)) {
                Toast.makeText(this, "To Date cannot be before From Date", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show();
            return;
        }

        int attendance = 0;
        try {
            attendance = Integer.parseInt(attendanceStr);
            if (attendance < 0 || attendance > 100) {
                Toast.makeText(this, "Attendance must be between 0 and 100", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid attendance value", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        LeaveRequest request = new LeaveRequest(
                sessionManager.getUid(),
                sessionManager.getName(),
                sessionManager.getPhotoUrl(),
                sessionManager.getYear(),
                parentPhone,
                reason,
                fromDate,
                toDate,
                attendance
        );

        final int finalAttendance = attendance;
        fbHelper.submitLeaveRequest(request, unused -> {
            // Also update the student's profile attendance for consistency
            fbHelper.updateUserAttendance(sessionManager.getUid(), finalAttendance, null, null);
            
            // Notify the warden about the new request
            fbHelper.getWardenUid(wardenUid -> {
                if (wardenUid != null) {
                    fbHelper.sendNotification(
                            wardenUid,
                            "📋 New Leave Request from " + sessionManager.getName(),
                            "NEW_LEAVE_REQUEST",
                            request.requestId);
                }
            }, e2 -> {}); // silent failure on warden lookup
        }, e -> {
            android.util.Log.e("ApplyLeave", "Submission error: " + e.getMessage());
        });

        // Fast optimistic finish
        Toast.makeText(this, "Leave Request Submitted", Toast.LENGTH_SHORT).show();
        new android.os.Handler().postDelayed(this::finish, 500);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSubmitLeave.setEnabled(!isLoading);
    }
}
