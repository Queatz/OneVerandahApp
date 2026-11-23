package apap.oneverandah.chat

import apap.oneverandah.api.DeviceTokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class DeviceRecord(
    val tokenHash: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long? = null,
)

interface DeviceRepository {
    suspend fun create(tokenHash: String, createdAt: Long, lastSeenAt: Long): DeviceRecord
    suspend fun findByTokenHash(tokenHash: String): DeviceRecord?
    suspend fun touch(tokenHash: String, lastSeenAt: Long)
}

class InMemoryDeviceRepository : DeviceRepository {
    private val devices = LinkedHashMap<String, DeviceRecord>()
    private val mutex = Mutex()

    override suspend fun create(tokenHash: String, createdAt: Long, lastSeenAt: Long): DeviceRecord =
        mutex.withLock {
            val record = DeviceRecord(
                tokenHash = tokenHash,
                createdAt = createdAt,
                lastSeenAt = lastSeenAt,
            )
            devices[tokenHash] = record
            record
        }

    override suspend fun findByTokenHash(tokenHash: String): DeviceRecord? =
        mutex.withLock { devices[tokenHash] }

    override suspend fun touch(tokenHash: String, lastSeenAt: Long) {
        mutex.withLock {
            val existing = devices[tokenHash] ?: return@withLock
            devices[tokenHash] = existing.copy(lastSeenAt = lastSeenAt)
        }
    }
}

class DeviceService(
    private val repository: DeviceRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tokenGenerator: () -> String = Companion::generateSecureToken,
) {
    suspend fun issueToken(): DeviceTokenResponse {
        val token = tokenGenerator()
        val now = clock()
        repository.create(
            tokenHash = hashToken(token),
            createdAt = now,
            lastSeenAt = now,
        )
        return DeviceTokenResponse(deviceToken = token)
    }

    /**
     * Validates a raw device token without logging it. Updates last-seen on success.
     * Returns null when the token is unknown or revoked.
     */
    suspend fun findValid(rawToken: String?): DeviceRecord? {
        val token = rawToken?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val record = repository.findByTokenHash(hashToken(token)) ?: return null
        if (record.revokedAt != null) return null
        val now = clock()
        try {
            repository.touch(record.tokenHash, now)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
        }
        return record.copy(lastSeenAt = now)
    }

    companion object {
        private val secureRandom = SecureRandom()

        fun generateSecureToken(): String {
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
