package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager sessionManager = new SessionManager(this);
            if (sessionManager.isLoggedIn()) {
                String role = sessionManager.getRole();
                if ("student".equals(role)) {
                    startActivity(new Intent(SplashActivity.this, StudentDashboardActivity.class));
                } else if ("warden".equals(role)) {
                    startActivity(new Intent(SplashActivity.this, WardenDashboardActivity.class));
                } else if ("security".equals(role)) {
                    startActivity(new Intent(SplashActivity.this, SecurityDashboardActivity.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
                }
            } else {
                startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            }
            finish();
        }, 2000); // 2 seconds delay
    }
}
