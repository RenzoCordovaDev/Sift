pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sift"
include(":app")
// E2E test module: pure JVM, runs Cucumber-JVM + Appium on the host machine
// against an Android emulator. Excluded from CI (no emulator available there);
// execute locally with: ./gradlew :e2e:e2eTest -Dappium.url=http://127.0.0.1:4723
include(":e2e")
