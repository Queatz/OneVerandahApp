This is a Kotlin Multiplatform project targeting Web, Server.

* [/app/sharedLogic](./app/sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./app/sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/app/web](./app/web/src) contains a Compose HTML web application using `routing-compose` for navigation. It depends on the [sharedLogic](./app/sharedLogic) module.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application. It also hosts the
  **Dynamic Image Generation & Display System** (AI cover photos for categories/services with
  ArangoDB/OSS/Qwen and graceful local fallbacks). See [IMAGES.md](./IMAGES.md) for setup of the
  server, Qwen API key, OSS bucket, and development/production (SSL) environments.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Server: `./gradlew :server:run`
- Web app (Compose HTML):
    Use Gradle:
    ```shell
    ./gradlew :app:web:jsBrowserDevelopmentRun
    ```
    (Uses webpack dev server with history fallback for client routing. Open http://localhost:8080)

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Server tests: `./gradlew :server:test`
- Web tests: `./gradlew :app:sharedLogic:jsTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
