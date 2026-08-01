package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.models.User;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        loadProfileDetails();
        fetchFreshProfileData();
    }

    private void fetchFreshProfileData() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            fbHelper.getUser(uid, documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        // Refresh session cache
                        sessionManager.saveSession(
                                user.userId, user.role, user.name, user.phone,
                                user.year, user.photoUrl,
                                user.parentPhone, user.employeeId
                        );
                        // Re-render
                        loadProfileDetails();
                    }
                }
            }, e -> {});
        }
    }

    private void loadProfileDetails() {
        TextView tvProfileName = findViewById(R.id.tvProfileName);
        TextView tvProfileRole = findViewById(R.id.tvProfileRole);
        TextView tvEmail = findViewById(R.id.tvEmail);
        TextView tvPhone = findViewById(R.id.tvPhone);
        CircleImageView ivProfilePhoto = findViewById(R.id.ivProfilePhoto);

        tvProfileName.setText(sessionManager.getName());
        tvProfileRole.setText(sessionManager.getRole().toUpperCase());
        
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            tvEmail.setText(FirebaseAuth.getInstance().getCurrentUser().getEmail());
        }
        
        tvPhone.setText(sessionManager.getPhone());

        // Load photo from Base64 data stored in session
        String photoData = sessionManager.getPhotoUrl();
        Log.d(TAG, "Loading profile photo, data length: " + (photoData != null ? photoData.length() : 0));
        PhotoHelper.loadWithGlide(this, photoData, ivProfilePhoto);

        String role = sessionManager.getRole();
        if ("student".equals(role)) {
            findViewById(R.id.rowYear).setVisibility(View.VISIBLE);
            
            ((TextView) findViewById(R.id.tvYear)).setText(sessionManager.getYear());
        } else if ("security".equals(role)) {
            findViewById(R.id.rowEmployeeId).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvEmployeeId)).setText(sessionManager.getEmployeeId());
        }
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        sessionManager.clearSession();
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
