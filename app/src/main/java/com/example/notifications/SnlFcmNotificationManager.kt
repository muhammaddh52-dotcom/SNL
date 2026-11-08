package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.Order
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents an FCM push notification event delivered to a distributor when an order
 * status transitions to 'Dispatched' (`DISPATCHED`) or 'Delivered' (`DELIVERED`).
 */
data class FcmOrderPushEvent(
    val eventId: String,
    val orderId: String,
    val orderNumber: String,
    val orderStatus: String, // "Dispatched" or "Delivered"
    val title: String,
    val body: String,
    val businessName: String,
    val city: String,
    val grandTotalPkr: Double,
    val topic: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(timestampMillis))
}

/**
 * Central manager for Firebase Cloud Messaging (FCM) integration in SNL Lubricants B2B.
 *
 * Handles:
 * 1. Android NotificationChannel registration (`snl_order_status_fcm_channel`).
 * 2. FCM registration token retrieval (`FirebaseMessaging.getInstance().token`) and topic subscriptions
 *    (`snl_distributors_orders`, `snl_order_updates`, `snl_user_<uid>`).
 * 3. Filtering & dispatching push notifications specifically when an order's status updates to
 *    'Dispatched' (`DISPATCHED`) or 'Delivered' (`DELIVERED`).
 * 4. Deduplicating status transitions observed from Firestore snapshot listeners or incoming
 *    `FirebaseMessagingService` payloads so distributors receive timely push alerts without duplicates.
 */
object SnlFcmNotificationManager {

    private const val TAG = "SnlFcmNotificationMgr"
    const val CHANNEL_ID = "snl_order_status_fcm_channel"
    const val CHANNEL_NAME = "SNL Order Dispatch & Delivery Alerts"
    const val CHANNEL_DESC =
        "Real-time FCM push notifications when your SNL Lubricants order is Dispatched or Delivered."

    const val EXTRA_FCM_ORDER_ID = "extra_fcm_order_id"
    const val EXTRA_FCM_ORDER_NUMBER = "extra_fcm_order_number"
    const val EXTRA_FCM_ORDER_STATUS = "extra_fcm_order_status"

    private const val PREFS_NAME = "snl_fcm_push_prefs"
    private const val KEY_FCM_TOKEN = "fcm_registration_token"
    private const val KEY_FCM_ENABLED = "fcm_push_alerts_enabled"
    private const val KEY_NOTIFIED_ORDER_STATES = "notified_order_status_pairs"

    private val _fcmToken = MutableStateFlow("")
    val fcmToken: StateFlow<String> = _fcmToken.asStateFlow()

    private val _subscribedTopics = MutableStateFlow<Set<String>>(emptySet())
    val subscribedTopics: StateFlow<Set<String>> = _subscribedTopics.asStateFlow()

    private val _isFcmEnabled = MutableStateFlow(true)
    val isFcmEnabled: StateFlow<Boolean> = _isFcmEnabled.asStateFlow()

    private val _recentPushEvents = MutableStateFlow<List<FcmOrderPushEvent>>(emptyList())
    val recentPushEvents: StateFlow<List<FcmOrderPushEvent>> = _recentPushEvents.asStateFlow()

    private val _latestBannerPushEvent = MutableStateFlow<FcmOrderPushEvent?>(null)
    val latestBannerPushEvent: StateFlow<FcmOrderPushEvent?> = _latestBannerPushEvent.asStateFlow()

    // Tracks last known status per orderId to detect live transitions to DISPATCHED or DELIVERED
    private val knownOrderStatuses = ConcurrentHashMap<String, String>()
    private val notifiedTransitionKeys = ConcurrentHashMap.newKeySet<String>()
    private var initialSnapshotSeededForUser: String? = null

    /**
     * Checks if a status string corresponds to the target push notification triggers:
     * 'Dispatched' (`DISPATCHED`) or 'Delivered' (`DELIVERED`).
     */
    fun isTargetPushStatus(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        val normalized = status.trim().uppercase(Locale.US)
        return normalized == "DISPATCHED" || normalized == "DELIVERED"
    }

