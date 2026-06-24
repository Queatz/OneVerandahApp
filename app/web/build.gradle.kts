plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinCompose)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.app.sharedLogic)
            implementation(libs.compose.html.core)
            implementation(libs.compose.html.svg)
            implementation(libs.routing.compose)
            implementation("io.ktor:ktor-client-core:3.5.0")
            // Lets webpack regenerate index.html and auto-inject <script> tags for
            // EVERY emitted chunk (runtime, vendors, main). Without it, Kotlin/JS just
            // copies the static index.html verbatim and new chunks (e.g. from
            // splitChunks) are never referenced -> blank page.
            implementation(devNpm("html-webpack-plugin", "5.6.3"))
        }
    }
}

// The raw "index.template.html" only needs to exist as a webpack template
// (html-webpack-plugin); it should not be shipped in the final distribution,
// where webpack already emits the generated "index.html".
tasks.withType<AbstractCopyTask>().configureEach {
    if (name.endsWith("ExecutableDistribution")) {
        exclude("index.template.html")
    }
}
