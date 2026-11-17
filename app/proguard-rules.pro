# Swatantra v7.1 ProGuard Rules
# Keep ALL classes in the in.swatantra.focus package hierarchy
# (Kotlin source uses `in`.swatantra.focus; JVM bytecode uses in.swatantra.focus)
-keep class in.swatantra.focus.** { *; }
-keepclassmembers class in.swatantra.focus.** { *; }

# Explicit keeps for Android-entry-point classes (Activities, Services, Receivers)
-keep class in.swatantra.focus.SwatantraApp { *; }
-keep class in.swatantra.focus.dpc.SwatantraAdminReceiver { *; }
-keep class in.swatantra.focus.dpc.AtomicDeprovisionManager { *; }
-keep class in.swatantra.focus.lecture.UniversalLectureInspector { *; }
-keep class in.swatantra.focus.calls.DirectBootCallScreener { *; }
-keep class in.swatantra.focus.calls.SwatantraInCallService { *; }
-keep class in.swatantra.focus.calls.AntiGossipCallEngine { *; }
-keep class in.swatantra.focus.watchdog.SwatantraWatchdogService { *; }
-keep class in.swatantra.focus.network.SafeStudyDnsVpnService { *; }
-keep class in.swatantra.focus.classifier.FourBucketClassifier { *; }
-keep class in.swatantra.focus.classifier.StrictPlayStoreAppVerifier { *; }
-keep class in.swatantra.focus.classifier.PackageInstallReceiver { *; }
-keep class in.swatantra.focus.ledger.MonotonicDualClockLedger { *; }
-keep class in.swatantra.focus.ui.** { *; }

# Preserve reflective access used in AntiGossipCallEngine
-keepclassmembers class android.telephony.TelecomManager {
    public boolean isInEmergencyCall();
}

# Suppress warnings for reflection on private/hidden APIs
-dontwarn android.telephony.**

-optimizations !code/allocation/variable
