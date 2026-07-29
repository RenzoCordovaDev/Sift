// Root build file — declares all plugins so submodules can apply them without re-specifying versions.
// Plugin versions are centralised in gradle/libs.versions.toml.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.android.junit5)      apply false
    alias(libs.plugins.detekt)
}

// Configure detekt for the entire project from the root so it analyses all source sets.
detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(
        "$rootDir/app/src/main/java"
    )
}
