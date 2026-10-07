# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep all fields and methods of MarkerView subclasses so reflection in
# DragAttemptsAdapter (shouldShow, pointType fields) survives R8/ProGuard.
-keepclassmembers class * extends com.github.mikephil.charting.components.MarkerView {
    *;
}

# Gson needs real field names for JSON mapping (weather, elevation, etc.).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class com.revix.app.network.** { *; }
-keep class com.revix.app.tracking.** { *; }
# Custom track share/import JSON must keep stable field names across devices.
-keep class com.revix.app.tracking.CustomTrackExchange { *; }
-keep class com.revix.app.tracking.CustomTrackExchangePoint { *; }
-keep class com.revix.app.GeoPoint { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.revix.app.navigation.GeocodingFeature { *; }
-keep class com.revix.app.navigation.GeocodingProperties { *; }
-keep class com.revix.app.navigation.GeocodingResponse { *; }
-keep class com.revix.app.navigation.CategoryFeature { *; }
-keep class com.revix.app.navigation.FeatureGeometry { *; }
-keep class com.revix.app.navigation.CategoryCoordinates { *; }
-keep class com.revix.app.navigation.CategoryProperties { *; }
-keep class com.revix.app.navigation.CategoryResponse { *; }
-keep class com.revix.app.navigation.DirectionsRoute { *; }
-keep class com.revix.app.navigation.DirectionsGeometry { *; }
-keep class com.revix.app.navigation.DirectionsLeg { *; }
-keep class com.revix.app.navigation.DirectionsStep { *; }
-keep class com.revix.app.navigation.StepManeuver { *; }
-keep class com.revix.app.navigation.BannerInstruction { *; }
-keep class com.revix.app.navigation.BannerComponent { *; }
-keep class com.revix.app.navigation.DirectionsResponse { *; }

# Retrofit interfaces / annotations
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson TypeToken / reflective models
# IMPORTANT: do NOT allowobfuscation/allowshrinking on TypeToken subclasses.
# That strips generic Signatures and makes Gson return LinkedTreeMap → ClassCastException in release.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Local/persisted Gson models (SharedPreferences + files). Must keep names + fields in release.
-keep class com.revix.app.DragSession { *; }
-keep class com.revix.app.DragAttempt { *; }
-keep class com.revix.app.DragAttemptSamples { *; }
-keep class com.revix.app.Profile { *; }
-keep class com.revix.app.Profile$VehicleType { *; }
-keep class com.revix.app.Race { *; }
-keep class com.revix.app.RoutePoint { *; }
-keep class com.revix.app.LapData { *; }
-keep class com.revix.app.data.GarageFuelEntry { *; }
-keep class com.revix.app.data.GarageMaintenanceEntry { *; }
-keep class com.revix.app.data.GarageDocumentEntry { *; }
-keep class com.revix.app.data.SessionFuelStop { *; }
-keep class com.revix.app.track.TrackLapStreamWriter$LapBinMeta { *; }
-keep class com.revix.app.track.TrackMiniMapShapeResolver$StoredMiniMapShape { *; }
-keep class com.revix.app.track.catalog.** { *; }
# Google Play Billing
-keep class com.android.billingclient.** { *; }
-keep class com.revix.app.billing.PlayBillingProducts { *; }

