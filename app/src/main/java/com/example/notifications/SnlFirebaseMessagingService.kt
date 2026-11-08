package com.example.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Locale

/**
 * Firebase Cloud Messaging (`FirebaseMessagingService`) implementation for SNL Lubricants.
 *
 * Receives push notifications from FCM when a distributor's order status updates to
 * `'Dispatched'` (`DISPATCHED`) or `'Delivered'` (`DELIVERED`), and manages FCM registration
 * token refreshes.
 */
class SnlFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Refreshed FCM registration token received")
        SnlFcmNotificationManager.saveFcmTokenLocally(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val rawStatus = (data["orderStatus"] ?: data["status"] ?: "").trim()
        val notificationTitle = remoteMessage.notification?.title ?: data["title"]
        val notificationBody = remoteMessage.notification?.body ?: data["body"] ?: data["message"]

        // Extract status from data payload or notification title/body if sent via Firebase Console / Cloud Functions
        val resolvedStatus = when {
            SnlFcmNotificationManager.isTargetPushStatus(rawStatus) -> rawStatus
            notificationTitle?.contains("Delivered", ignoreCase = true) == true ||
                notificationBody?.contains("Delivered", ignoreCase = true) == true -> "DELIVERED"
            notificationTitle?.contains("Dispatched", ignoreCase = true) == true ||
                notificationBody?.contains("Dispatched", ignoreCase = true) == true -> "DISPATCHED"
            else -> rawStatus
        }

        if (!SnlFcmNotificationManager.isTargetPushStatus(resolvedStatus)) {
            Log.d(TAG, "Ignoring FCM message for non-target order status: $resolvedStatus")
            return
        }

        val orderId = data["orderId"] ?: data["referenceId"] ?: ""
        val orderNumber = data["orderNumber"] ?: orderId.ifBlank { "SNL-ORDER" }
        val businessName = data["businessName"] ?: data["customerName"] ?: "Authorized SNL Distributor"
        val city = data["city"] ?: "Karachi"
        val grandTotalPkr = data["grandTotal"]?.toDoubleOrNull()
            ?: data["grandTotalPkr"]?.toDoubleOrNull()
            ?: 0.0
        val topic = remoteMessage.from ?: "snl_distributors_orders"

        SnlFcmNotificationManager.showOrderStatusPushNotification(
            context = applicationContext,
            orderId = orderId,
            orderNumber = orderNumber,
            orderStatus = resolvedStatus.uppercase(Locale.US),
            businessName = businessName,
            city = city,
            grandTotalPkr = grandTotalPkr,
            customTitle = notificationTitle,
            customBody = notificationBody,
            topic = topic,
            forceEvenIfAlreadyNotified = true
        )
    }

    companion object {
        private const val TAG = "SnlFcmService"
    }
}
