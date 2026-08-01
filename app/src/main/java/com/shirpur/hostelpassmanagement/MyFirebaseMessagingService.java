package com.shirpur.hostelpassmanagement;

import android.text.TextUtils;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.firestore.FirebaseFirestore;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.helpers.NotificationHelper;
import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        String uid = sessionManager.getUid();
        
        if (!TextUtils.isEmpty(uid)) {
            Map<String, Object> update = new HashMap<>();
            update.put("fcmToken", token);
            FirebaseFirestore.getInstance().collection("Users").document(uid).update(update);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        String title = "Hostel Pass Update";
        String body = "You have a new notification";
        
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        } else if (remoteMessage.getData().size() > 0) {
            title = remoteMessage.getData().get("title");
            body = remoteMessage.getData().get("body");
        }
        
        NotificationHelper.showNotification(getApplicationContext(), title, body);
    }
}
