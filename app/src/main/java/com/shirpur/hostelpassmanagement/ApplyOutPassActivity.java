package com.shirpur.hostelpassmanagement;

import android.app.TimePickerDialog;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.OutPassRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ApplyOutPassActivity extends AppCompatActivity {

    private EditText etStudentName, etTimeOut, etTimeIn, etOtherReason;
    private Spinner spinnerReason;
    private Button btnSubmitOutPass;
    private ProgressBar progressBar;
    private TextView tvOutingRules, tvEligibilityMsg, tvNotEligibleMsg;
    private LinearLayout bannerEligibility, bannerNotEligible;

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    
    private boolean isEligible = false;
    private boolean isEligibleByDay = false;
    private String dayEligibilityError = "";
    private String todayDayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apply_outpass);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();

        initViews();
        setupListeners();
        
        todayDayName = new SimpleDateFormat("EEEE", Locale.ENGLISH).format(new Date()).toUpperCase();
        loadOutingRulesFromAssets();
        checkEligibilityFromHistory();
    }

    private void initViews() {
        etStudentName = findViewById(R.id.etStudentName);
        etTimeOut = findViewById(R.id.etTimeOut);
        etTimeIn = findViewById(R.id.etTimeIn);
        spinnerReason = findViewById(R.id.spinnerReason);
        btnSubmitOutPass = findViewById(R.id.btnSubmitOutPass);
        etOtherReason = findViewById(R.id.etOtherReason);
        progressBar = findViewById(R.id.progressBar);
        tvOutingRules = findViewById(R.id.tvOutingRules);
        
        bannerEligibility = findViewById(R.id.bannerEligibility);
        bannerNotEligible = findViewById(R.id.bannerNotEligible);
        tvEligibilityMsg = findViewById(R.id.tvEligibilityMsg);
        tvNotEligibleMsg = findViewById(R.id.tvNotEligibleMsg);

        etStudentName.setText(sessionManager.getName());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.outpass_reasons, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        // Time fields are now fixed and disabled in layout
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

        btnSubmitOutPass.setOnClickListener(v -> submitOutPass());
    }

    // "Reading text file from storage" topic
    private void loadOutingRulesFromAssets() {
        AssetManager assetManager = getAssets();
        StringBuilder rulesBuilder = new StringBuilder();
        
        String studentYear = sessionManager.getYear().toUpperCase().replace(" ", "_");
        if(studentYear.contains("FIRST")) studentYear = "FIRST";
        else if(studentYear.contains("SECOND")) studentYear = "SECOND";
        else if(studentYear.contains("THIRD")) studentYear = "THIRD";
        else if(studentYear.contains("FOURTH")) studentYear = "FOURTH";

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("outing_rules.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                rulesBuilder.append(line).append("\n");
                
                // Parse rule string: MONDAY=THIRD,FOURTH
                if (!line.startsWith("#") && !line.trim().isEmpty() && line.contains("=")) {
                    String[] parts = line.split("=");
                    String day = parts[0].trim();
                    String allowedYears = parts[1].trim();
                    
                    if (day.equals(todayDayName)) {
                        if (allowedYears.contains(studentYear)) {
                            isEligibleByDay = true;
                        }
                    }
                }
            }
            reader.close();
            tvOutingRules.setText(rulesBuilder.toString().trim());
            
            if (!isEligibleByDay) {
                dayEligibilityError = "✖ No outing allowed for " + sessionManager.getYear() + " on " + todayDayName + ".";
            }
        } catch (Exception e) {
            tvOutingRules.setText("Failed to load rules: " + e.getMessage());
            isEligibleByDay = true;
        }
    }

    private void checkEligibilityFromHistory() {
        setLoading(true);
        fbHelper.getOutPassRequestsByStudent(sessionManager.getUid(), querySnapshot -> {
            setLoading(false);
            int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            
            int outingsThisMonth = 0;
            Date mostRecentOuting = null;
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                OutPassRequest req = doc.toObject(OutPassRequest.class);
                if (req == null || "REJECTED".equals(req.status)) continue;
                
                try {
                    Date reqDate = sdf.parse(req.date);
                    if (reqDate != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(reqDate);
                        
                        if (cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear) {
                            outingsThisMonth++;
                        }
                        
                        // Keep track of the most recent outing
                        if (mostRecentOuting == null || reqDate.after(mostRecentOuting)) {
                            mostRecentOuting = reqDate;
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            checkConstraints(outingsThisMonth, mostRecentOuting);
            
        }, e -> {
            setLoading(false);
            isEligible = true; // Fallback
            updateEligibilityUI(null);
        });
    }

    private void checkConstraints(int outingsThisMonth, Date mostRecentOuting) {
        String errMsg = null;
        
        if (!isEligibleByDay) {
            errMsg = dayEligibilityError;
        } else if (outingsThisMonth >= 2) {
            errMsg = "✖ You have exhausted your 2 outings for this month.";
        } else if (mostRecentOuting != null) {
            long diffInMillis = new Date().getTime() - mostRecentOuting.getTime();
            // To prevent negative days if somehow future date exists, take absolute or bound to 0
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
            if (diffInDays >= 0 && diffInDays < 15) {
                long daysLeft = 15 - diffInDays;
                errMsg = "✖ You must wait " + daysLeft + " more day(s) before applying again.";
            }
        }
        
        updateEligibilityUI(errMsg);
    }

    private void updateEligibilityUI(String errMsg) {
        if (errMsg == null) {
            isEligible = true;
            bannerEligibility.setVisibility(View.VISIBLE);
            bannerNotEligible.setVisibility(View.GONE);
            tvEligibilityMsg.setText("✓ You are eligible to apply for an outing today.");
            btnSubmitOutPass.setEnabled(true);
        } else {
            isEligible = false;
            bannerEligibility.setVisibility(View.GONE);
            bannerNotEligible.setVisibility(View.VISIBLE);
            tvNotEligibleMsg.setText(errMsg);
        }
    }


    private void submitOutPass() {
        String reason = spinnerReason.getSelectedItem().toString();
        if ("Other".equalsIgnoreCase(reason)) {
            reason = etOtherReason.getText().toString().trim();
            if (TextUtils.isEmpty(reason)) {
                Toast.makeText(this, "Please specify your reason", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String timeOut = etTimeOut.getText().toString().trim();
        String timeIn = etTimeIn.getText().toString().trim();
        String todayDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

        if (TextUtils.isEmpty(timeOut) || TextUtils.isEmpty(timeIn)) {
            Toast.makeText(this, "Please enter Time Out and Time In", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isEligible && !reason.toLowerCase().contains("medical")) {
            Toast.makeText(this, "You are not eligible for non-medical outing today.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        OutPassRequest request = new OutPassRequest(
                sessionManager.getUid(),
                sessionManager.getName(),
                sessionManager.getPhotoUrl(),
                sessionManager.getYear(),
                sessionManager.getParentPhone(),
                reason,
                timeOut,
                timeIn,
                todayDate
        );

        fbHelper.submitOutPassRequest(request, unused -> {
            // Notify the warden about the new request
            fbHelper.getWardenUid(wardenUid -> {
                if (wardenUid != null) {
                    fbHelper.sendNotification(
                            wardenUid,
                            "🚶 New Outpass Request from " + sessionManager.getName(),
                            "NEW_OUTPASS_REQUEST",
                            request.requestId);
                }
            }, e2 -> {});
        }, e -> {
            android.util.Log.e("ApplyOutPass", "Submission error: " + e.getMessage());
        });

        Toast.makeText(this, "Out Pass Requested", Toast.LENGTH_SHORT).show();
        new android.os.Handler().postDelayed(this::finish, 500);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSubmitOutPass.setEnabled(!isLoading);
    }
}
