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
                // Prefer local.properties (gitignored); fallback to gradle.properties / env.
                password = run {
                    val localProps = java.util.Properties()
                    val localFile = rootDir.resolve("local.properties")
                    if (localFile.exists()) {
                        localFile.inputStream().use { localProps.load(it) }
                    }
                    localProps.getProperty("MAPBOX_DOWNLOADS_TOKEN")
                        ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orElse("").get()
                }
            }
        }
    }
}

rootProject.name = "REVIX"
include(":app")
