const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendPushNotification = functions.firestore
  .document("Notifications/{notifId}")
  .onCreate(async (snap, context) => {
    const notification = snap.data();
    const recipientId = notification.recipientId;

    if (!recipientId) return null;

    try {
      const userDoc = await admin.firestore().collection("Users").doc(recipientId).get();
      if (!userDoc.exists) return null;

      const fcmToken = userDoc.data().fcmToken;
      if (!fcmToken) return null;

      const payload = {
        token: fcmToken,
        notification: {
          title: "Hostel Alerts",
          body: notification.message || "You have a new notification"
        }
      };

      await admin.messaging().send(payload);
      return null;
    } catch (error) {
      console.error("Error sending push notification:", error);
      
      // If token is invalid/unregistered, remove it from Firestore
      if (error.code && error.code.includes('messaging/registration-token-not-registered')) {
        await admin.firestore().collection("Users").doc(recipientId).update({ 
            fcmToken: admin.firestore.FieldValue.delete() 
        });
      }
      return null;
    }
  });
