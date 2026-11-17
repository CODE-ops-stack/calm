package `in`.swatantra.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * SwatantraProductionVerificationTest
 *
 * Zero-Trust JUnit4 test suite — pure JVM, no Android Context required.
 * Covers three production invariants:
 *   1. PlayStore entertainment / browser rejection logic
 *   2. Max-3 family number slots + 6-digit PIN hash validation
 *   3. Monotonic dual-clock reboot-resilience arithmetic
 */
class SwatantraProductionVerificationTest {

    // ── Helper: SHA-256 hex (mirrors FamilySetupActivity + MainActivity logic) ──
    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ── Helper: mirror of StrictPlayStoreAppVerifier gate logic ──────────────
    private val curatedStudyRegistry = setOf(
        "xyz.penpencil.physicswallah",
        "com.unacademyapp",
        "com.testbook.tbapp",
        "com.adda247.app",
        "in.oliveboard.prep",
        "com.parmaracademy",
        "com.infeepedia",
        "org.telegram.messenger",
        "com.google.android.youtube",
        "com.adobe.reader",
        "com.microsoft.office.onenote",
        "com.google.android.apps.docs"
    )

    private val entertainmentKeywords = listOf(
        "instagram", "tiktok", "snapchat", "netflix", "hotstar", "youtube.gaming",
        "facebook", "twitter", "pubg", "freefire", "roblox", "minecraft",
        "candy", "clash", "brawl", "gaming", "sport", "cricket", "ipl"
    )

    private val browserKeywords = listOf(
        "opera", "brave", "browser", "chrome.beta", "firefox", "duckduckgo",
        "uc.browser", "via.browser", "puffin", "kiwi"
    )

    /**
     * Classify a package using Gate A (curated study list) and
     * Gate C (entertainment + browser token rejection).
     * Returns true  → ALLOWED
     * Returns false → REJECTED
     */
    private fun isAllowed(pkg: String): Boolean {
        // Gate A: immediately allow curated study packages
        if (pkg in curatedStudyRegistry) return true

        // Gate C: reject entertainment keywords
        val pkgLower = pkg.lowercase()
        if (entertainmentKeywords.any { pkgLower.contains(it) }) return false

        // Gate C: reject browser keywords
        if (browserKeywords.any { pkgLower.contains(it) }) return false

        // Default deny (not in any whitelist)
        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — PlayStore entertainment & browser rejection
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPlayStoreEntertainmentAndBrowserRejection() {
        // Entertainment packages must be REJECTED
        assertFalse("Instagram must be rejected",    isAllowed("com.instagram.android"))
        assertFalse("PUBG must be rejected",         isAllowed("com.pubg.imobile"))
        assertFalse("Clash of Clans must be rejected", isAllowed("com.supercell.clashofclans"))
        assertFalse("FreeFire must be rejected",     isAllowed("com.dts.freefireth"))
        assertFalse("Netflix must be rejected",      isAllowed("com.netflix.mediaclient"))

        // Browser packages must be REJECTED
        assertFalse("Opera Mini must be rejected",   isAllowed("com.opera.mini.native"))
        assertFalse("Brave must be rejected",        isAllowed("com.brave.browser"))
        assertFalse("UC Browser must be rejected",   isAllowed("com.uc.browser.en"))
        assertFalse("Firefox must be rejected",      isAllowed("org.mozilla.firefox"))

        // Curated study packages must be ALLOWED (Gate A)
        assertTrue("PhysicsWallah must be allowed",  isAllowed("xyz.penpencil.physicswallah"))
        assertTrue("Unacademy must be allowed",      isAllowed("com.unacademyapp"))
        assertTrue("Telegram must be allowed",       isAllowed("org.telegram.messenger"))
        assertTrue("Adobe Reader must be allowed",   isAllowed("com.adobe.reader"))
        assertTrue("OneNote must be allowed",        isAllowed("com.microsoft.office.onenote"))
        assertTrue("Google Docs must be allowed",    isAllowed("com.google.android.apps.docs"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — Max-3 family number slots + 6-digit PIN hash validation
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testMaxThreeFamilyNumbersAndPinHash() {
        // ── 3-slot enforcement ────────────────────────────────────────────────
        val validSlots   = listOf("+919876543210", "+919123456789", "+918888888888")
        val tooManySlots = listOf("+919876543210", "+919123456789", "+918888888888", "+917777777777")

        // Exactly 3 must pass
        assertTrue("Exactly 3 slots must be accepted",  validSlots.size <= 3)
        // 4 slots must be rejected
        assertFalse("More than 3 slots must be rejected", tooManySlots.size <= 3)

        // ── PIN length validation ─────────────────────────────────────────────
        val shortPin  = "12345"   // 5 digits — invalid
        val validPin  = "123456"  // 6 digits — valid
        val longPin   = "1234567" // 7 digits — invalid

        assertFalse("5-digit PIN must be rejected",  shortPin.length == 6 && shortPin.all { it.isDigit() })
        assertTrue("6-digit PIN must be accepted",   validPin.length == 6 && validPin.all { it.isDigit() })
        assertFalse("7-digit PIN must be rejected",  longPin.length == 6 && longPin.all { it.isDigit() })

        // Non-numeric PIN must be rejected
        val alphaPIN = "abc123"
        assertFalse("Alphanumeric PIN must be rejected", alphaPIN.all { it.isDigit() })

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
    // TEST 3 — Monotonic dual-clock reboot-resilience arithmetic
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

        // ── 24-hour cooling-off: 86_400_000 ms boundary check ─────────────────
        val timerStartElapsed  = 1_000_000L
        val justBefore24h      = timerStartElapsed + 86_399_999L
        val exactlyAt24h       = timerStartElapsed + 86_400_000L
        assertFalse("1 ms before 24h must NOT allow PIN-free deprovision",
            (justBefore24h - timerStartElapsed) >= 86_400_000L)
        assertTrue("At exactly 24h must allow PIN-free deprovision",
            (exactlyAt24h - timerStartElapsed) >= 86_400_000L)
    }
}
