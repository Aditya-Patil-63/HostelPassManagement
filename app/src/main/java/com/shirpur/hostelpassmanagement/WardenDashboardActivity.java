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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.PhotoHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.Pass;

import de.hdodenhof.circleimageview.CircleImageView;

public class WardenDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    private TextView tvPendingLeaveCount, tvPendingOutCount, tvNotifBadge;
    private ListenerRegistration leaveRegistration, outRegistration, notifRegistration, newNotifRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warden_dashboard);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();

        initHeader();
        initClickListeners();

        tvPendingLeaveCount = findViewById(R.id.tvPendingLeaveCount);
        tvPendingOutCount   = findViewById(R.id.tvPendingOutCount);
        tvNotifBadge        = findViewById(R.id.tvNotifBadge);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNotificationPermission();
        startListening();
        checkOverdueStudents();
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
        stopListening();
    }

    private void startListening() {
        stopListening(); // Clear existing

        leaveRegistration = fbHelper.listenPendingLeaveRequests((querySnapshot, e) -> {
            if (querySnapshot != null) {
                int count = querySnapshot.size();
                tvPendingLeaveCount.setText(count + " pending");
            }
        });

        outRegistration = fbHelper.listenPendingOutPassRequests((querySnapshot, e) -> {
            if (querySnapshot != null) {
                int count = querySnapshot.size();
                tvPendingOutCount.setText(count + " pending");
            }
        });

        String uid = sessionManager.getUid();
        if (uid != null) {
            notifRegistration = fbHelper.listenUnreadNotifications(uid, (querySnapshot, e) -> {
                if (querySnapshot != null) {
                    int count = querySnapshot.size();
                    if (count > 0) {
                        tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                        tvNotifBadge.setVisibility(android.view.View.VISIBLE);
                    } else {
                        tvNotifBadge.setVisibility(android.view.View.GONE);
                    }
                }
            });

            newNotifRegistration = fbHelper.listenNewNotifications(uid, (querySnapshot, e) -> {
                if (querySnapshot == null) return;
                for (com.google.firebase.firestore.DocumentChange dc : querySnapshot.getDocumentChanges()) {
                    if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        com.shirpur.hostelpassmanagement.models.Notification notif = 
                            dc.getDocument().toObject(com.shirpur.hostelpassmanagement.models.Notification.class);
                        if (notif != null && notif.message != null) {
                            com.shirpur.hostelpassmanagement.helpers.NotificationHelper.showNotification(
                                this, "Hostel Alert", notif.message);
                        }
                    }
                }
            });
        }
    }



    private void stopListening() {
        if (leaveRegistration != null) {
            leaveRegistration.remove();
            leaveRegistration = null;
        }
        if (outRegistration != null) {
            outRegistration.remove();
            outRegistration = null;
        }
        if (notifRegistration != null) {
            notifRegistration.remove();
            notifRegistration = null;
        }
        if (newNotifRegistration != null) {
            newNotifRegistration.remove();
            newNotifRegistration = null;
        }
    }

    private void checkOverdueStudents() {
        fbHelper.getOutPasses(querySnapshot -> {
            long now = System.currentTimeMillis();
            String wardenUid = sessionManager.getUid();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Pass pass = doc.toObject(Pass.class);
                if (pass != null && pass.expectedReturnMillis > 0) {
                    if (now > pass.expectedReturnMillis) {
                        // Send or update the overdue alert notification
                        fbHelper.sendOverdueAlert(wardenUid, pass.studentName, null, doc.getId());
                    }
                }
            }
        }, e -> {});
    }

    private void initHeader() {
        TextView tvWardenName = findViewById(R.id.tvWardenName);
        CircleImageView ivWardenPhoto = findViewById(R.id.ivWardenPhoto);

        tvWardenName.setText(sessionManager.getName());

        String photoData = sessionManager.getPhotoUrl();
        PhotoHelper.loadWithGlide(this, photoData, ivWardenPhoto);
    }

    private void initClickListeners() {
        findViewById(R.id.cardLeaveRequests).setOnClickListener(v ->
            startActivity(new Intent(this, LeaveRequestsActivity.class)));

        findViewById(R.id.cardOutingRequests).setOnClickListener(v ->
            startActivity(new Intent(this, OutPassRequestsActivity.class)));

        findViewById(R.id.cardViewRecords).setOnClickListener(v ->
            startActivity(new Intent(this, ViewRecordsActivity.class)));

        findViewById(R.id.cardWardenProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.ivWardenPhoto).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
            startActivity(new Intent(this, NotificationsActivity.class)));


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
