plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val previewInstall = providers.gradleProperty("petPreviewInstall").orNull == "true"

android {
    namespace = "io.github.chatgpt01.petai"
    compileSdk = 36
    defaultConfig {
        applicationId = if (previewInstall) "io.github.chatgpt01.petai.preview3" else "io.github.chatgpt01.petai"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.0-preview.3"
        manifestPlaceholders["petAppLabel"] = if (previewInstall) "AI 桌宠·预览3" else "@string/app_name"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } } // Both app-selectable languages must remain installed.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/petAssets"))
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/petRes"))
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val exportPetAssets by tasks.registering(Exec::class) {
    val repository = rootProject.projectDir.parentFile
    val python = providers.gradleProperty("petPython").orElse("python")
    val animationBatch = providers.gradleProperty("petAnimationBatch").orElse("0")
    require(animationBatch.get().toInt() in 0..3) { "petAnimationBatch must be 0, 1, 2 or 3" }
    inputs.property("animationBatch", animationBatch)
    inputs.dir(repository.resolve("assets/characters/shenshen/videos"))
    inputs.dir(repository.resolve("assets/sounds/duck"))
    inputs.file(repository.resolve("scripts/export_android_assets.py"))
    inputs.dir(repository.resolve("tools/asset_pipeline"))
    inputs.file(rootProject.file("NOTICE.txt"))
    inputs.file(rootProject.file("LICENSE-APACHE-2.0.txt"))
    inputs.file(repository.resolve("THIRD_PARTY_NOTICES.md"))
    outputs.dir(layout.buildDirectory.dir("generated/petAssets"))
    outputs.dir(layout.buildDirectory.dir("generated/petRes"))
    workingDir(repository)
    commandLine(python.get(), "scripts/export_android_assets.py", "--output",
        layout.buildDirectory.dir("generated/petAssets").get().asFile.absolutePath,
        "--res-output", layout.buildDirectory.dir("generated/petRes").get().asFile.absolutePath,
        "--max-batch", animationBatch.get())
}
tasks.named("preBuild").configure { dependsOn(exportPetAssets) }
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Catalog contract tests must rerun when a different animation batch is built.
    inputs.file(layout.buildDirectory.file("generated/petAssets/pet/manifest.json"))
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
