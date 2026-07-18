plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "apap.oneverandah"
version = "1.0.0"
application {
    mainClass = "apap.oneverandah.ApplicationKt"
}

kotlin {
    // Pin the produced bytecode to Java 21 so the fat JAR runs on the
    // OpenJDK 21 installed on the production server by deploy/init.sh,
    // regardless of which JDK is used to run the build locally.
    jvmToolchain(21)
}

dependencies {
    api(projects.core)
    implementation(libs.dashscope.sdk)
    implementation(libs.logback)

    // Ktor server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serializationKotlinxJson)

    // Ktor client (Qwen API, Alibaba OSS, ArangoDB HTTP API)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.kotlin.testJunit)
}