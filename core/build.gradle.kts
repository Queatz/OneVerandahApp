plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
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
