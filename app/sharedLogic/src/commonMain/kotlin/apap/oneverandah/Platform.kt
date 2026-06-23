package apap.oneverandah

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform