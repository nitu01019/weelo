package com.weelo.logistics.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.weelo.logistics.R
import com.weelo.logistics.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * FIREBASE CLOUD MESSAGING SERVICE - Push Notifications (Customer App)
 * =============================================================================
 * 
 * Handles push notifications for:
 * - Booking confirmations
 * - Driver assigned notifications
 * - Driver arriving notifications  
 * - Trip status updates
 * - Promotional messages
 * 
 * ARCHITECTURE:
 * - FCM is used ONLY for wake-up/background notifications
 * - Real-time data comes via WebSocket when app is open
 * - FCM triggers app to connect to backend for actual data
 * =============================================================================
 */
class WeeloFirebaseService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "WeeloFCM"
        
        // Notification channels
        private const val CHANNEL_BOOKING = "weelo_booking"
        private const val CHANNEL_DRIVER = "weelo_driver"
        private const val CHANNEL_PROMO = "weelo_promo"
        
        // Notification types from backend
        const val TYPE_BOOKING_CONFIRMED = "booking_confirmed"
        const val TYPE_DRIVER_ASSIGNED = "driver_assigned"
        const val TYPE_DRIVER_ARRIVING = "driver_arriving"
        const val TYPE_TRIP_STARTED = "trip_started"
        const val TYPE_TRIP_COMPLETED = "trip_completed"
        const val TYPE_PAYMENT_RECEIVED = "payment_received"
        const val TYPE_PROMOTIONAL = "promotional"
        
        // SharedFlow for broadcasting notifications to UI
        private val _notificationFlow = MutableSharedFlow<FCMNotification>(replay = 1)
        val notificationFlow: SharedFlow<FCMNotification> = _notificationFlow.asSharedFlow()
        
        // Store FCM token for sending to backend
        private var fcmToken: String? = null
        
        fun getToken(): String? = fcmToken
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    /**
     * Called when FCM token is generated or refreshed
     * Send this token to your backend to enable push notifications
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔑 New FCM Token: $token")
        fcmToken = token
        
        // TODO: Send token to backend
        // Use TokenManager to get auth token, then call API
        // apiService.updateFcmToken(token)
    }
    
    /**
     * Called when a push notification is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "📩 Message received from: ${remoteMessage.from}")
        
        // Parse notification data
        val data = remoteMessage.data
        val notification = parseNotification(data, remoteMessage.notification)
        
        // Broadcast to UI if app is in foreground
        CoroutineScope(Dispatchers.Main).launch {
            _notificationFlow.emit(notification)
        }
        
        // Show notification if app is in background or data-only message
        if (shouldShowNotification(notification)) {
            showNotification(notification)
        }
    }
    
    private fun parseNotification(
        data: Map<String, String>,
        remoteNotification: RemoteMessage.Notification?
    ): FCMNotification {
        return FCMNotification(
            type = data["type"] ?: TYPE_PROMOTIONAL,
            title = data["title"] ?: remoteNotification?.title ?: "Weelo",
            body = data["body"] ?: remoteNotification?.body ?: "",
            data = data,
            bookingId = data["bookingId"],
            driverId = data["driverId"],
            driverName = data["driverName"],
            driverPhone = data["driverPhone"],
            vehicleNumber = data["vehicleNumber"],
            estimatedArrival = data["eta"]
        )
    }
    
    private fun shouldShowNotification(notification: FCMNotification): Boolean {
        // Always show important notifications
        return when (notification.type) {
            TYPE_DRIVER_ASSIGNED,
            TYPE_DRIVER_ARRIVING,
            TYPE_TRIP_STARTED,
            TYPE_TRIP_COMPLETED -> true
            else -> true // Show all notifications for now
        }
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Booking channel - High importance
            val bookingChannel = NotificationChannel(
                CHANNEL_BOOKING,
                "Booking Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your bookings"
                enableVibration(true)
            }
            
            // Driver channel - High importance
            val driverChannel = NotificationChannel(
                CHANNEL_DRIVER,
                "Driver Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your driver"
                enableVibration(true)
            }
            
            // Promo channel - Default importance
            val promoChannel = NotificationChannel(
                CHANNEL_PROMO,
                "Offers & Promotions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Special offers and promotions"
            }
            
            notificationManager.createNotificationChannels(
                listOf(bookingChannel, driverChannel, promoChannel)
            )
        }
    }
    
    private fun getChannelForType(type: String): String {
        return when (type) {
            TYPE_BOOKING_CONFIRMED,
            TYPE_TRIP_STARTED,
            TYPE_TRIP_COMPLETED,
            TYPE_PAYMENT_RECEIVED -> CHANNEL_BOOKING
            
            TYPE_DRIVER_ASSIGNED,
            TYPE_DRIVER_ARRIVING -> CHANNEL_DRIVER
            
            else -> CHANNEL_PROMO
        }
    }
    
    private fun showNotification(notification: FCMNotification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", notification.type)
            notification.bookingId?.let { putExtra("booking_id", it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val channelId = getChannelForType(notification.type)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Add extra info for driver notifications
        if (notification.type == TYPE_DRIVER_ASSIGNED || notification.type == TYPE_DRIVER_ARRIVING) {
            notification.driverName?.let { driverName ->
                val bigText = buildString {
                    append(notification.body)
                    notification.vehicleNumber?.let { append("\nVehicle: $it") }
                    notification.estimatedArrival?.let { append("\nETA: $it") }
                }
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            }
        }
        
        val notificationId = (notification.bookingId ?: System.currentTimeMillis().toString()).hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "📬 Notification shown: ${notification.title}")
    }
}

/**
 * FCM Notification data class for Customer App
 */
data class FCMNotification(
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val bookingId: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val vehicleNumber: String? = null,
    val estimatedArrival: String? = null
)
