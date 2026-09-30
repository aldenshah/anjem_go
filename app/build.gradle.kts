plugins {
    alias(libs.plugins.android.application)
    id("androidx.navigation.safeargs.kotlin") // Plugin Navigation SafeArgs
    id("com.google.gms.google-services") // Plugin Firebase
}

android {
    namespace = "com.example.anjemgogogo"
    compileSdk = 35 // Disesuaikan ke SDK stabil

    defaultConfig {
        applicationId = "com.example.anjemgogogo"
        minSdk = 24 // Diturunkan ke 24 agar kompatibel dengan 90%+ HP Android
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Aktifkan ViewBinding agar mudah memanggil elemen UI XML di Kotlin
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX & Material UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.0")

    // Jetpack Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Firebase (BOM / Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // OpenStreetMap (OSMDroid - Free Real Street Map Engine)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Google Maps SDK & Location Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}