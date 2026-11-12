import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "io.github.chatgpt01.voice"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.chatgpt01.voice"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-research"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Align the test runtime with the host's already verified AndroidX/coroutine versions.
    androidTestImplementation("androidx.annotation:annotation:1.9.1")
    androidTestImplementation("androidx.tracing:tracing:1.2.0")
    androidTestImplementation("androidx.lifecycle:lifecycle-common:2.8.7")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
val verifySherpaRuntime by tasks.registering {
    doLast {
        val runtime = file("libs/sherpa-onnx-2.0.1.aar")
        require(runtime.isFile) { "Run: python scripts/prepare_voice_engine.py runtime" }
        val hash = MessageDigest.getInstance("SHA-256")
        runtime.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n=input.read(buffer); if(n<0) break; hash.update(buffer,0,n) }
        }
        require(hash.digest().joinToString("") { "%02x".format(it) } == "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7") { "Sherpa runtime checksum mismatch" }
    }
}
tasks.named("preBuild").configure { dependsOn(verifySherpaRuntime) }
