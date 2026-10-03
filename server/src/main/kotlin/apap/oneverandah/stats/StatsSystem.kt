package apap.oneverandah.stats

import apap.oneverandah.config.AppConfig
import apap.oneverandah.stats.arango.ArangoVisitRepository
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory

/**
 * Owns visit persistence for the admin stats dashboard.
 */
class StatsSystem(
    val service: StatsService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(StatsSystem::class.java)

        suspend fun create(
            config: AppConfig,
            httpClient: HttpClient? = null,
        ): StatsSystem {
            val repo = ArangoVisitRepository.connect(config.arango, httpClient)
                ?: InMemoryVisitRepository().also {
                    log.info(
                        "Visit repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            return StatsSystem(service = StatsService(repo))
        }
    }
}
