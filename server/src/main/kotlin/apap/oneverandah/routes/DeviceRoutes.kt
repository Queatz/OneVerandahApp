package apap.oneverandah.routes

import apap.oneverandah.chat.DeviceService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.deviceRoutes(deviceService: DeviceService) {
    post("/api/device") {
        call.respond(deviceService.issueToken())
    }
}
