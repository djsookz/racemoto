plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.clinometer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.clinometer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
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
    
    // Mapbox Maps SDK
    implementation("com.mapbox.maps:android:11.17.2")
    implementation("com.mapbox.extension:maps-compose:11.17.2")
    
    // Mapbox Navigation SDK - Core and UI components
    implementation("com.mapbox.navigationcore:android:3.17.5") {
        exclude(group = "com.caverock", module = "androidsvg")
        exclude(group = "com.caverock", module = "androidsvg-aar")
    }
    implementation("com.mapbox.navigationcore:ui-components:3.17.5") {
        exclude(group = "com.caverock", module = "androidsvg")
        exclude(group = "com.caverock", module = "androidsvg-aar")
    }

    // JSON и мрежа
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

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
}
