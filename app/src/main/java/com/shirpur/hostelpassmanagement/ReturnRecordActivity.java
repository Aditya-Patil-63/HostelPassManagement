package com.shirpur.hostelpassmanagement;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.models.Pass;
import com.shirpur.hostelpassmanagement.models.User;

import de.hdodenhof.circleimageview.CircleImageView;

public class ReturnRecordActivity extends AppCompatActivity {

    private String mode; // "LEAVE" or "OUTING"
    private EditText etSapId, etYear;
    private View layoutYear;
    private TextView tvTitle, tvErrorIcon, tvErrorMessage;
    private ProgressBar progressBar;
    private FrameLayout resultCardContainer;
    private View errorState;

    private FirebaseHelper fbHelper;
    private Pass currentPass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_record);

        fbHelper = new FirebaseHelper();

        mode = getIntent().getStringExtra("mode"); // "LEAVE" or "OUTING"

        initViews();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerify).setOnClickListener(v -> verifyAndReturn());
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        etSapId = findViewById(R.id.etSapId);
        etYear = findViewById(R.id.etYear);
        layoutYear = findViewById(R.id.layoutYear);
        progressBar = findViewById(R.id.progressBar);
        resultCardContainer = findViewById(R.id.resultCardContainer);
        errorState = findViewById(R.id.errorState);
        tvErrorIcon = findViewById(R.id.tvErrorIcon);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        if ("LEAVE".equals(mode)) {
            tvTitle.setText("   RETURN FROM HOME   ");
            layoutYear.setVisibility(View.GONE);
        } else {
            tvTitle.setText("   RETURN FROM OUTING   ");
            layoutYear.setVisibility(View.VISIBLE);
        }
    }

    private void verifyAndReturn() {
        String sapId = etSapId.getText().toString().trim();
        String year = etYear.getText().toString().trim();

        if (TextUtils.isEmpty(sapId)) {
            Toast.makeText(this, "Please enter SAP ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("OUTING".equals(mode) && TextUtils.isEmpty(year)) {
            Toast.makeText(this, "Please enter Student Year", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        resultCardContainer.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);

        fbHelper.getUserBySapId(sapId, querySnapshot -> {
            if (!querySnapshot.isEmpty()) {
                DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                User user = doc.toObject(User.class);
                if (user != null) {
                    // For outing, verify year
                    if ("OUTING".equals(mode)) {
                        if (user.year == null || !user.year.equalsIgnoreCase(year)) {
                            showError("Year mismatch. Student belongs to " + user.year, "⚠️");
                            return;
                        }
                    }
                    checkPassAndMarkIn(user.userId);
                }
            } else {
                showError("Student not found with this SAP ID", "❌");
            }
        }, e -> showError("Failed to search student", "⚠️"));
    }

    private void checkPassAndMarkIn(String studentId) {
        fbHelper.getPassByStudentId(studentId, querySnapshot -> {
            Pass activePass = null;
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Pass p = doc.toObject(Pass.class);
                if (p != null && "OUT".equals(p.status)) {
                    // Check if type matches
                    if ("LEAVE".equals(mode) && "leave".equalsIgnoreCase(p.type)) {
                        activePass = p;
                        break;
                    }
                    if ("OUTING".equals(mode) && "outing".equalsIgnoreCase(p.type)) {
                        activePass = p;
                        break;
                    }
                }
            }

            if (activePass != null) {
                currentPass = activePass;
                markIn();
            } else {
                String typeStr = "LEAVE".equals(mode) ? "home" : "outing";
                showError("No active record found for this student having status 'OUT' for " + typeStr, "ℹ️");
            }
        }, e -> showError("Failed to check pass", "⚠️"));
    }

    private void markIn() {
        fbHelper.markStudentIn(currentPass.passId, unused -> {
            // Notify warden
            fbHelper.getWardenUid(wardenUid -> {
                if (wardenUid != null) {
                    fbHelper.sendNotification(
                            wardenUid,
                            "🏠 " + currentPass.studentName + " has safely returned from " + 
                            ("leave".equalsIgnoreCase(currentPass.type) ? "home" : "outing") + ".",
                            "STUDENT_ARRIVED",
                            currentPass.passId
                    );
                }
            }, e -> {});

            buildSuccessUI();
        }, e -> showError("Failed to mark entry", "⚠️"));
    }

    private void buildSuccessUI() {
        setLoading(false);
        resultCardContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background_dark);
        card.setPadding(40, 40, 40, 40);

        TextView tvMsg = new TextView(this);
        tvMsg.setText("✅ ENTRY SUCCESSFUL");
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.status_approved));
        tvMsg.setTextSize(16);
        tvMsg.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMsg.setGravity(android.view.Gravity.CENTER);
        card.addView(tvMsg);

        CircleImageView civPhoto = new CircleImageView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(160, 160);
        p.gravity = android.view.Gravity.CENTER;
        p.topMargin = 20;
        civPhoto.setLayoutParams(p);
        PhotoHelper.loadWithGlide(this, currentPass.studentPhotoUrl, civPhoto);
        card.addView(civPhoto);

        TextView tvName = new TextView(this);
        tvName.setText(currentPass.studentName);
        tvName.setTextColor(ContextCompat.getColor(this, R.color.white));
        tvName.setTextSize(18);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setGravity(android.view.Gravity.CENTER);
        card.addView(tvName);

        TextView tvDetails = new TextView(this);
        tvDetails.setText("Returned from " + currentPass.type.toUpperCase());
        tvDetails.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvDetails.setTextSize(13);
        tvDetails.setGravity(android.view.Gravity.CENTER);
        card.addView(tvDetails);

        Button btnDone = new Button(this);
        LinearLayout.LayoutParams btnLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 120);
        btnLP.topMargin = 30;
        btnDone.setLayoutParams(btnLP);
        btnDone.setText("DONE");
        btnDone.setBackgroundResource(R.drawable.btn_primary);
        btnDone.setTextColor(ContextCompat.getColor(this, R.color.white));
        btnDone.setOnClickListener(v -> finish());
        card.addView(btnDone);

        resultCardContainer.addView(card);
        resultCardContainer.setVisibility(View.VISIBLE);
    }

    private void showError(String message, String icon) {
        setLoading(false);
        tvErrorIcon.setText(icon);
        tvErrorMessage.setText(message);
        errorState.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}
