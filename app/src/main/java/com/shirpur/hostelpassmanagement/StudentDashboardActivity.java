package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;

import de.hdodenhof.circleimageview.CircleImageView;

public class StudentDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    private TextView tvNotifBadge;
    private ListenerRegistration notifRegistration;
    private ListenerRegistration newNotifRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        sessionManager = new SessionManager(this);
        fbHelper       = new FirebaseHelper();

        tvNotifBadge = findViewById(R.id.tvNotifBadge);

        initHeader();
        initClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNotificationPermission();
        listenForUnreadNotifications();
        listenForNewNotifications();
    }

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, 
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (notifRegistration != null) {
            notifRegistration.remove();
            notifRegistration = null;
        }
        if (newNotifRegistration != null) {
            newNotifRegistration.remove();
            newNotifRegistration = null;
        }
    }

    private void initHeader() {
        TextView tvStudentName  = findViewById(R.id.tvStudentName);
        TextView tvStudentYear  = findViewById(R.id.tvStudentYear);
        TextView tvStudentSapId = findViewById(R.id.tvStudentSapId);
        CircleImageView ivStudentPhoto = findViewById(R.id.ivStudentPhoto);

        tvStudentName.setText(sessionManager.getName());
        tvStudentYear.setText(sessionManager.getYear());

        String sapId = sessionManager.getStudentSapId();
        tvStudentSapId.setText(sapId.isEmpty() ? "" : "SAP ID: " + sapId);

        String photoData = sessionManager.getPhotoUrl();
        PhotoHelper.loadWithGlide(this, photoData, ivStudentPhoto);
    }

    private void initClickListeners() {
        findViewById(R.id.cardApplyLeave).setOnClickListener(v ->
            startActivity(new Intent(this, ApplyLeaveActivity.class)));

        findViewById(R.id.cardApplyOutPass).setOnClickListener(v ->
            startActivity(new Intent(this, ApplyOutPassActivity.class)));

        findViewById(R.id.cardViewStatus).setOnClickListener(v ->
            startActivity(new Intent(this, ViewStatusActivity.class)));

        findViewById(R.id.cardProfile).setOnClickListener(v -> startActivity(new android.content.Intent(this, ProfileActivity.class)));
        findViewById(R.id.ivStudentPhoto).setOnClickListener(v -> startActivity(new android.content.Intent(this, ProfileActivity.class)));

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
            startActivity(new Intent(this, NotificationsActivity.class)));
    }

    private void listenForUnreadNotifications() {
        if (notifRegistration != null) notifRegistration.remove();
        String uid = sessionManager.getUid();
        if (uid == null) return;

        notifRegistration = fbHelper.listenUnreadNotifications(uid, (querySnapshot, e) -> {
            if (querySnapshot == null) return;
            int count = querySnapshot.size();
            if (count > 0) {
                tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                tvNotifBadge.setVisibility(android.view.View.VISIBLE);
            } else {
                tvNotifBadge.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void listenForNewNotifications() {
        if (newNotifRegistration != null) newNotifRegistration.remove();
        String uid = sessionManager.getUid();
        if (uid == null) return;

        newNotifRegistration = fbHelper.listenNewNotifications(uid, (querySnapshot, e) -> {
            if (querySnapshot == null) return;
            for (com.google.firebase.firestore.DocumentChange dc : querySnapshot.getDocumentChanges()) {
                if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                    com.shirpur.hostelpassmanagement.models.Notification notif = 
                        dc.getDocument().toObject(com.shirpur.hostelpassmanagement.models.Notification.class);
                    if (notif != null && notif.message != null) {
                        com.shirpur.hostelpassmanagement.helpers.NotificationHelper.showNotification(
                            this, "Hostel Pass Update", notif.message);
                    }
                }
            }
        });
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
