package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.Pass;

import de.hdodenhof.circleimageview.CircleImageView;

public class SecurityDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    private LinearLayout recentActivityContainer;
    private TextView tvTotalPasses, tvTotalOut, tvTotalIn, tvEmptyRecent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_dashboard);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();

        recentActivityContainer = findViewById(R.id.recentActivityContainer);
        tvTotalPasses = findViewById(R.id.tvTotalPasses);
        tvTotalOut = findViewById(R.id.tvTotalOut);
        tvTotalIn = findViewById(R.id.tvTotalIn);
        tvEmptyRecent = findViewById(R.id.tvEmptyRecent);

        initHeader();

        findViewById(R.id.btnVerifyPass).setOnClickListener(v -> showVerifyPassDialog());
            
        findViewById(R.id.btnReturnRecord).setOnClickListener(v -> showReturnRecordDialog());

        findViewById(R.id.btnProfile).setOnClickListener(v -> 
            startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.ivSecurityPhoto).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));


    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void initHeader() {
        TextView tvSecurityName = findViewById(R.id.tvSecurityName);
        CircleImageView ivSecurityPhoto = findViewById(R.id.ivSecurityPhoto);

        tvSecurityName.setText(sessionManager.getName());

        String photoData = sessionManager.getPhotoUrl();
        PhotoHelper.loadWithGlide(this, photoData, ivSecurityPhoto);
    }

    private void loadDashboardData() {
        fbHelper.getRecentPasses(querySnapshot -> {
            int approvedToday = 0;
            int currentlyOut = 0;
            int returnedIn = 0;
            
            recentActivityContainer.removeAllViews();
            
            if (querySnapshot.isEmpty()) {
                tvEmptyRecent.setVisibility(View.VISIBLE);
            } else {
                tvEmptyRecent.setVisibility(View.GONE);
                int count = 0;
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Pass pass = doc.toObject(Pass.class);
                    if (pass == null) continue;

                    approvedToday++;
                    if ("OUT".equals(pass.status)) {
                        currentlyOut++;
                    } else if ("IN".equals(pass.status)) {
                        returnedIn++;
                    }

                    // Show last 5 in the list
                    if (count < 5) {
                        addRecentItem(pass);
                        count++;
                    }
                }
            }
            
            tvTotalPasses.setText(String.valueOf(approvedToday));
            tvTotalOut.setText(String.valueOf(currentlyOut));
            tvTotalIn.setText(String.valueOf(returnedIn));
            
        }, e -> Toast.makeText(this, "Failed to load stats", Toast.LENGTH_SHORT).show());
    }

    private void addRecentItem(Pass pass) {
        View view = LayoutInflater.from(this).inflate(R.layout.list_item_request, recentActivityContainer, false);
        
        TextView tvName = view.findViewById(R.id.tvItemStudentName);
        TextView tvInfo = view.findViewById(R.id.tvItemSubInfo);
        TextView tvStatus = view.findViewById(R.id.tvItemType);
        TextView tvPendingBadge = view.findViewById(R.id.tvItemStatus);

        if (tvPendingBadge != null) {
            tvPendingBadge.setVisibility(View.GONE);
        }

        tvName.setText(pass.studentName);
        tvInfo.setText(pass.type.toUpperCase() + " · " + pass.validity);
        tvStatus.setText(pass.status);
        
        if ("OUT".equals(pass.status)) {
            tvStatus.setTextColor(getResources().getColor(R.color.status_pending));
        } else if ("IN".equals(pass.status)) {
            tvStatus.setTextColor(getResources().getColor(R.color.status_approved));
        } else {
            tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        }

        recentActivityContainer.addView(view);
    }

    private void showVerifyPassDialog() {
        String[] options = {"Verify Leave Pass", "Verify Outpass"};
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Pass Type")
                .setItems(options, (dialog, which) -> {
                    Intent intent = new Intent(SecurityDashboardActivity.this, VerifyPassActivity.class);
                    if (which == 0) {
                        intent.putExtra("verifyMode", "LEAVE");
                    } else if (which == 1) {
                        intent.putExtra("verifyMode", "OUTING");
                    }
                    startActivity(intent);
                })
                .show();
    }

    private void showReturnRecordDialog() {
        String[] options = {"Return from Home (Leave)", "Return from Outing (Outpass)"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Return Type")
                .setItems(options, (dialog, which) -> {
                    Intent intent = new Intent(SecurityDashboardActivity.this, ReturnRecordActivity.class);
                    if (which == 0) {
                        intent.putExtra("mode", "LEAVE");
                    } else if (which == 1) {
                        intent.putExtra("mode", "OUTING");
                    }
                    startActivity(intent);
                })
                .show();
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
