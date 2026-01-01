pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        // Mapbox Maven repository - requires authentication
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                // Get token from gradle.properties - use simple approach
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orElse("").get()
            }
        }
    }
}

rootProject.name = "Clinometer"
include(":app")
