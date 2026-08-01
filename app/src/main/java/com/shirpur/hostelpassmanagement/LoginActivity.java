package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private String selectedRole = "student";
    private EditText etEmail, etPassword;
    private ProgressBar progressBar;
    private Button btnLogin;
    
    private FirebaseAuth mAuth;
    private FirebaseHelper fbHelper;
    private SessionManager sessionManager;
    // temporary holders used to avoid capturing local variables in callbacks
    private String pendingUid;
    private String pendingDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        fbHelper = new FirebaseHelper();
        sessionManager = new SessionManager(this);

        if (getIntent() != null && getIntent().hasExtra("role")) {
            selectedRole = getIntent().getStringExtra("role");
        }

        TextView tvRoleLabel = findViewById(R.id.tvRoleLabel);
        tvRoleLabel.setText(selectedRole.toUpperCase());

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegisterLink = findViewById(R.id.tvRegisterLink);

        btnLogin.setOnClickListener(v -> attemptLogin());
        tvRegisterLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            intent.putExtra("role", selectedRole);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
             .addOnSuccessListener(authResult -> {
                 String uid = authResult.getUser().getUid();
                 fetchUserDetails(uid);
             })
             .addOnFailureListener(e -> {
                 setLoading(false);
                 String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                 if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException || 
                     msg.contains("no user") || msg.contains("invalid credential")) {
                     Toast.makeText(LoginActivity.this, "Account Not Found, Register First", Toast.LENGTH_LONG).show();
                 } else {
                     Toast.makeText(LoginActivity.this, "Login Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                 }
             });
    }

    private void fetchUserDetails(String uid) {
        fbHelper.getUser(uid, documentSnapshot -> {
            if (documentSnapshot.exists()) {
                setLoading(false);
                String fetchedRole = documentSnapshot.getString("role");
                if (fetchedRole != null && fetchedRole.equals(selectedRole)) {
                    
                    sessionManager.saveSession(
                        uid,
                        fetchedRole,
                        documentSnapshot.getString("name"),
                        documentSnapshot.getString("phone"),
                        documentSnapshot.getString("year"),
                        documentSnapshot.getString("photoUrl"),
                        documentSnapshot.getString("parentPhone"),
                        documentSnapshot.getString("employeeId")
                    );
                    
                    FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                        FirebaseFirestore.getInstance().collection("Users").document(uid).update("fcmToken", token);
                    });
                    
                    navigateToDashboard(fetchedRole);
                } else {
                    mAuth.signOut();
                    Toast.makeText(this, "Invalid role for this account.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Profile missing in Firestore (likely old registration where photo upload
                // failed before saving the user). Auto-create a basic profile to recover.
                autoCreateMissingProfile(uid);
            }
        }, e -> {
            setLoading(false);
            Toast.makeText(this, "Error fetching profile.", Toast.LENGTH_SHORT).show();
            mAuth.signOut();
        });
    }

    private void autoCreateMissingProfile(String uid) {
        String email = "";
        if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getEmail() != null) {
            email = mAuth.getCurrentUser().getEmail();
        }

        // Build a display name from the email prefix if no name is available
        String displayName = email.contains("@") ? email.substring(0, email.indexOf("@")) : "User";
        // Capitalize first letter
        displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);

        com.shirpur.hostelpassmanagement.models.User user =
                new com.shirpur.hostelpassmanagement.models.User(uid, displayName, email, "", selectedRole, "");

        // store values into instance fields so callbacks don't need to capture local vars
        this.pendingUid = uid;
        this.pendingDisplayName = displayName;

        fbHelper.saveUser(user, unused -> {
            setLoading(false);
            Toast.makeText(this, "Profile recovered. Please update your details in Profile.", Toast.LENGTH_LONG).show();

            sessionManager.saveSession(this.pendingUid, this.selectedRole, this.pendingDisplayName, "", "", "", "", "");
            
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                FirebaseFirestore.getInstance().collection("Users").document(this.pendingUid).update("fcmToken", token);
            });
            
            navigateToDashboard(this.selectedRole);
            // clear temporaries
            this.pendingUid = null;
            this.pendingDisplayName = null;
        }, e -> {
            setLoading(false);
            mAuth.signOut();
            Toast.makeText(this, "Failed to recover profile. Please register again.", Toast.LENGTH_SHORT).show();
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

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }
}
