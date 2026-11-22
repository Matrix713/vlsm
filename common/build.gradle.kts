plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wspólny kod obu aplikacji: logika IPv4, motyw, klawiatura IP, bezel, komponenty list.
android {
    namespace = "pl.vlsmcalc.wear.common"
    compileSdk = 35
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.material:material-icons-core")
    api("androidx.activity:activity-compose:1.9.3")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val wearCompose = "1.4.0"
    api("androidx.wear.compose:compose-material:$wearCompose")
    api("androidx.wear.compose:compose-foundation:$wearCompose")
    api("androidx.wear.compose:compose-navigation:$wearCompose")
    api("androidx.wear:wear-input:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
