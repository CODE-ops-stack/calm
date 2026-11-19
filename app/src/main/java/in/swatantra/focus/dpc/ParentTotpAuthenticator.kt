package `in`.swatantra.focus.dpc

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Android SDK RFC 6238 TOTP (Google Authenticator compatible, ₹0 offline cost)
 * plus Offline 12-Character Recovery Key helper.
 */
object ParentTotpAuthenticator {
    const val TOTP_AUDIT_SYMBOL = "PARENT_TOTP_RFC6238_V7_1"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val TIME_STEP_SECONDS = 30L

    fun generateBase32Secret(length: Int = 16): String {
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(BASE32_ALPHABET[random.nextInt(BASE32_ALPHABET.length)])
            }
        }
    }

    fun generateRecoveryKey(): String {
        val random = SecureRandom()
        fun chunk(): String = buildString(4) {
            repeat(4) { append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]) }
        }
        return "SWT-${chunk()}-${chunk()}-${chunk()}"
    }

    fun normalizeRecoveryKey(input: String): String =
        input.uppercase().replace("-", "").replace(" ", "").trim()

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun decodeBase32(base32: String): ByteArray {
        val cleaned = base32.uppercase().replace("=", "").replace(" ", "").replace("-", "")
        val out = ByteArray((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var idx = 0
        for (c in cleaned) {
            val val5 = BASE32_ALPHABET.indexOf(c)
            if (val5 < 0) continue
            buffer = (buffer shl 5) or val5
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out[idx++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return out.copyOf(idx)
    }

    fun generateTotpForTimeStep(secretBase32: String, timeStep: Long): String {
        val keyBytes = decodeBase32(secretBase32)
        if (keyBytes.isEmpty()) return ""
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return "%06d".format(otp)
    }

    fun verifyTotpCode(
        secretBase32: String,
        enteredCode: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanCode = enteredCode.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) return false
        if (secretBase32.isBlank()) return false
        val currentStep = (currentTimeMs / 1000L) / TIME_STEP_SECONDS
        for (offset in -1L..1L) {
            val expected = generateTotpForTimeStep(secretBase32, currentStep + offset)
            if (expected.isNotEmpty() && MessageDigest.isEqual(
                    expected.toByteArray(Charsets.UTF_8),
                    cleanCode.toByteArray(Charsets.UTF_8)
                )
            ) {
                return true
            }
        }
        return false
    }
}
