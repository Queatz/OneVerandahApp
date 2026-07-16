plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Pin the JVM target to Java 21 so the server's fat JAR (which depends on
    // this module) stays compatible with the OpenJDK 21 installed on the
    // production server, regardless of which JDK runs the local build.
    jvmToolchain(21)

    jvm()

    js {
        browser()
    }


    sourceSets {
        commonMain.dependencies {
            // Shared, multiplatform-friendly JSON (de)serialization used by both
            // the Ktor server and the Compose HTML web client for the image API.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
