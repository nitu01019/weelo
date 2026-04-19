package com.weelo.logistics.data.telemetry

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/**
 * P1-L1 — `booking_legacy_fallback_invoked` telemetry emission unit tests.
 *
 * Contract under test:
 *   Every time `BookingApiRepository.createOrder(...)` falls back from the
 *   primary `/bookings/orders` endpoint to the legacy `/orders` endpoint on
 *   400/404/405/422/501 responses, a telemetry event is emitted with:
 *     - event name                       = "booking_legacy_fallback_invoked"
 *     - params.endpoint                  = "bookings/orders"
 *     - params.primary_code              = the HTTP status code as a string
 *     - params.primary_error_code        = backend error code or "none"
 *
 * See:
 *   - `.planning/verification/ISSUES-AND-SOLUTIONS.md#L1`
 *   - `BookingApiRepository.kt` createOrder fallback branch (~ line 587)
 *
 * These are pure JVM unit tests — Firebase Crashlytics requires Android, so
 * the telemetry sink is swapped for an in-memory capture for the duration of
 * the test. The production sink (CrashlyticsSink) is exercised via
 * instrumentation on-device.
 */
class BookingTelemetryTest {

    private class CapturingSink : BookingTelemetry.TelemetrySink {
        data class Entry(val event: String, val params: Map<String, String>)
        val entries = mutableListOf<Entry>()
        override fun log(event: String, params: Map<String, String>) {
            entries += Entry(event, params)
        }
    }

    private val captured = CapturingSink()
    private val originalSink: BookingTelemetry.TelemetrySink = BookingTelemetry.sink

    init {
        BookingTelemetry.sink = captured
    }

    @After
    fun restoreSink() {
        BookingTelemetry.sink = originalSink
    }

    @Test
    fun `event name is the canonical booking_legacy_fallback_invoked`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 404,
            primaryErrorCode = null
        )
        assertThat(captured.entries).hasSize(1)
        assertThat(captured.entries.first().event)
            .isEqualTo(BookingTelemetry.EVENT_LEGACY_FALLBACK_INVOKED)
        assertThat(BookingTelemetry.EVENT_LEGACY_FALLBACK_INVOKED)
            .isEqualTo("booking_legacy_fallback_invoked")
    }

    @Test
    fun `400 response emits exactly one event with primary_code=400`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 400,
            primaryErrorCode = "VALIDATION_FAILED"
        )
        val entry = captured.entries.single()
        assertThat(entry.params["endpoint"]).isEqualTo("bookings/orders")
        assertThat(entry.params["primary_code"]).isEqualTo("400")
        assertThat(entry.params["primary_error_code"]).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `404 response emits one event with primary_code=404`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 404,
            primaryErrorCode = "ROUTE_NOT_FOUND"
        )
        assertThat(captured.entries.single().params["primary_code"]).isEqualTo("404")
    }

    @Test
    fun `422 response emits one event with primary_code=422`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 422,
            primaryErrorCode = null
        )
        val entry = captured.entries.single()
        assertThat(entry.params["primary_code"]).isEqualTo("422")
        // null error code is normalized to the literal "none" so the metric
        // has a stable cardinality in the downstream dashboard.
        assertThat(entry.params["primary_error_code"]).isEqualTo("none")
    }

    @Test
    fun `501 response emits one event with primary_code=501`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 501,
            primaryErrorCode = "NOT_IMPLEMENTED"
        )
        assertThat(captured.entries.single().params["primary_code"]).isEqualTo("501")
    }

    @Test
    fun `blank error code is normalized to none for stable cardinality`() {
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 404,
            primaryErrorCode = "   "
        )
        assertThat(captured.entries.single().params["primary_error_code"]).isEqualTo("none")
    }

    @Test
    fun `telemetry never throws even when the underlying sink does`() {
        BookingTelemetry.sink = object : BookingTelemetry.TelemetrySink {
            override fun log(event: String, params: Map<String, String>) {
                throw RuntimeException("Crashlytics is down")
            }
        }
        // Must NOT propagate — the booking request path must not fail on
        // telemetry hiccups. The best-effort catch in BookingTelemetry itself
        // is what covers this.
        BookingTelemetry.logLegacyFallbackInvoked(
            endpoint = "bookings/orders",
            primaryCode = 500,
            primaryErrorCode = null
        )
    }

    @Test
    fun `sink is called once per invocation - four fallbacks in a row yield four entries`() {
        listOf(400, 404, 422, 501).forEach { code ->
            BookingTelemetry.logLegacyFallbackInvoked(
                endpoint = "bookings/orders",
                primaryCode = code,
                primaryErrorCode = null
            )
        }
        assertThat(captured.entries.size).isEqualTo(4)
        assertThat(captured.entries.map { it.params["primary_code"] })
            .containsExactly("400", "404", "422", "501")
            .inOrder()
    }
}
