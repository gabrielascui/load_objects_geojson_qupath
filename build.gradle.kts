plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

// Get version from Environment Variable (GitHub Actions) or fallback to VERSION file
// 1. Get the tag name from GitHub (e.g., "v1.0.3" or "v1.0.3-rc1")
val githubTag = System.getenv("GITHUB_REF_NAME")

// 2. Determine the final version string
val releaseVersion = if (githubTag != null && githubTag.startsWith("v")) {
    githubTag.removePrefix("v") // Use the tag (stripped of 'v')
} else {
    file("VERSION").readText().trim() // Fallback to your SNAPSHOT file
}

// TODO: Configure your extension here (please change the defaults!)
qupathExtension {
    name = "qupath-extension-load-objects-geojson"
    group = "io.github.qupath"
    version = releaseVersion

    description = "A simple QuPath extension"
    automaticModule = "io.github.qupath.extension.load-objects-geojson"
}

repositories {
    mavenCentral()
    maven(url = "https://maven.scijava.org/content/repositories/public/")
    maven(url = "https://maven.scijava.org/content/repositories/releases/")
}

// TODO: Define your dependencies here
dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    shadow("com.fasterxml.jackson.core:jackson-core:2.17.2")
    shadow("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}

