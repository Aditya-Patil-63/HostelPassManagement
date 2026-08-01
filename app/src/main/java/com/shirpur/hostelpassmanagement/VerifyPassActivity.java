package com.shirpur.hostelpassmanagement;

import android.content.res.ColorStateList;
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
import android.Manifest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SQLiteHelper;
import com.shirpur.hostelpassmanagement.models.Pass;

import de.hdodenhof.circleimageview.CircleImageView;

public class VerifyPassActivity extends AppCompatActivity {

    private EditText etPassId;
    private FrameLayout passCardContainer;
    private View notFoundState;
    private TextView tvNotFoundTitle, tvNotFoundSub;
    private ProgressBar progressBar;

    private FirebaseHelper fbHelper;
    private SQLiteHelper sqlHelper;

    private Pass currentPass = null;
    private String verifyMode = null;

    private ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchScanner();
                } else {
                    Toast.makeText(this, "Camera permission required to scan QR", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    etPassId.setText(result.getContents());
                    verifyPass();
                }
            });

    private void checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchScanner();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan pass QR");
        options.setBeepEnabled(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        barcodeLauncher.launch(options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_pass);

        fbHelper = new FirebaseHelper();
        sqlHelper = new SQLiteHelper(this);

        if (getIntent() != null && getIntent().hasExtra("verifyMode")) {
            verifyMode = getIntent().getStringExtra("verifyMode");
            TextView tvVerifyTitle = findViewById(R.id.tvVerifyTitle);
            if (tvVerifyTitle != null && verifyMode != null) {
                if ("LEAVE".equalsIgnoreCase(verifyMode)) {
                    tvVerifyTitle.setText("   VERIFY LEAVE PASS   ");
                } else if ("OUTING".equalsIgnoreCase(verifyMode)) {
                    tvVerifyTitle.setText("   VERIFY OUTPASS   ");
                } else if ("RETURN".equalsIgnoreCase(verifyMode) || verifyMode.startsWith("RETURN_")) {
                    tvVerifyTitle.setText("   RETURN RECORD   ");
                    etPassId.setHint("Enter SAP ID to Return IN");
                }
            }
        }

        etPassId = findViewById(R.id.etPassId);
        passCardContainer = findViewById(R.id.passCardContainer);
        notFoundState = findViewById(R.id.notFoundState);
        tvNotFoundTitle = (TextView) ((ViewGroup)notFoundState).getChildAt(0); // The ❌
        tvNotFoundSub = (TextView) ((ViewGroup)notFoundState).getChildAt(1);   // The message

        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerify).setOnClickListener(v -> verifyPass());
        findViewById(R.id.btnScanQr).setOnClickListener(v -> checkPermissionAndScan());
    }

    private void verifyPass() {
        String input = etPassId.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "Enter Pass ID or Student ID", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        passCardContainer.setVisibility(View.GONE);
        notFoundState.setVisibility(View.GONE);

        // Stage 1: Try direct Pass ID
        fbHelper.getPassById(input, documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Pass tempPass = documentSnapshot.toObject(Pass.class);
                if (tempPass != null && verifyMode != null && !verifyMode.equalsIgnoreCase(tempPass.type)) {
                    showError("This is a " + tempPass.type + ", but you are verifying " + verifyMode + "S.", "🚫");
                    return;
                }
                currentPass = tempPass;
                sqlHelper.upsertPass(currentPass);
                buildPassCard();
            } else {
                // Stage 2: Try searching as Hostel ID or Student UID
                searchByStudentInfo(input);
            }
        }, e -> searchByStudentInfo(input));
    }

    private void searchByStudentInfo(String input) {
        // Try SAP ID search
        fbHelper.getUserBySapId(input, querySnapshot -> {
            if (!querySnapshot.isEmpty()) {
                String studentUid = querySnapshot.getDocuments().get(0).getId();
                checkPassesForStudent(studentUid);
            } else {
                // Try as raw UID
                fbHelper.getUser(input, doc -> {
                    if (doc.exists()) {
                        checkPassesForStudent(doc.getId());
                    } else {
                        showError("Pass not found", "❌");
                    }
                }, e -> showError("Search failed. Try again.", "⚠️"));
            }
        }, e -> showError("Search failed. Try again.", "⚠️"));
    }

    private void checkPassesForStudent(String uid) {
        fbHelper.getPassByStudentId(uid, querySnapshot -> {
            if (!querySnapshot.isEmpty()) {
                // Find most recent approved/out pass
                Pass validPass = null;
                long maxIssuedAt = -1;
                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Pass p = doc.toObject(Pass.class);
                    if (p != null) {
                        // In RETURN modes, we strictly look for students who are already OUT
                        if (verifyMode != null && verifyMode.startsWith("RETURN")) {
                            if ("OUT".equals(p.status)) {
                                // Match the specific sub-type if applicable
                                if ("RETURN_LEAVE".equals(verifyMode) && !"leave".equalsIgnoreCase(p.type)) continue;
                                if ("RETURN_OUTING".equals(verifyMode) && !"outing".equalsIgnoreCase(p.type)) continue;
                                
                                if (p.issuedAt > maxIssuedAt) {
                                    validPass = p;
                                    maxIssuedAt = p.issuedAt;
                                }
                            }
                            continue;
                        }

                        // Match the specific type (Leave vs Outing)
                        if (verifyMode != null) {
                            if ("LEAVE".equals(verifyMode) && !"leave".equalsIgnoreCase(p.type)) continue;
                            if ("OUTING".equals(verifyMode) && !"outing".equalsIgnoreCase(p.type)) continue;
                        }

                        // Looking for APPROVED or OUT pass
                        if ("APPROVED".equals(p.status) || "OUT".equals(p.status)) {
                            if (p.issuedAt > maxIssuedAt) {
                                validPass = p;
                                maxIssuedAt = p.issuedAt;
                            }
                        }
                    }
                }

                if (validPass != null) {
                    currentPass = validPass;
                    buildPassCard();
                } else {
                    String msg = "No active " + (verifyMode != null ? verifyMode : "") + " pass found";
                    if (verifyMode != null && verifyMode.startsWith("RETURN")) {
                        String typeStr = verifyMode.contains("LEAVE") ? "Leave" : "Outpass";
                        msg = "Student is not currently marked as OUT for " + typeStr + ".";
                    }
                    showError(msg, "ℹ️");
                }
            } else {
                // No approved pass. Check for PENDING requests.
                fbHelper.checkAnyPendingRequest(uid, isPending -> {
                    if (isPending) {
                        showError("Student's approve is not get still yet", "⏳");
                    } else {
                        showError("No active requests or passes for this student", "ℹ️");
                    }
                }, e -> showError("Failed to check status", "⚠️"));
            }
        }, e -> showError("Failed to check status", "⚠️"));
    }

    private void buildPassCard() {
        if (currentPass == null) return;
        setLoading(true);

        // Fetch student profile to get the most up-to-date year
        fbHelper.getUser(currentPass.studentId, doc -> {
            if (doc.exists()) {
                String year = doc.getString("year");
                if (!TextUtils.isEmpty(year)) {
                    currentPass.studentYear = year;
                }
            }
            buildPassCardUI();
        }, e -> {
            buildPassCardUI();
        });
    }

    private void buildPassCardUI() {
        setLoading(false);
        passCardContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.pass_card_background);
        card.setPadding(40, 40, 40, 40);

        TextView tvHeader = new TextView(this);
        String displayType = currentPass.type.equalsIgnoreCase("outing") ? "OUTPASS" : currentPass.type.toUpperCase();
        tvHeader.setText("🟢 APPROVED " + displayType + " PASS");
        tvHeader.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        tvHeader.setTextSize(16);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeader.setGravity(android.view.Gravity.CENTER);
        card.addView(tvHeader);

        CircleImageView civPhoto = new CircleImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 200);
        params.gravity = android.view.Gravity.CENTER;
        params.topMargin = 30;
        civPhoto.setLayoutParams(params);
        PhotoHelper.loadWithGlide(this, currentPass.studentPhotoUrl, civPhoto);
        card.addView(civPhoto);

        TextView tvName = new TextView(this);
        tvName.setText(currentPass.studentName);
        tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvName.setTextSize(20);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setGravity(android.view.Gravity.CENTER);

        LinearLayout.LayoutParams nameLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLP.topMargin = 20;
        tvName.setLayoutParams(nameLP);
        card.addView(tvName);

        TextView tvVal = new TextView(this);
        tvVal.setText("Valid: " + currentPass.validity);
        tvVal.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvVal.setTextSize(14);
        tvVal.setGravity(android.view.Gravity.CENTER);
        card.addView(tvVal);

        TextView tvPid = new TextView(this);
        tvPid.setText("ID: " + currentPass.passId);
        tvPid.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        tvPid.setTextSize(12);
        tvPid.setGravity(android.view.Gravity.CENTER);
        card.addView(tvPid);

        TextView tvYear = new TextView(this);
        tvYear.setText("STUDENT YEAR: " + (currentPass.studentYear != null ? currentPass.studentYear : "—"));
        tvYear.setTextColor(ContextCompat.getColor(this, R.color.brand_primary_light));
        tvYear.setTextSize(14);
        tvYear.setTypeface(null, android.graphics.Typeface.BOLD);
        tvYear.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams yearLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        yearLP.topMargin = 10;
        tvYear.setLayoutParams(yearLP);
        card.addView(tvYear);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLP.topMargin = 40;
        actionLayout.setLayoutParams(actionLP);

        Button btnAction = new Button(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, 150, 1f);
        btnAction.setLayoutParams(btnParams);

        if ("APPROVED".equals(currentPass.status)) {
            // Student is in hostel, going OUT
            btnAction.setText("MARK AS OUT");
            btnAction.setBackgroundResource(R.drawable.btn_primary);
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.white));

            btnAction.setOnClickListener(v -> {
                btnAction.setEnabled(false);
                fbHelper.updatePassStatus(currentPass.passId, "OUT", unused -> {
                    sqlHelper.updatePassStatus(currentPass.passId, "OUT");
                    Toast.makeText(this, "Marked as OUT successfully", Toast.LENGTH_SHORT).show();
                    btnAction.setText("OUT");
                    btnAction.setBackgroundResource(R.drawable.btn_secondary);
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                }, e -> {
                    Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show();
                    btnAction.setEnabled(true);
                });
            });

        } else if ("OUT".equals(currentPass.status)) {
            // Student is outside, returning IN
            btnAction.setText("MARK AS IN");
            btnAction.setBackgroundResource(R.drawable.btn_primary);
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.white));
            
            // Setting a different color for "IN" to distinguish from "OUT"
            btnAction.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_approved)
            ));

            btnAction.setOnClickListener(v -> {
                btnAction.setEnabled(false);
                fbHelper.markStudentIn(currentPass.passId, unused -> {
                    sqlHelper.updatePassStatus(currentPass.passId, "IN");
                    Toast.makeText(this, "Marked as IN successfully", Toast.LENGTH_SHORT).show();
                    btnAction.setText("IN");
                    btnAction.setBackgroundTintList(null);
                    btnAction.setBackgroundResource(R.drawable.btn_secondary);
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                    
                    // Notify warden that student has returned safely
                    fbHelper.getWardenUid(wardenUid -> {
                        if (wardenUid != null) {
                            fbHelper.sendNotification(
                                    wardenUid,
                                    "🏠 " + currentPass.studentName + " has safely returned to the hostel.",
                                    "STUDENT_ARRIVED",
                                    currentPass.passId
                            );
                        }
                    }, e -> {});

                }, e -> {
                    Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show();
                    btnAction.setEnabled(true);
                });
            });

        } else {
            // IN or other status
            btnAction.setText("COMPLETED (IN)");
            btnAction.setEnabled(false);
            btnAction.setBackgroundResource(R.drawable.btn_secondary);
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.white));
        }

        actionLayout.addView(btnAction);
        card.addView(actionLayout);
        passCardContainer.addView(card);
        passCardContainer.setVisibility(View.VISIBLE);
    }

    private void showError(String message, String icon) {
        setLoading(false);
        tvNotFoundTitle.setText(icon);
        tvNotFoundSub.setText(message);

        if (message.contains("not get still yet")) {
            tvNotFoundSub.setTextColor(ContextCompat.getColor(this, R.color.status_rejected)); // Red
        } else {
            tvNotFoundSub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }

        notFoundState.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}
