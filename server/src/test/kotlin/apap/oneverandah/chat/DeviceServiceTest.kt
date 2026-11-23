package apap.oneverandah.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceServiceTest {
    @Test
    fun `issueToken returns a long opaque token and stores only its hash`() = runBlocking {
        val repository = InMemoryDeviceRepository()
        val service = DeviceService(
            repository = repository,
            clock = { 1_700_000_000_000L },
            tokenGenerator = { "test-device-token-abcdefghijklmnopqrstuvwxyz012345" },
        )

        val response = service.issueToken()

        assertEquals("test-device-token-abcdefghijklmnopqrstuvwxyz012345", response.deviceToken)
        assertTrue(response.deviceToken.length >= 32)

        val stored = repository.findByTokenHash(DeviceService.hashToken(response.deviceToken))
        assertNotNull(stored)
        assertEquals(1_700_000_000_000L, stored.createdAt)
        assertEquals(1_700_000_000_000L, stored.lastSeenAt)
        assertNull(stored.revokedAt)
    }

    @Test
    fun `repeat issueToken calls create distinct devices`() = runBlocking {
        var counter = 0
        val service = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "token-${++counter}-xxxxxxxxxxxxxxxxxxxxxxxxxxxx" },
        )

        val first = service.issueToken().deviceToken
        val second = service.issueToken().deviceToken

        assertNotEquals(first, second)
        assertNotNull(service.findValid(first))
        assertNotNull(service.findValid(second))
        Unit
    }

    @Test
    fun `findValid accepts known tokens and rejects unknown or blank ones`() = runBlocking {
        var now = 100L
        val service = DeviceService(
            repository = InMemoryDeviceRepository(),
            clock = { now },
            tokenGenerator = { "valid-token-abcdefghijklmnopqrstuvwxyz0123" },
        )
        val token = service.issueToken().deviceToken

        now = 200L
        val valid = service.findValid(token)
        assertNotNull(valid)
        assertEquals(200L, valid.lastSeenAt)

        assertNull(service.findValid("unknown-token"))
        assertNull(service.findValid("   "))
        assertNull(service.findValid(null))
        Unit
    }

    @Test
    fun `findValid returns valid device when touch fails`() = runBlocking {
        val repository = InMemoryDeviceRepository()
        val service = DeviceService(
            repository = repository,
            tokenGenerator = { "valid-token-abcdefghijklmnopqrstuvwxyz0123" },
        )
        val token = service.issueToken().deviceToken
        val failingRepository = object : DeviceRepository by repository {
            override suspend fun touch(tokenHash: String, lastSeenAt: Long) {
                throw ArangoDeviceException("touch failed")
            }
        }

        val valid = DeviceService(repository = failingRepository).findValid(token)

        assertNotNull(valid)
        Unit
    }

    @Test
    fun `findValid rejects revoked devices`() = runBlocking {
        val repository = InMemoryDeviceRepository()
        val service = DeviceService(
            repository = repository,
            tokenGenerator = { "revoked-token-abcdefghijklmnopqrstuvwxyz01" },
        )
        val token = service.issueToken().deviceToken
        val revokedRepo = object : DeviceRepository by repository {
            override suspend fun findByTokenHash(tokenHash: String) =
                repository.findByTokenHash(tokenHash)?.copy(revokedAt = 999L)
        }
        val revokedService = DeviceService(repository = revokedRepo)
        assertNull(revokedService.findValid(token))
    }

    @Test
    fun `hashToken is stable and does not equal the raw token`() {
        val token = "sample-device-token-value"
        val hash = DeviceService.hashToken(token)
        assertEquals(64, hash.length)
        assertNotEquals(token, hash)
        assertEquals(hash, DeviceService.hashToken(token))
    }
}
