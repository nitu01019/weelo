package com.weelo.logistics.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.weelo.logistics.R
import com.weelo.logistics.presentation.booking.BookingTrackingActivity
import timber.log.Timber

/**
 * =============================================================================
 * TRACKING FOREGROUND SERVICE — Keeps WebSocket Alive in Background
 * =============================================================================
 *
 * PURPOSE:
 * When the customer switches to WhatsApp/SMS during an active trip, Android
 * may kill the app process after ~60s. This ForegroundService tells the OS
 * "this process is doing important work" so it keeps the WebSocket alive.
 *
 * HOW IT WORKS:
 * - BookingTrackingActivity starts this service when tracking begins
 * - Shows a silent persistent notification: "Trip in progress"
 * - Tapping the notification reopens BookingTrackingActivity
 * - BookingTrackingActivity stops this service onDestroy()
 *
 * WHAT IT DOES NOT DO:
 * - Does NOT manage the WebSocket connection itself (WebSocketService handles that)
 * - Does NOT do ANY location work (TrackingRepository handles that)
 * - Purely an Android process-keep-alive mechanism
 *
 * INDUSTRY STANDARD:
 * - Uber, Ola, Google Maps all use ForegroundService during active trips
 * - FOREGROUND_SERVICE_DATA_SYNC type (Android 14+) — for network data sync
 * - START_STICKY — OS restarts if somehow killed
 *
 * =============================================================================
 */
class TrackingForegroundService : Service() {

    companion object {
        private const val TAG = "TrackingFgService"

        private const val NOTIFICATION_CHANNEL_ID = "trip_tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Trip Tracking"
        private const val NOTIFICATION_ID = 2001

        const val EXTRA_BOOKING_ID = "extra_booking_id"

        /**
         * Start the foreground service for a booking.
         */
        fun start(context: Context, bookingId: String) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Timber.d("$TAG: Start requested for booking $bookingId")
        }

        /**
         * Stop the foreground service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingForegroundService::class.java))
            Timber.d("$TAG: Stop requested")
        }
    }

    private var bookingId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("$TAG: Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bookingId = intent?.getStringExtra(EXTRA_BOOKING_ID)

        Timber.i("$TAG: Starting foreground for booking $bookingId")
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: Service destroyed")
    }

    /**
     * Create notification channel (Android 8+).
     * IMPORTANCE_LOW = no sound, no vibration — silent but visible.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when your trip is being tracked"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the persistent notification.
     * Tapping opens BookingTrackingActivity with the booking ID.
     */
    private fun createNotification(): Notification {
        val tapIntent = Intent(this, BookingTrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            bookingId?.let { putExtra("booking_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Trip in Progress")
            .setContentText("Tracking your truck delivery")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
