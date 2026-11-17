# Swatantra v7.1 Workspace Rules (Mandatory Agent Guardrails)

1. **Zero Third-Party Bloat & Zero Polling**:
   - Do NOT add Room, Retrofit, OkHttp, Dagger/Hilt, or Jetpack Compose dependencies to `app/build.gradle.kts`. Keep the APK under 1.5 MB using pure Android SDK classes already loaded in Zygote RAM.
   - Never write `while(true)` polling loops or subscribe to unfiltered `TYPE_WINDOW_CONTENT_CHANGED` events. Keep `notificationTimeout = 1500L` in `UniversalLectureInspector.kt` and flush SQLite WAL ticks at most once every 15-60 seconds or on state transitions.

2. **Zero-Brick & Device-Encrypted Storage Invariants**:
   - Never remove any package from `immutableSystemCore` in `FourBucketClassifier.kt` (`WebView.getCurrentWebViewPackage()`, `com.google.android.gms`, `com.android.settings`, `com.miui.securitycenter`, `com.coloros.safecenter`, `com.vivo.abe`).
   - Always use `context.createDeviceProtectedStorageContext()` for all databases and `SharedPreferences`. Never access Credential-Encrypted storage so Direct Boot (`LOCKED_BOOT_COMPLETED`) never throws `IllegalStateException`.
   - Always preserve `setFactoryResetProtectionEnabled(false)` and the 3-crash-in-60s circuit breaker in `SwatantraApp.kt`.
   - Never call `dpm.clearDeviceOwnerApp()` before verifying `stillHidden.isEmpty()` in `AtomicDeprovisionManager.kt`.

3. **Life-Safety Call Invariant**:
   - Never remove or bypass `isEmergencyOrCallbackImmune()` in `AntiGossipCallEngine.kt` (`112`, `100`, `101`, `102`, `108`, `1091`, `1098`, `181`, `1930` + 30-minute PSAP callback immunity window).

4. **GitHub Telemetry Sync (`CODE-ops-stack/calm`)**:
   - After every build or test run, run `powershell -ExecutionPolicy Bypass -File scripts/sync_and_verify.ps1` to log compiler/test output to `error.log`, update `TODO.md`, and push to `https://github.com/CODE-ops-stack/calm.git`.
