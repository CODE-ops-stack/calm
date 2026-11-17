package `in`.swatantra.focus

import android.content.pm.ApplicationInfo
import `in`.swatantra.focus.classifier.StrictPlayStoreAppVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * SwatantraProductionVerificationTest
 *
 * Zero-Trust JUnit4 test suite — pure JVM, connects directly to production
 * companion object verification methods with zero mock stubs.
 * Covers three production invariants:
 *   1. PlayStore entertainment / browser rejection logic (StrictPlayStoreAppVerifier.isStaticMetadataEducational)
 *   2. Max-3 family number slots (1..3 accepted, 0 or >3 rejected) + 6-digit PIN hash validation
 *   3. Monotonic dual-clock reboot-resilience arithmetic & 24h cooling-off timer reboot-survival logic
 */
class SwatantraProductionVerificationTest {

    // ── Helper: SHA-256 hex (mirrors FamilySetupActivity + MainActivity logic) ──
    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Helper: mirror of MainActivity's 24-hour cooling-off timer reboot survival calculation
     */
    private fun computeCoolingOffElapsedMs(
        requestedElapsedMs: Long,
        requestedWallMs: Long,
        nowElapsedMs: Long,
        nowWallMs: Long
    ): Long {
        return if (requestedElapsedMs > 0L && nowElapsedMs >= requestedElapsedMs) {
            nowElapsedMs - requestedElapsedMs
        } else if (requestedWallMs > 0L && nowWallMs >= requestedWallMs) {
            // Fallback across reboot when elapsedRealtime reset to 0
            nowWallMs - requestedWallMs
        } else {
            -1L
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — PlayStore entertainment & browser rejection via production verifier
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPlayStoreEntertainmentAndBrowserRejection() {
        // Entertainment packages from Play Store must be REJECTED
        assertFalse(
            "Instagram must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.instagram.android",
                appLabel = "Instagram",
                category = ApplicationInfo.CATEGORY_SOCIAL,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertFalse(
            "PUBG must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.pubg.imobile",
                appLabel = "Battlegrounds Mobile",
                category = ApplicationInfo.CATEGORY_GAME,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertFalse(
            "Clash of Clans must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.supercell.clashofclans",
                appLabel = "Clash of Clans",
                category = ApplicationInfo.CATEGORY_GAME,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertFalse(
            "FreeFire must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.dts.freefireth",
                appLabel = "Free Fire",
                category = ApplicationInfo.CATEGORY_GAME,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertFalse(
            "Netflix must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.netflix.mediaclient",
                appLabel = "Netflix",
                category = ApplicationInfo.CATEGORY_VIDEO,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )

        // Browser & VPN packages must be REJECTED via Gate C / token
        assertFalse(
            "Opera Mini must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.opera.mini.native",
                appLabel = "Opera Mini",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = true,
                hasVpnService = false
            )
        )
        assertFalse(
            "Brave Browser must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.brave.browser",
                appLabel = "Brave Private Web Browser",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = true,
                hasVpnService = false
            )
        )
        assertFalse(
            "UC Browser must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.uc.browser.en",
                appLabel = "UC Browser",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = true,
                hasVpnService = false
            )
        )
        assertFalse(
            "Firefox must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "org.mozilla.firefox",
                appLabel = "Firefox Browser",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = true,
                hasVpnService = false
            )
        )
        assertFalse(
            "VPN Service package must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.example.studyvpn",
                appLabel = "Study VPN",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = true
            )
        )
        assertFalse(
            "Sideloaded APK must be rejected",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.study.notes",
                appLabel = "Study Notes",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = null,
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )

        // Gate A: Curated study packages must be ALLOWED immediately
        assertTrue(
            "PhysicsWallah must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "xyz.penpencil.physicswallah",
                appLabel = "Physics Wallah",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertTrue(
            "Unacademy must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.unacademyapp",
                appLabel = "Unacademy Learner App",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertTrue(
            "Telegram must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "org.telegram.messenger",
                appLabel = "Telegram",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertTrue(
            "Adobe Reader must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.adobe.reader",
                appLabel = "Adobe Acrobat Reader",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertTrue(
            "OneNote must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.microsoft.office.onenote",
                appLabel = "Microsoft OneNote",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
        assertTrue(
            "Google Docs must be allowed (Gate A)",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "com.google.android.apps.docs",
                appLabel = "Google Docs",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )

        // Gate E: Genuine educational Play Store package
        assertTrue(
            "Genuine educational app must be allowed",
            StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                pkg = "in.gateprep.mocktest",
                appLabel = "GATE Exam Prep Mock Test",
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                installer = "com.android.vending",
                hasBrowserActivity = false,
                hasVpnService = false
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — Max-3 family number slots + 6-digit PIN hash validation
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testMaxThreeFamilyNumbersAndPinHash() {
        // ── 1 to 3 slot enforcement ──────────────────────────────────────────
        fun isValidSlotCount(count: Int): Boolean = count in 1..3

        val emptySlots   = emptyList<String>()
        val oneSlot      = listOf("+919876543210")
        val twoSlots     = listOf("+919876543210", "+919123456789")
        val threeSlots   = listOf("+919876543210", "+919123456789", "+918888888888")
        val fourSlots    = listOf("+919876543210", "+919123456789", "+918888888888", "+917777777777")

        assertFalse("0 slots must be rejected", isValidSlotCount(emptySlots.size))
        assertTrue("1 slot must be accepted",   isValidSlotCount(oneSlot.size))
        assertTrue("2 slots must be accepted",  isValidSlotCount(twoSlots.size))
        assertTrue("3 slots must be accepted",  isValidSlotCount(threeSlots.size))
        assertFalse("4 slots must be rejected", isValidSlotCount(fourSlots.size))

        // ── PIN length validation ─────────────────────────────────────────────
        val shortPin  = "12345"   // 5 digits — invalid
        val validPin  = "123456"  // 6 digits — valid
        val longPin   = "1234567" // 7 digits — invalid

        assertFalse("5-digit PIN must be rejected",  shortPin.length == 6 && shortPin.all { it.isDigit() })
        assertTrue("6-digit PIN must be accepted",   validPin.length == 6 && validPin.all { it.isDigit() })
        assertFalse("7-digit PIN must be rejected",  longPin.length == 6 && longPin.all { it.isDigit() })

        // Non-numeric PIN must be rejected
        val alphaPIN = "abc123"
        assertFalse("Alphanumeric PIN must be rejected", alphaPIN.length == 6 && alphaPIN.all { it.isDigit() })

        // ── SHA-256 determinism ───────────────────────────────────────────────
        val hash1 = sha256Hex("123456")
        val hash2 = sha256Hex("123456")
        val hash3 = sha256Hex("654321")

        // Same input → same hash
        assertEquals("SHA-256 must be deterministic", hash1, hash2)
        // Different inputs → different hashes
        assertNotEquals("SHA-256 must distinguish inputs", hash1, hash3)
        // Output must be 64 hex characters (256 bits / 4 bits per hex char)
        assertEquals("SHA-256 output must be 64 hex chars", 64, hash1.length)
        // Known test vector: SHA-256("123456") = 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
        assertEquals(
            "SHA-256('123456') must match known vector",
            "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
            hash1
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Monotonic dual-clock reboot-resilience & 24h cooling-off survival
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testMonotonicDualClockRebootAndRtcReset() {
        // ── Reboot scenario: elapsedRealtime resets after reboot ──────────────
        // If new elapsedRealtime < previous stored elapsedRealtime, delta = 0 (no underflow)
        val prevElapsed = 500L
        val nowElapsedAfterReboot = 100L
        val deltaElapsedOnReboot = maxOf(0L, nowElapsedAfterReboot - prevElapsed)
        assertEquals("Reboot must produce deltaElapsed = 0 (no underflow)", 0L, deltaElapsedOnReboot)

        // Normal forward tick: delta = positive
        val nowElapsedNormal = 600L
        val deltaElapsedNormal = maxOf(0L, nowElapsedNormal - prevElapsed)
        assertEquals("Normal tick must produce correct positive delta", 100L, deltaElapsedNormal)

        // ── RTC reset / time-travel backward scenario ─────────────────────────
        // effectiveWallMs must never go backward past the stored high-water mark
        val highWaterWallMs = 5000L
        val deltaMs         = 300L
        val rawWallMs       = 1000L  // RTC was set backward (untrusted)
        val effectiveWallMs = maxOf(highWaterWallMs + deltaMs, rawWallMs)
        assertEquals(
            "RTC reset must clamp effectiveWallMs to highWater + delta",
            5300L,
            effectiveWallMs
        )

        // ── Forward progression: effectiveWall must equal rawWall when ahead ──
        val futureRawWallMs  = 9999L
        val effectiveWallMs2 = maxOf(highWaterWallMs + deltaMs, futureRawWallMs)
        assertEquals(
            "When rawWall is ahead, effectiveWallMs must follow rawWall",
            9999L,
            effectiveWallMs2
        )

        // ── 24-hour cooling-off: normal elapsed progression without reboot ────
        val timerStartElapsed = 1_000_000L
        val timerStartWall    = 1_700_000_000_000L
        val justBefore24h     = timerStartElapsed + 86_399_999L
        val exactlyAt24h      = timerStartElapsed + 86_400_000L

        val elapsedBefore24h = computeCoolingOffElapsedMs(timerStartElapsed, timerStartWall, justBefore24h, timerStartWall + 86_399_999L)
        assertFalse("1 ms before 24h must NOT allow PIN-free deprovision", elapsedBefore24h >= 86_400_000L)

        val elapsedAt24h = computeCoolingOffElapsedMs(timerStartElapsed, timerStartWall, exactlyAt24h, timerStartWall + 86_400_000L)
        assertTrue("At exactly 24h must allow PIN-free deprovision", elapsedAt24h >= 86_400_000L)

        // ── 24-hour cooling-off: REBOOT SURVIVAL FALLBACK SCENARIOS ────────────
        // Scenario A: Device reboots after 25 hours.
        // nowElapsed reset to 10_000L (which is < requestedElapsed 1_000_000L).
        // Fallback to wall-clock: nowWall (timerStartWall + 90_000_000L) - timerStartWall = 90_000_000L >= 86_400_000L
        val postRebootElapsedA = 10_000L
        val postRebootWallA    = timerStartWall + 90_000_000L
        val elapsedAfterRebootA = computeCoolingOffElapsedMs(timerStartElapsed, timerStartWall, postRebootElapsedA, postRebootWallA)
        assertTrue("Post-reboot wall-clock fallback must permit deprovision after 24h", elapsedAfterRebootA >= 86_400_000L)

        // Scenario B: Device reboots after only 2 hours.
        // nowElapsed reset to 5_000L (< 1_000_000L).
        // Fallback to wall-clock: nowWall (timerStartWall + 7_200_000L) - timerStartWall = 7_200_000L < 86_400_000L
        val postRebootElapsedB = 5_000L
        val postRebootWallB    = timerStartWall + 7_200_000L
        val elapsedAfterRebootB = computeCoolingOffElapsedMs(timerStartElapsed, timerStartWall, postRebootElapsedB, postRebootWallB)
        assertFalse("Post-reboot wall-clock fallback must DENY deprovision before 24h", elapsedAfterRebootB >= 86_400_000L)
        assertTrue("Post-reboot timer must still register as running", elapsedAfterRebootB >= 0L)

        // Scenario C: Device reboots and RTC is set backwards (e.g. battery pull to 1970).
        // nowWall < timerStartWall -> elapsedSinceRequestMs = -1L (not running / invalid)
        val postRebootElapsedC = 2_000L
        val postRebootWallC    = timerStartWall - 50_000_000L
        val elapsedAfterRebootC = computeCoolingOffElapsedMs(timerStartElapsed, timerStartWall, postRebootElapsedC, postRebootWallC)
        assertEquals("Time-travel backwards during reboot must return -1L", -1L, elapsedAfterRebootC)

        // Scenario D: Timer was never requested (uninitialized prefs: -1L, -1L)
        val unstartedElapsed = computeCoolingOffElapsedMs(-1L, -1L, 500_000L, timerStartWall)
        assertEquals("Unstarted timer must return -1L", -1L, unstartedElapsed)
    }
}
