plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProps: Map<String, String> = buildMap {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) return@forEach
            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim()
            put(key, value)
        }
    }
}

fun localProp(name: String): String? = localProps[name]?.takeIf { it.isNotBlank() }

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.revix.app"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "app.revix.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 44
        versionName = "1.1.0"

        resValue("string", "app_name", "REVIX")
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        // Free RINEX→UBX aiding blob (published by .github/workflows/racebox-aiding.yml).
        val aidingUrl = localProp("racebox.aiding.url")
            ?: "https://raw.githubusercontent.com/djsookz/racemoto/racebox-aiding/aiding.ubx"
        buildConfigField("String", "RACEBOX_AIDING_URL", "\"${escapeBuildConfigString(aidingUrl)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = localProp("RELEASE_STORE_FILE")
            val storePasswordValue = localProp("RELEASE_STORE_PASSWORD")
            val keyAliasValue = localProp("RELEASE_KEY_ALIAS")
            val keyPasswordValue = localProp("RELEASE_KEY_PASSWORD")
            if (
                storePath != null &&
                storePasswordValue != null &&
                keyAliasValue != null &&
                keyPasswordValue != null
            ) {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

configurations.configureEach {
    exclude(group = "net.sf.kxml", module = "kxml2")
    exclude(group = "com.caverock", module = "androidsvg-aar")
}

dependencies {
    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // AndroidX и UI
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.camera:camera-effects:1.4.2")
    implementation("androidx.media3:media3-transformer:1.6.1")
    implementation("androidx.media3:media3-effect:1.6.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")

    // Google Play Billing (Play Console requires ≥8.0.0 by 2026-08-31; recommend 9.x).
    // Use the Java artifact (not billing-ktx): 9.1.0-ktx needs Kotlin 2.3 metadata; project is 2.0.21.
    implementation("com.android.billingclient:billing:9.1.0")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // ViewPager2 за instant navigation
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Карти и локация
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // OSMDroid и BonusPack
    implementation("org.osmdroid:osmdroid-android:6.1.17")
    implementation("org.osmdroid:osmdroid-wms:6.1.17")
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.17")
    implementation("com.github.MKergall:osmbonuspack:6.9.0") {
        exclude(group = "com.caverock", module = "androidsvg")
        exclude(group = "com.caverock", module = "androidsvg-aar")
    }
    
    // Mapbox Maps / Navigation — NDK 27 artifacts are 16 KB page-size aligned on 64-bit.
    implementation("com.mapbox.maps:android-ndk27:11.18.1")
    implementation("com.mapbox.extension:maps-compose:11.18.1") {
        exclude(group = "com.mapbox.maps", module = "android")
        exclude(group = "com.mapbox.maps", module = "android-core")
    }
    implementation("com.mapbox.navigationcore:android-ndk27:3.18.0") {
        exclude(group = "com.caverock", module = "androidsvg")
        exclude(group = "com.caverock", module = "androidsvg-aar")
        exclude(group = "com.mapbox.maps", module = "android")
        exclude(group = "com.mapbox.maps", module = "android-core")
    }
    implementation("com.mapbox.navigationcore:ui-components-ndk27:3.18.0") {
        exclude(group = "com.caverock", module = "androidsvg")
        exclude(group = "com.caverock", module = "androidsvg-aar")
        exclude(group = "com.mapbox.maps", module = "android")
        exclude(group = "com.mapbox.maps", module = "android-core")
    }

    // JSON и мрежа
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.guava:guava:33.4.8-android")

    // Графики
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Тестване
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    
    // Coil за зареждане на изображения (memory caching)
    implementation("io.coil-kt:coil:2.4.0")
    
    // Firebase BoM (Bill of Materials) - управлява версиите автоматично
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    
    // Firebase Firestore (за realtime database)
    implementation("com.google.firebase:firebase-firestore-ktx")
    
    // Firebase Authentication (за anonymous users)
    implementation("com.google.firebase:firebase-auth-ktx")
    
    // Coroutines support за Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
