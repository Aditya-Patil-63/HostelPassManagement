package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.ByteArrayOutputStream;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.User;

import de.hdodenhof.circleimageview.CircleImageView;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private String selectedRole = "student";
    private Uri    photoUri;

    private EditText etName, etEmail, etPassword, etConfirmPassword, etPhone,
                     etParentPhone, etEmployeeId, etSapId;
    private Spinner  spinnerYear;
    private CircleImageView ivPhotoPreview;
    private ProgressBar progressBar;
    private Button btnRegister;
    private TextView tvSapIdLabel;

    private FirebaseAuth   mAuth;
    private FirebaseHelper fbHelper;
    private SessionManager sessionManager;

    private final ActivityResultLauncher<String> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    photoUri = uri;
                    ivPhotoPreview.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth          = FirebaseAuth.getInstance();
        fbHelper       = new FirebaseHelper();
        sessionManager = new SessionManager(this);

        if (getIntent() != null && getIntent().hasExtra("role")) {
            selectedRole = getIntent().getStringExtra("role");
        }

        initViews();
        setupRoleDynamicFields();



        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvLoginLink).setOnClickListener(v -> finish());
        findViewById(R.id.btnUploadPhoto).setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        btnRegister.setOnClickListener(v -> attemptRegistration());
    }

    private void initViews() {
        TextView tvRoleLabel = findViewById(R.id.tvRoleLabel);
        tvRoleLabel.setText("Registering as: " + selectedRole.toUpperCase());

        etName             = findViewById(R.id.etName);
        etEmail            = findViewById(R.id.etEmail);
        etPassword         = findViewById(R.id.etPassword);
        etConfirmPassword  = findViewById(R.id.etConfirmPassword);
        etPhone            = findViewById(R.id.etPhone);

        etParentPhone      = findViewById(R.id.etParentPhone);
        etEmployeeId       = findViewById(R.id.etEmployeeId);
        etSapId            = findViewById(R.id.etSapId);
        tvSapIdLabel       = findViewById(R.id.tvSapIdLabel);

        spinnerYear = findViewById(R.id.spinnerYear);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.years, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapter);

        ivPhotoPreview = findViewById(R.id.ivPhotoPreview);
        progressBar    = findViewById(R.id.progressBar);
        btnRegister    = findViewById(R.id.btnRegister);
    }

    private void setupRoleDynamicFields() {
        if ("student".equals(selectedRole)) {
            show(R.id.tvYearLabel);
            spinnerYear.setVisibility(View.VISIBLE);

            show(R.id.tvParentPhoneLabel);
            etParentPhone.setVisibility(View.VISIBLE);
            tvSapIdLabel.setVisibility(View.VISIBLE);
            etSapId.setVisibility(View.VISIBLE);
        } else if ("security".equals(selectedRole)) {
            show(R.id.tvEmployeeIdLabel);
            etEmployeeId.setVisibility(View.VISIBLE);
        }
    }



    private void attemptRegistration() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String phone    = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword) || TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (photoUri == null) {
            Toast.makeText(this, "Please upload a photo", Toast.LENGTH_SHORT).show();
            return;
        }


        if ("student".equals(selectedRole)) {
            String sapId = etSapId.getText().toString().trim();
            if (TextUtils.isEmpty(sapId)) {
                Toast.makeText(this, "SAP ID is required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            setLoading(true);
            fbHelper.getUserBySapId(sapId, querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    setLoading(false);
                    Toast.makeText(this, "This SAP ID is already registered!", Toast.LENGTH_SHORT).show();
                } else {
                    // Unique! Continue
                    performAuthRegistration(email, password, name, phone);
                }
            }, e -> {
                setLoading(false);
                Toast.makeText(this, "Error checking SAP ID", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        performAuthRegistration(email, password, name, phone);
    }

    private void performAuthRegistration(String email, String password, String name, String phone) {
        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
             .addOnSuccessListener(authResult -> {
                 String uid = authResult.getUser().getUid();
                 processPhotoAndSave(uid, name, email, phone);
             })
             .addOnFailureListener(e -> {
                 setLoading(false);
                 Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
             });
    }

    /**
     * Compresses the selected photo and uploads it to Firebase Storage.
     * Saves the resulting download URL to Firestore.
     */
    private void processPhotoAndSave(String uid, String name, String email, String phone) {
        setLoading(true);
        Log.d(TAG, "Processing photo for uid: " + uid);

        try {
            Bitmap bitmap = PhotoHelper.getBitmapFromUri(this, photoUri);

            if (bitmap == null) {
                Log.e(TAG, "BitmapFactory returned null for photoUri: " + photoUri);
                Toast.makeText(this, "Invalid image selected", Toast.LENGTH_SHORT).show();
                setLoading(false);
                return;
            }

            // Resize to max 800px
            int maxSize = 800;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxSize || height > maxSize) {
                float ratio = (float) width / (float) height;
                if (ratio > 1) {
                    width = maxSize;
                    height = (int) (width / ratio);
                } else {
                    height = maxSize;
                    width = (int) (height * ratio);
                }
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();

            StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("profile_photos/" + uid + ".jpg");
            storageRef.putBytes(bytes)
                    .addOnSuccessListener(taskSnapshot -> {
                        storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String downloadUrl = uri.toString();
                            saveUserToFirestore(uid, name, email, phone, downloadUrl);
                        }).addOnFailureListener(e -> {
                            setLoading(false);
                            Toast.makeText(this, "Failed to get photo URL", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, "Failed to upload photo", Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error processing photo", e);
            setLoading(false);
            Toast.makeText(this, "Error processing photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveUserToFirestore(String uid, String name, String email,
                                     String phone, String photoData) {
        User user = new User(uid, name, email, phone, selectedRole, photoData);

        if ("student".equals(selectedRole)) {
            user.year           = spinnerYear.getSelectedItem().toString();

            user.parentPhone    = etParentPhone.getText().toString().trim();
            user.studentSapId   = etSapId.getText().toString().trim();
        } else if ("security".equals(selectedRole)) {
            user.employeeId = etEmployeeId.getText().toString().trim();
        }

        fbHelper.saveUser(user, unused -> {
            // Save all fields to session
            sessionManager.saveSession(uid, selectedRole, name, phone,
                    user.year, photoData, user.parentPhone, user.employeeId);
            if ("student".equals(selectedRole)) {
                sessionManager.saveStudentSapId(user.studentSapId);
            }
            
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                FirebaseFirestore.getInstance().collection("Users").document(uid).update("fcmToken", token);
            });
            
            setLoading(false);
            Toast.makeText(this, "Registration Successful! SAP ID: " + user.studentSapId,
                    Toast.LENGTH_LONG).show();
            Log.d(TAG, "User saved. SAP ID=" + user.studentSapId);
            navigateToDashboard(selectedRole);
        }, e -> {
            setLoading(false);
            Toast.makeText(this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Firestore save failed", e);
        });
    }

    private void navigateToDashboard(String role) {
        Intent intent;
        if ("student".equals(role)) {
            intent = new Intent(this, StudentDashboardActivity.class);
        } else if ("warden".equals(role)) {
            intent = new Intent(this, WardenDashboardActivity.class);
        } else {
            intent = new Intent(this, SecurityDashboardActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void show(int viewId) {
        findViewById(viewId).setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
        etName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        etConfirmPassword.setEnabled(!isLoading);
    }
}
