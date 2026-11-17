package `in`.swatantra.focus.ledger

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class MonotonicDualClockLedger(context: Context) {
    private val deContext = context.createDeviceProtectedStorageContext()
    private val db: SQLiteDatabase = deContext.openOrCreateDatabase(
        "swatantra_ledger.db",
        Context.MODE_PRIVATE or Context.MODE_ENABLE_WRITE_AHEAD_LOGGING,
        null
    ).apply {
        execSQL("PRAGMA synchronous = FULL;")
        execSQL("""
            CREATE TABLE IF NOT EXISTS ledger_state (
                slot INTEGER PRIMARY KEY,
                seq_no INTEGER NOT NULL,
                boot_count INTEGER NOT NULL,
                boot_id TEXT NOT NULL,
                last_elapsed_ms INTEGER NOT NULL,
                high_water_wall_ms INTEGER NOT NULL,
                daily_study_ms INTEGER NOT NULL,
                daily_call_ms INTEGER NOT NULL,
                monotonic_since_reset_ms INTEGER NOT NULL,
                day_epoch INTEGER NOT NULL,
                hmac_hex TEXT NOT NULL
            )
        """.trimIndent())
    }

    @Synchronized
    fun recordHeartbeatTick(
        isStudying: Boolean,
        isOnNonWhitelistedCall: Boolean,
        verifiedTlsOrGnssTimeMs: Long?
    ): Pair<Long, Long> {
        val nowElapsed = SystemClock.elapsedRealtime()
        val rawWall = System.currentTimeMillis()
        val bootCount = Settings.Global.getInt(deContext.contentResolver, Settings.Global.BOOT_COUNT, 0)
        val bootId = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown")

        val prev = loadLatestVerifiedSlot()
        val isSameBoot = prev != null &&
            prev.bootCount == bootCount &&
            prev.bootId == bootId &&
            nowElapsed >= prev.lastElapsedMs

        val deltaMs = if (isSameBoot) (nowElapsed - prev!!.lastElapsedMs).coerceAtLeast(0L) else nowElapsed

        val effectiveWallMs = when {
            prev == null -> verifiedTlsOrGnssTimeMs ?: rawWall
            verifiedTlsOrGnssTimeMs != null -> maxOf(prev.highWaterWallMs + deltaMs, verifiedTlsOrGnssTimeMs)
            else -> prev.highWaterWallMs + deltaMs
        }

        val candidateDayEpoch = effectiveWallMs / 86_400_000L
        val monotonicSinceReset = (prev?.monotonicSinceResetMs ?: 0L) + deltaMs

        val shouldRolloverDay = prev != null &&
            candidateDayEpoch > prev.dayEpoch &&
            (verifiedTlsOrGnssTimeMs != null || monotonicSinceReset >= 86_400_000L)

        val persistedDayEpoch = if (prev == null || shouldRolloverDay) candidateDayEpoch else prev.dayEpoch

        val nextStudyMs = (if (shouldRolloverDay) 0L else (prev?.dailyStudyMs ?: 0L)) + if (isStudying) deltaMs else 0L
        val nextCallMs = (if (shouldRolloverDay) 0L else (prev?.dailyCallMs ?: 0L)) + if (isOnNonWhitelistedCall) deltaMs else 0L
        val nextSeq = (prev?.seqNo ?: 0L) + 1L
        val targetSlot = (nextSeq % 2L).toInt()
        val nextMonotonicSinceReset = if (shouldRolloverDay) 0L else monotonicSinceReset

        val payload = "$nextSeq|$bootCount|$bootId|$nowElapsed|$effectiveWallMs|$nextStudyMs|$nextCallMs|$nextMonotonicSinceReset|$persistedDayEpoch"
        val hmac = computeHmacHex(payload)

        db.beginTransactionNonExclusive()
        try {
            db.execSQL(
                """INSERT OR REPLACE INTO ledger_state
                   (slot, seq_no, boot_count, boot_id, last_elapsed_ms, high_water_wall_ms, daily_study_ms, daily_call_ms, monotonic_since_reset_ms, day_epoch, hmac_hex)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any>(
                    targetSlot, nextSeq, bootCount, bootId, nowElapsed, effectiveWallMs,
                    nextStudyMs, nextCallMs, nextMonotonicSinceReset, persistedDayEpoch, hmac
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        deContext.getSharedPreferences("call_engine_de", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("daily_call_quota_exhausted", nextCallMs >= 3_600_000L)
            .commit()

        return Pair(nextStudyMs, nextCallMs)
    }

    private data class LedgerRow(
        val seqNo: Long, val bootCount: Int, val bootId: String,
        val lastElapsedMs: Long, val highWaterWallMs: Long,
        val dailyStudyMs: Long, val dailyCallMs: Long,
        val monotonicSinceResetMs: Long, val dayEpoch: Long, val hmacHex: String
    )

    private fun loadLatestVerifiedSlot(): LedgerRow? {
        db.rawQuery(
            "SELECT seq_no, boot_count, boot_id, last_elapsed_ms, high_water_wall_ms, daily_study_ms, daily_call_ms, monotonic_since_reset_ms, day_epoch, hmac_hex FROM ledger_state ORDER BY seq_no DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val row = LedgerRow(
                    c.getLong(0), c.getInt(1), c.getString(2), c.getLong(3),
                    c.getLong(4), c.getLong(5), c.getLong(6), c.getLong(7),
                    c.getLong(8), c.getString(9)
                )
                val payload = "${row.seqNo}|${row.bootCount}|${row.bootId}|${row.lastElapsedMs}|${row.highWaterWallMs}|${row.dailyStudyMs}|${row.dailyCallMs}|${row.monotonicSinceResetMs}|${row.dayEpoch}"
                if (computeHmacHex(payload) == row.hmacHex) return row
            }
        }
        return null
    }

    private fun computeHmacHex(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateDirectBootHmacKey())
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateDirectBootHmacKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "swatantra_ledger_hmac_v7"
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setUnlockedDeviceRequired(false)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
