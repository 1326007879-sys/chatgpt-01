plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "io.github.chatgpt01.plugins.fault"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.chatgpt01.plugins.fault"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-test"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDir(rootProject.file("plugin-protocol/src/main/kotlin"))
    sourceSets["main"].java.srcDir(rootProject.file("plugin-runtime/src/main/kotlin"))
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation("junit:junit:4.13.2")
}
