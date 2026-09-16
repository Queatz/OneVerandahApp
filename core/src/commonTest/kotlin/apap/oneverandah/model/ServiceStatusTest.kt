package apap.oneverandah.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceStatusTest {

    private fun createService(
        serviceStatus: ServiceStatus = ServiceStatus.AVAILABLE,
        providerStatus: ServiceStatus = ServiceStatus.AVAILABLE,
    ): Service {
        val provider = ServiceProvider(
            name = "Jacob",
            sex = Sex.MALE,
            blockAddress = "Block B",
            phone = "+84 000",
            photo = "face",
            status = providerStatus,
        )
        return Service(
            id = "test-service",
            categoryId = "test",
            icon = "star",
            title = LocalizedText(en = "Test", vi = "Test"),
            description = LocalizedText(en = "Test", vi = "Test"),
            rating = 5.0,
            reviewCount = 10,
            basePrice = Money.thousands(100),
            bookingTypes = listOf(BookingTypeOption("default", LocalizedText(en = "Default", vi = "Default"))),
            durations = listOf(DurationOption("60", 60, Money.thousands(100))),
            provider = provider,
            status = serviceStatus,
        )
    }

    @Test
    fun defaultStatusIsAvailable() {
        val provider = ServiceProvider(
            name = "Jacob",
            sex = Sex.MALE,
            blockAddress = "Block B",
            phone = "+84 000",
            photo = "face",
        )
        assertEquals(ServiceStatus.AVAILABLE, provider.status)
    }

    @Test
    fun providerAvailableFallsBackOnServiceStatus() {
        assertEquals(
            ServiceStatus.AVAILABLE,
            createService(
                serviceStatus = ServiceStatus.AVAILABLE,
                providerStatus = ServiceStatus.AVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.TEMPORARILY_UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
                providerStatus = ServiceStatus.AVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.UNAVAILABLE,
                providerStatus = ServiceStatus.AVAILABLE,
            ).effectiveStatus,
        )
    }

    @Test
    fun providerTemporarilyUnavailableOverridesUnlessServiceIsUnavailable() {
        assertEquals(
            ServiceStatus.TEMPORARILY_UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.AVAILABLE,
                providerStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.TEMPORARILY_UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
                providerStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.UNAVAILABLE,
                providerStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
            ).effectiveStatus,
        )
    }

    @Test
    fun providerUnavailableAlwaysResultsInUnavailable() {
        assertEquals(
            ServiceStatus.UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.AVAILABLE,
                providerStatus = ServiceStatus.UNAVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.TEMPORARILY_UNAVAILABLE,
                providerStatus = ServiceStatus.UNAVAILABLE,
            ).effectiveStatus,
        )
        assertEquals(
            ServiceStatus.UNAVAILABLE,
            createService(
                serviceStatus = ServiceStatus.UNAVAILABLE,
                providerStatus = ServiceStatus.UNAVAILABLE,
            ).effectiveStatus,
        )
    }
}
