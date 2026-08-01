package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        View cardStudent = findViewById(R.id.cardStudent);
        View cardWarden = findViewById(R.id.cardWarden);
        View cardSecurity = findViewById(R.id.cardSecurity);

        cardStudent.setOnClickListener(v -> navigateToLogin("student"));
        cardWarden.setOnClickListener(v -> navigateToLogin("warden"));
        cardSecurity.setOnClickListener(v -> navigateToLogin("security"));
    }

    private void navigateToLogin(String role) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("role", role);
        startActivity(intent);
    }
}
