package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.stats.StatsApiError
import apap.oneverandah.stats.StatsSystem
import apap.oneverandah.stats.VisitPingRequest
import apap.oneverandah.stats.VisitPingResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Visitor stats API.
 *
 * Public:
 *  - `POST /api/v1/visits`  (device token required)
 *
 * Admin (Bearer JWT):
 *  - `GET  /api/v1/admin/stats`
 */
fun Route.statsRoutes(system: StatsSystem, config: AppConfig) {
    post("/api/v1/visits") {
        val hash = deviceHash()
        if (hash == null) {
            call.respond(HttpStatusCode.Unauthorized, StatsApiError("Valid device token required"))
            return@post
        }
        val body = runCatching { call.receive<VisitPingRequest>() }.getOrNull() ?: VisitPingRequest()
        system.service.recordVisit(hash, body.path, body.languageTag)
        call.respond(VisitPingResponse())
    }

    get("/api/v1/admin/stats") {
        if (!requireAdmin(config)) return@get
        call.respond(system.service.adminStats())
    }
}

private fun io.ktor.server.routing.RoutingContext.deviceHash(): String? {
    val token = call.request.header(DEVICE_TOKEN_HEADER)?.trim().orEmpty()
    return token.takeIf { it.isNotEmpty() }?.let(DeviceService::hashToken)
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, StatsApiError("Admin authentication required"))
        return false
    }
    return true
}
