package com.shirpur.hostelpassmanagement;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    private ListView listView;
    private View emptyState;
    private List<Map<String, Object>> notifications = new ArrayList<>();
    private NotificationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        sessionManager = new SessionManager(this);
        fbHelper       = new FirebaseHelper();

        listView   = findViewById(R.id.listNotifications);
        emptyState = findViewById(R.id.emptyState);

        adapter = new NotificationAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadNotifications();
    }

    private void loadNotifications() {
        String uid = sessionManager.getUid();
        fbHelper.getNotificationsForUser(uid, querySnapshot -> {
            notifications.clear();
            for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) doc.getData();
                if (data != null) {
                    data.put("__id", doc.getId());
                    notifications.add(data);
                }
            }
            
            // Sort client-side: newest first (timestamp DESC)
            java.util.Collections.sort(notifications, (o1, o2) -> {
                Long t1 = (Long) o1.get("timestamp");
                Long t2 = (Long) o2.get("timestamp");
                if (t1 == null || t2 == null) return 0;
                return t2.compareTo(t1);
            });
            
            emptyState.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
            listView.setVisibility(notifications.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.notifyDataSetChanged();

            // Mark all as read after opening
            fbHelper.markAllNotificationsRead(uid);

        }, e -> Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }

    private class NotificationAdapter extends BaseAdapter {
        @Override public int     getCount()                    { return notifications.size(); }
        @Override public Object  getItem(int pos)              { return notifications.get(pos); }
        @Override public long    getItemId(int pos)            { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(NotificationsActivity.this)
                        .inflate(R.layout.list_item_notification, parent, false);
            }
            Map<String, Object> notif = notifications.get(position);

            TextView tvIcon    = convertView.findViewById(R.id.tvNotifIcon);
            TextView tvMsg     = convertView.findViewById(R.id.tvNotifMessage);
            TextView tvTime    = convertView.findViewById(R.id.tvNotifTime);
            TextView tvWarden   = convertView.findViewById(R.id.tvWardenName);
            TextView tvReason   = convertView.findViewById(R.id.tvRejectionReason);
            View     dotUnread = convertView.findViewById(R.id.viewUnreadDot);

            String type    = (String) notif.get("type");
            String message = (String) notif.get("message");
            Long   ts      = (Long)   notif.get("timestamp");
            Object isRead  = notif.get("isRead");
            String wName   = (String) notif.get("wardenName");
            String rReason = (String) notif.get("rejectionReason");

            // Icon by type
            if (type != null) {
                switch (type) {
                    case "LEAVE_APPROVED":   tvIcon.setText("✅"); break;
                    case "LEAVE_REJECTED":   tvIcon.setText("❌"); break;
                    case "OUTPASS_APPROVED": tvIcon.setText("✅"); break;
                    case "OUTPASS_REJECTED": tvIcon.setText("❌"); break;
                    case "NEW_LEAVE_REQUEST":   tvIcon.setText("📋"); break;
                    case "NEW_OUTPASS_REQUEST": tvIcon.setText("🚶"); break;
                    case "STUDENT_ARRIVED":  tvIcon.setText("🏠"); break;
                    case "STUDENT_OVERDUE":  tvIcon.setText("⚠️"); break;
                    default:                 tvIcon.setText("🔔"); break;
                }
            }

            tvMsg.setText(message != null ? message : "");

            // Show Warden Name if available
            if (wName != null && !wName.isEmpty()) {
                tvWarden.setText("By Warden: " + wName);
                tvWarden.setVisibility(View.VISIBLE);
            } else {
                tvWarden.setVisibility(View.GONE);
            }

            // Show Rejection Reason if available
            if (rReason != null && !rReason.isEmpty()) {
                tvReason.setText("Reason: " + rReason);
                tvReason.setVisibility(View.VISIBLE);
            } else {
                tvReason.setVisibility(View.GONE);
            }

            // Relative time (e.g., "2 minutes ago")
            if (ts != null) {
                CharSequence relTime = DateUtils.getRelativeTimeSpanString(
                        ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                tvTime.setText(relTime);
            }

            // Show unread dot
            boolean read = (isRead instanceof Boolean) && (Boolean) isRead;
            dotUnread.setVisibility(read ? View.GONE : View.VISIBLE);

            // Dim background for read notifications
            convertView.setAlpha(read ? 0.75f : 1.0f);

            return convertView;
        }
    }
}