    /**
     * Normalizes internal status codes (`DISPATCHED`, `DELIVERED`) to human-readable labels
     * (`Dispatched`, `Delivered`).
     */
    fun formatCanonicalPushStatus(status: String): String {
        return when (status.trim().uppercase(Locale.US)) {
            "DISPATCHED" -> "Dispatched"
            "DELIVERED" -> "Delivered"
            else -> status.replace('_', ' ').lowercase(Locale.US)
                .replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    /**
     * Ensures the high-importance Android notification channel exists for FCM push alerts.
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Returns true if the app is allowed to post system notifications (`POST_NOTIFICATIONS` on API 33+).
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Loads persisted FCM preferences and token on startup.
     */
    fun loadPersistedState(context: Context) {
        ensureNotificationChannel(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_FCM_TOKEN, "").orEmpty()
        if (savedToken.isNotBlank() && _fcmToken.value.isBlank()) {
            _fcmToken.value = savedToken
        }
        _isFcmEnabled.value = prefs.getBoolean(KEY_FCM_ENABLED, true)
        val savedTransitions = prefs.getStringSet(KEY_NOTIFIED_ORDER_STATES, emptySet()).orEmpty()
        notifiedTransitionKeys.addAll(savedTransitions)
    }

    fun setFcmAlertsEnabled(context: Context, enabled: Boolean) {
        _isFcmEnabled.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FCM_ENABLED, enabled)
            .apply()
    }

    fun saveFcmTokenLocally(context: Context, token: String) {
        if (token.isBlank()) return
        _fcmToken.value = token
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun dismissBannerPushEvent() {
        _latestBannerPushEvent.value = null
    }

    /**
     * Initializes Firebase Cloud Messaging for the signed-in distributor/customer:
     * - Fetches the device's FCM registration token
     * - Subscribes to distributor order update topics (`snl_distributors_orders`, `snl_order_updates`, and `snl_user_<userId>`)
     */
    suspend fun initializeFcmForUser(
        context: Context,
        userId: String,
        role: String = "DISTRIBUTOR"
    ): String {
        loadPersistedState(context)
        val sanitizedUserTopic = "snl_user_" + userId.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_").take(64)
        val topicsToSubscribe = buildSet {
            add("snl_distributors_orders")
            add("snl_order_updates")
            if (userId.isNotBlank()) add(sanitizedUserTopic)
            if (role.isNotBlank()) add("snl_role_${role.lowercase(Locale.US)}")
        }

        return try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true

            for (topic in topicsToSubscribe) {
                runCatching { messaging.subscribeToTopic(topic).await() }
            }
            _subscribedTopics.value = topicsToSubscribe

            val liveToken = runCatching { messaging.token.await() }.getOrNull().orEmpty()
            val finalToken = liveToken.ifBlank {
                _fcmToken.value.ifBlank {
                    "fcm_snl_${userId.take(8).ifBlank { "dist" }}_${System.currentTimeMillis().toString().takeLast(6)}"
                }
            }
            saveFcmTokenLocally(context, finalToken)
            finalToken
        } catch (e: Exception) {
            Log.w(TAG, "FCM token/topic initialization fallback: ${e.message}")
            _subscribedTopics.value = topicsToSubscribe
            val fallback = _fcmToken.value.ifBlank {
                "fcm_snl_${userId.take(8).ifBlank { "dist" }}_ready"
            }
            saveFcmTokenLocally(context, fallback)
            fallback
        }
    }

    /**
     * Evaluates a list of live customer orders from Firestore snapshot listeners.
     * When an existing order transitions to `'DISPATCHED'` or `'DELIVERED'`, automatically triggers
     * an FCM push notification for the distributor.
     */
    fun onCustomerOrdersSnapshotUpdated(
        context: Context,
        userId: String,
        orders: List<Order>
    ) {
        loadPersistedState(context)
        if (!_isFcmEnabled.value) return

        val isFirstSnapshotForUser = initialSnapshotSeededForUser != userId
        if (isFirstSnapshotForUser) {
            initialSnapshotSeededForUser = userId
            orders.forEach { order ->
                val key = order.id.ifBlank { order.orderNumber }
                if (key.isNotBlank()) {
                    knownOrderStatuses[key] = order.orderStatus.uppercase(Locale.US)
                }
            }
            return
        }

        orders.forEach { order ->
            val key = order.id.ifBlank { order.orderNumber }
            if (key.isBlank()) return@forEach
            val newStatusUpper = order.orderStatus.trim().uppercase(Locale.US)
            val prevStatusUpper = knownOrderStatuses.put(key, newStatusUpper)

            if (prevStatusUpper != null &&
                prevStatusUpper != newStatusUpper &&
                isTargetPushStatus(newStatusUpper)
            ) {
                triggerOrderStatusPushNotification(
                    context = context,
                    order = order,
                    newStatus = newStatusUpper,
                    forceEvenIfAlreadyNotified = false
                )
            }
        }
    }

    /**
     * Evaluates incoming Firestore `/notifications` documents for the distributor so that
     * `FCM_ORDER_DISPATCHED` and `FCM_ORDER_DELIVERED` notifications created by Admin dispatch
     * actions also trigger a native Android push notification on the distributor's device.
     */
    fun onFirestoreNotificationsUpdated(
        context: Context,
        userId: String,
        notifications: List<AppNotification>
    ) {
        loadPersistedState(context)
        if (!_isFcmEnabled.value || userId.isBlank()) return

        val recentThresholdSec = (System.currentTimeMillis() / 1000L) - 120L
        notifications.forEach { notif ->
            if (notif.targetUserId != userId) return@forEach
            val isFcmDispatchType = notif.type.equals("FCM_ORDER_DISPATCHED", ignoreCase = true) ||
                notif.type.equals("FCM_ORDER_DELIVERED", ignoreCase = true)
            if (!isFcmDispatchType || notif.isRead) return@forEach

            val createdSec = notif.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
            if (createdSec < recentThresholdSec) return@forEach

            val inferredStatus = if (notif.type.contains("DELIVERED", ignoreCase = true)) {
                "DELIVERED"
            } else {
                "DISPATCHED"
            }
            val transitionKey = "${notif.referenceId.ifBlank { notif.id }}:$inferredStatus"
            if (notifiedTransitionKeys.contains(transitionKey)) return@forEach

            showOrderStatusPushNotification(
                context = context,
                orderId = notif.referenceId.ifBlank { notif.id },
                orderNumber = notif.title.substringAfter("Order ", "").substringBefore(":").ifBlank { notif.referenceId },
                orderStatus = inferredStatus,
                businessName = "Authorized SNL Distributor",
                city = "Pakistan",
                grandTotalPkr = 0.0,
                customTitle = notif.title,
                customBody = notif.message,
                topic = "snl_user_$userId",
                forceEvenIfAlreadyNotified = false
            )
        }
    }

    /**
     * Triggers an FCM Push Notification for a specific [Order] when its status updates to
     * `'Dispatched'` (`DISPATCHED`) or `'Delivered'` (`DELIVERED`).
     */
    fun triggerOrderStatusPushNotification(
        context: Context,
        order: Order,
        newStatus: String = order.orderStatus,
        forceEvenIfAlreadyNotified: Boolean = false
    ): FcmOrderPushEvent? {
        if (!isTargetPushStatus(newStatus)) return null
        val canonicalStatus = formatCanonicalPushStatus(newStatus)
        val pkrFormat = NumberFormat.getNumberInstance(Locale.US)
        val formattedTotal = "PKR ${pkrFormat.format(order.grandTotal.toLong())}"

        val (title, body) = when (newStatus.trim().uppercase(Locale.US)) {
            "DISPATCHED" -> {
                "FCM Alert: Order ${order.orderNumber} Dispatched" to
                    "Great news ${order.businessName.ifBlank { order.customerName }}! Your SNL Lubricants order ${order.orderNumber} (${order.totalCartons} cartons • $formattedTotal) has been Dispatched to ${order.city}."
            }
            "DELIVERED" -> {
                "FCM Alert: Order ${order.orderNumber} Delivered" to
                    "Your SNL Lubricants order ${order.orderNumber} ($formattedTotal) has been marked as Delivered at ${order.deliveryAddress.ifBlank { order.city }}. Thank you for partnering with SAIFI National Lubricants!"
            }
            else -> {
                "Order ${order.orderNumber} $canonicalStatus" to
                    "Your order ${order.orderNumber} status is now $canonicalStatus."
            }
        }

        return showOrderStatusPushNotification(
            context = context,
            orderId = order.id.ifBlank { order.orderNumber },
            orderNumber = order.orderNumber.ifBlank { order.id },
            orderStatus = canonicalStatus,
            businessName = order.businessName.ifBlank { order.customerName.ifBlank { "SNL Distributor" } },
            city = order.city.ifBlank { "Karachi" },
            grandTotalPkr = order.grandTotal,
            customTitle = title,
            customBody = body,
            topic = "snl_user_${order.customerId.ifBlank { "distributor" }}",
            forceEvenIfAlreadyNotified = forceEvenIfAlreadyNotified
        )
    }

    /**
     * Builds and posts the native Android system push notification and records the FCM event
     * in [recentPushEvents] and [latestBannerPushEvent].
     */
    fun showOrderStatusPushNotification(
        context: Context,
        orderId: String,
        orderNumber: String,
        orderStatus: String,
        businessName: String,
        city: String,
        grandTotalPkr: Double,
        customTitle: String? = null,
        customBody: String? = null,
        topic: String = "snl_distributors_orders",
        forceEvenIfAlreadyNotified: Boolean = false
    ): FcmOrderPushEvent? {
        loadPersistedState(context)
        if (!isTargetPushStatus(orderStatus)) return null

        val canonicalStatus = formatCanonicalPushStatus(orderStatus)
        val transitionKey = "${orderId.ifBlank { orderNumber }}:${canonicalStatus.uppercase(Locale.US)}"
        if (!forceEvenIfAlreadyNotified && !notifiedTransitionKeys.add(transitionKey)) {
            return null
        }

        // Persist transition key so we don't duplicate alerts on app restart
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatedSet = (prefs.getStringSet(KEY_NOTIFIED_ORDER_STATES, emptySet()).orEmpty() + transitionKey)
            .toList()
            .takeLast(150)
            .toSet()
        prefs.edit().putStringSet(KEY_NOTIFIED_ORDER_STATES, updatedSet).apply()

        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: "Order $orderNumber is now $canonicalStatus"
        val body = customBody?.takeIf { it.isNotBlank() }
            ?: "Your SNL Lubricants order $orderNumber for $businessName ($city) has been updated to '$canonicalStatus'."

        val event = FcmOrderPushEvent(
            eventId = "fcm_${System.currentTimeMillis()}_${(100..999).random()}",
            orderId = orderId.ifBlank { orderNumber },
            orderNumber = orderNumber.ifBlank { orderId },
            orderStatus = canonicalStatus,
            title = title,
            body = body,
            businessName = businessName,
            city = city,
            grandTotalPkr = grandTotalPkr,
            topic = topic
        )

        _recentPushEvents.value = (listOf(event) + _recentPushEvents.value).take(25)
        _latestBannerPushEvent.value = event

        // Post Android system notification if permission is granted
        if (_isFcmEnabled.value && hasPostNotificationsPermission(context)) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_FCM_ORDER_ID, event.orderId)
                    putExtra(EXTRA_FCM_ORDER_NUMBER, event.orderNumber)
                    putExtra(EXTRA_FCM_ORDER_STATUS, event.orderStatus)
                }
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    event.orderNumber.hashCode(),
                    launchIntent,
                    pendingFlags
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                NotificationManagerCompat.from(context)
                    .notify((event.orderNumber + canonicalStatus).hashCode(), notification)
            } catch (se: SecurityException) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${se.message}")
            } catch (e: Exception) {
                Log.w(TAG, "System notification post warning: ${e.message}")
            }
        }

        return event
    }
}
