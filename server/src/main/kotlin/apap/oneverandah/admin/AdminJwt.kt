package apap.oneverandah.admin

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal HS256 JWT helper for the admin portal.
 *
 * Tokens intentionally omit an `exp` claim so a successful sign-in yields a
 * never-expiring credential the browser can keep in local storage.
 */
object AdminJwt {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun issue(secret: String, subject: String = "admin"): String {
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString(
            """{"sub":"$subject","role":"admin"}""".toByteArray(),
        )
        val signingInput = "$header.$payload"
        val signature = encoder.encodeToString(hmacSha256(signingInput, secret))
        return "$signingInput.$signature"
    }

    fun verify(token: String?, secret: String): Boolean {
        if (token.isNullOrBlank()) return false
        val parts = token.trim().removePrefix("Bearer ").trim().split('.')
        if (parts.size != 3) return false
        val (header, payload, signature) = parts
        val expected = encoder.encodeToString(hmacSha256("$header.$payload", secret))
        if (!constantTimeEquals(expected, signature)) return false
        val payloadJson = runCatching {
            decoder.decode(payload).toString(Charsets.UTF_8)
        }.getOrNull() ?: return false
        return payloadJson.contains("\"role\":\"admin\"")
    }

    private fun hmacSha256(data: String, secret: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
