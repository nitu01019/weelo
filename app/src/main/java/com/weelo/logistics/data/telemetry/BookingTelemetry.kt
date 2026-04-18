package com.weelo.logistics.data.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Thin wrapper around the customer-app's existing telemetry sink
 * (FirebaseCrashlytics), kept deliberately small so that:
 *
 *   1. Call sites stay readable — `BookingTelemetry.logLegacyFallbackInvoked(...)`
 *      reads like an analytics intent, not a Crashlytics implementation detail.
 *   2. Local JVM unit tests can replace the sink with an in-memory capture
 *      without pulling `FirebaseCrashlytics` onto the classpath (the Firebase
 *      SDK is not resolvable on the JVM test runtime — it needs Android).
 *
 * No new SDK is introduced — Crashlytics is already a dependency
 * (`app/build.gradle`: `firebase-crashlytics-ktx`) and is the same sink
 * used by `WeeloApplication.initializeSecurity()` and `logError(...)` helpers
 * in `CustomBookingActivity`, `MapSelectionActivity`, `WeeloPlacesAdapter`, and
 * `LoadingStateManager`.
 */
object BookingTelemetry {

    /**
     * Emits a single telemetry event capturing that the legacy `/orders` route
     * fallback was invoked from the primary `/bookings/orders` endpoint.
     *
     * P1-L1: This event lets us track the legacy-hit rate so the fallback +
     * legacy route can eventually be removed in a coordinated backend+client
     * release once the metric stays at zero for ≥ 4 weeks.
     */
    fun logLegacyFallbackInvoked(
        endpoint: String,
        primaryCode: Int,
        primaryErrorCode: String? = null
    ) {
        val errorCodeLabel = primaryErrorCode?.takeIf { it.isNotBlank() } ?: "none"
        try {
            sink.log(
                event = EVENT_LEGACY_FALLBACK_INVOKED,
                params = mapOf(
                    "endpoint" to endpoint,
                    "primary_code" to primaryCode.toString(),
                    "primary_error_code" to errorCodeLabel
                )
            )
        } catch (e: Throwable) {
            // Telemetry is best-effort — never fail the user-facing request path
            // because a logging sink hiccupped.
            Timber.w(e, "Failed to emit telemetry event: %s", EVENT_LEGACY_FALLBACK_INVOKED)
        }
    }

    /** Stable event name — surfaced verbatim in Crashlytics breadcrumbs and custom keys. */
    const val EVENT_LEGACY_FALLBACK_INVOKED: String = "booking_legacy_fallback_invoked"

    /** Swappable sink. Default: Crashlytics. Tests replace with an in-memory capture. */
    @JvmStatic
    @Suppress("MemberVisibilityCanBePrivate") // intentionally package/module-visible for tests
    var sink: TelemetrySink = CrashlyticsSink

    /**
     * Sink abstraction — keeps the call-site free of SDK plumbing and lets unit
     * tests capture events without bringing Firebase onto the classpath.
     */
    interface TelemetrySink {
        fun log(event: String, params: Map<String, String>)
    }

    private object CrashlyticsSink : TelemetrySink {
        override fun log(event: String, params: Map<String, String>) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            // Breadcrumb (shows up on any subsequent crash record).
            crashlytics.log(
                buildString {
                    append(event)
                    if (params.isNotEmpty()) {
                        append(' ')
                        params.entries.joinTo(this, separator = " ") { (k, v) -> "$k=$v" }
                    }
                }
            )
            // Custom keys — last-value-wins, mirroring WeeloApplication's pattern.
            crashlytics.setCustomKey("telemetry.last_event", event)
            params.forEach { (k, v) -> crashlytics.setCustomKey("telemetry.$event.$k", v) }
        }
    }
}
