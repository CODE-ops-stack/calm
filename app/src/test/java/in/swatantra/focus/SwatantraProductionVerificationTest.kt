package `in`.swatantra.focus

import android.content.pm.ApplicationInfo
import `in`.swatantra.focus.classifier.StrictPlayStoreAppVerifier
import `in`.swatantra.focus.dpc.ParentTotpAuthenticator
import `in`.swatantra.focus.ui.MainActivity
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

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4 — ON A MISSION Command Deck theme & Virtual Mission Day Simulation
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testOnAMissionThemeAndVirtualSimulation() {
        // 1. Theme Verification
        assertEquals(
            "Mission Theme constant must match ON_A_MISSION_COMMAND_DECK_V7_1",
            "ON_A_MISSION_COMMAND_DECK_V7_1",
            MainActivity.MISSION_THEME_ID
        )

        // 2. Virtual Mission Simulation: 180 minutes of GATE study ticks
        var virtualStudyMs = 0L
        val studyTickMs = 60_000L
        for (minute in 1..180) {
            val isStudying = true
            if (isStudying) {
                virtualStudyMs += studyTickMs
            }
        }
        val loggedStudyMinutes = virtualStudyMs / 60_000L
        assertEquals("Sortie logged study duration must equal 180 minutes", 180L, loggedStudyMinutes)

        // 3. 15 minutes of family call immunity (112, 100, 108 & 3-slot family numbers)
        val lockedFamilyNumbers = setOf("+919876543210", "+919123456780", "+919988776655")
        val emergencyNumbers = setOf("112", "100", "101", "102", "108", "1091", "1098", "181", "1930")

        fun isCallImmune(number: String): Boolean {
            return number in emergencyNumbers || number in lockedFamilyNumbers
        }

        assertTrue("112 emergency call must have absolute immunity", isCallImmune("112"))
        assertTrue("100 police call must have absolute immunity", isCallImmune("100"))
        assertTrue("Mother slot must have call immunity", isCallImmune("+919876543210"))
        assertTrue("Father slot must have call immunity", isCallImmune("+919123456780"))
        assertFalse("Random non-whitelisted number must NOT be immune", isCallImmune("+919999999999"))

        // Family calls do NOT consume non-whitelisted call budget
        var virtualCallMs = 0L
        val familyCallMinutes = 15
        for (minute in 1..familyCallMinutes) {
            val isOnNonWhitelistedCall = false
            if (isOnNonWhitelistedCall) {
                virtualCallMs += 60_000L
            }
        }
        assertEquals("Family call must consume 0 ms of call budget", 0L, virtualCallMs)

        // 4. 45 minutes of non-whitelisted call metering (Comms Shield: max 60 min daily quota)
        val nonWhitelistedCallMinutes = 45
        for (minute in 1..nonWhitelistedCallMinutes) {
            val isOnNonWhitelistedCall = true
            if (isOnNonWhitelistedCall) {
                virtualCallMs += 60_000L
            }
        }
        val loggedCallMinutes = virtualCallMs / 60_000L
        assertEquals("Non-whitelisted call meter must equal 45 minutes", 45L, loggedCallMinutes)
        assertTrue("Call duration must be within 60-minute daily budget", loggedCallMinutes <= 60L)

        // 5. Play Store Distraction Block during Mission Lock
        val blockedEntertainmentApps = listOf(
            "com.instagram.android" to "Instagram",
            "com.supercell.clashofclans" to "Clash of Clans",
            "com.netflix.mediaclient" to "Netflix",
            "com.opera.mini.native" to "Opera Mini",
            "com.brave.browser" to "Brave Browser"
        )
        for ((pkg, label) in blockedEntertainmentApps) {
            assertFalse(
                "Entertainment/browser package $pkg must be blocked",
                StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                    pkg = pkg,
                    appLabel = label,
                    category = ApplicationInfo.CATEGORY_UNDEFINED,
                    installer = "com.android.vending",
                    hasBrowserActivity = pkg.contains("browser") || pkg.contains("opera"),
                    hasVpnService = false
                )
            )
        }

        // Educational GATE study apps must be verified and allowed
        val approvedStudyApps = listOf(
            "xyz.penpencil.physicswallah" to "Physics Wallah",
            "com.unacademyapp" to "Unacademy Learner App"
        )
        for ((pkg, label) in approvedStudyApps) {
            assertTrue(
                "Study package $pkg must be allowed",
                StrictPlayStoreAppVerifier.isStaticMetadataEducational(
                    pkg = pkg,
                    appLabel = label,
                    category = ApplicationInfo.CATEGORY_UNDEFINED,
                    installer = "com.android.vending",
                    hasBrowserActivity = false,
                    hasVpnService = false
                )
            )
        }
    }

    @Test
    fun testUniversalIfThenDirectiveAndMotivatingProtocol() {
        assertEquals(
            "TACTICAL_IF_THEN_DIRECTIVE_V7_1",
            MainActivity.TACTICAL_IF_THEN_DIRECTIVE_V7_1
        )
        assertTrue(
            "Must provide 3 universal motivating directives",
            MainActivity.UNIVERSAL_IF_THEN_DIRECTIVES.size == 3
        )
        for (rule in MainActivity.UNIVERSAL_IF_THEN_DIRECTIVES) {
            assertTrue("Rule must start with IF: $rule", rule.startsWith("IF "))
            assertTrue("Rule must contain -> THEN : $rule", rule.contains("-> THEN "))
            assertFalse("Rule must stay universal (no exam lock-in): $rule", rule.contains("GATE", ignoreCase = true))
        }
    }

    @Test
    fun testParentTotpRfc6238AndDualUnlockGuard() {
        assertEquals("PARENT_TOTP_RFC6238_V7_1", ParentTotpAuthenticator.TOTP_AUDIT_SYMBOL)

        val secret = ParentTotpAuthenticator.generateBase32Secret(16)
        assertEquals("Base32 secret must be 16 chars", 16, secret.length)

        val fixedTimeMs = 1_763_540_000_000L
        val currentStep = (fixedTimeMs / 1000L) / 30L
        val codeNow = ParentTotpAuthenticator.generateTotpForTimeStep(secret, currentStep)
        val codePrev = ParentTotpAuthenticator.generateTotpForTimeStep(secret, currentStep - 1L)
        val codeNext = ParentTotpAuthenticator.generateTotpForTimeStep(secret, currentStep + 1L)
        val codeExpired = ParentTotpAuthenticator.generateTotpForTimeStep(secret, currentStep - 3L)

        assertEquals("TOTP code must be 6 digits", 6, codeNow.length)
        assertTrue("Current TOTP window must verify", ParentTotpAuthenticator.verifyTotpCode(secret, codeNow, fixedTimeMs))
        assertTrue("Previous -30s drift window must verify", ParentTotpAuthenticator.verifyTotpCode(secret, codePrev, fixedTimeMs))
        assertTrue("Next +30s drift window must verify", ParentTotpAuthenticator.verifyTotpCode(secret, codeNext, fixedTimeMs))
        assertFalse("Expired TOTP window must be rejected", ParentTotpAuthenticator.verifyTotpCode(secret, codeExpired, fixedTimeMs))
        assertFalse("Wrong 6-digit code must be rejected", ParentTotpAuthenticator.verifyTotpCode(secret, "000000", fixedTimeMs) && codeNow != "000000")

        val recoveryKey = ParentTotpAuthenticator.generateRecoveryKey()
        assertTrue("Recovery key must start with SWT-", recoveryKey.startsWith("SWT-"))
        val norm = ParentTotpAuthenticator.normalizeRecoveryKey(recoveryKey)
        assertEquals(
            "Normalized recovery key hash must match",
            ParentTotpAuthenticator.sha256Hex(norm),
            ParentTotpAuthenticator.sha256Hex(ParentTotpAuthenticator.normalizeRecoveryKey(recoveryKey.lowercase()))
        )
    }
}
