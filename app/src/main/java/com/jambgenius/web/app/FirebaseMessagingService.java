package com.jambgenius.web.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "JambGenius-FCM";
    private static final String CHANNEL_ID = "jambgenius_notifications";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        // Create notification channel for Android 8+
        createNotificationChannel();
        
        // Get notification data
        String title = remoteMessage.getNotification() != null ? 
            remoteMessage.getNotification().getTitle() : "JambGenius";
        String body = remoteMessage.getNotification() != null ? 
            remoteMessage.getNotification().getBody() : "New notification";
        String clickAction = remoteMessage.getNotification() != null ? 
            remoteMessage.getNotification().getClickAction() : null;
        
        // Get custom data
        String notificationType = remoteMessage.getData().get("type");
        String deepLink = remoteMessage.getData().get("deepLink");
        
        // Log notification
        android.util.Log.d(TAG, "Notification received: " + title + " - " + body);
        
        // Send notification
        sendNotification(title, body, clickAction, notificationType, deepLink);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        
        // Log new token
        android.util.Log.d(TAG, "FCM Token: " + token);
        
        // Send token to server for storage
        sendTokenToServer(token);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "JambGenius Notifications";
            String description = "Notifications for study reminders, messages, and alerts";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = 
                getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendNotification(String title, String body, String clickAction, 
                                   String notificationType, String deepLink) {
        // Create intent
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Add deep link if provided
        if (deepLink != null && !deepLink.isEmpty()) {
            intent.putExtra("deepLink", deepLink);
        }
        
        // Add notification type for tracking
        if (notificationType != null && !notificationType.isEmpty()) {
            intent.putExtra("notificationType", notificationType);
        }
        
        int requestCode = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Add style for longer text
        if (body.length() > 100) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(body));
        }

        // Send notification
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(requestCode, builder.build());
        }
    }

    private void sendTokenToServer(String token) {
        // This would normally send the token to your server
        // For now, we'll just log it and store in SharedPreferences
        android.util.Log.d(TAG, "Token stored for sending to server later");
    }
}
