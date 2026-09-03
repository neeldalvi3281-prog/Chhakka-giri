plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.aegis.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.citchat.chhakka"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", "\"https://hohtqhfvoeudftaalyqm.supabase.co\"")
        buildConfigField("String", "SUPABASE_GATEWAY_URL", "\"https://hohtqhfvoeudftaalyqm.supabase.co/functions/v1/gateway-upload\"")
        // Legacy anon JWT (not the sb_publishable_ key): the gateway-upload
        // edge function runs with JWT verification, which requires a JWT.
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhvaHRxaGZ2b2V1ZGZ0YWFseXFtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgyNjEyODMsImV4cCI6MjEwMzgzNzI4M30.nhWXROanI_mFI1Big6OcRiCPpmtVTeIw7F-VvLL6AvQ\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core Android & Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google Nearby Connections API (P2P Cluster Offline Mesh)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Security & Cryptography
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // WorkManager for background gateway sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Offline Mapping (OSMDroid)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // OkHttp for Internet Bridge
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
