import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing. Kept in a gitignored file that points at a keystore stored
// outside the repo, so neither the key nor the passwords are ever committed.
// If the file is absent the release build still succeeds, signed with the debug
// key — fine for a local smoke test, not shippable.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")
    ?.let { file(it).exists() } == true

android {
    namespace = "com.focuslock.mdm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.focuslock.mdm"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true   // sideloading onto older images
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // ── Tiers ────────────────────────────────────────────────────
    //
    // The only difference between them is whether the app is allowed to hold
    // Device-Owner powers. Every screen, rule, task and setting is identical,
    // which is the point: the demo has to be the real thing or it proves
    // nothing about whether the app suits you.
    flavorDimensions += "tier"

    productFlavors {
        create("full") {
            dimension = "tier"
            // The shipping app. Enforcement on.
            buildConfigField("boolean", "ENFORCEMENT", "true")
            buildConfigField("String", "TIER", "\"full\"")
        }
        create("demo") {
            dimension = "tier"
            // Enforcement off, and a separate applicationId so the demo can sit
            // beside a paid copy instead of colliding with it. The demo manifest
            // also drops AdminReceiver entirely, so it cannot be provisioned as
            // device owner even by someone who wants it to be.
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "ENFORCEMENT", "false")
            buildConfigField("String", "TIER", "\"demo\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // FocusLock-full-v1.0.apk / FocusLock-demo-v1.0.apk, rather than
    // app-full-release.apk, so the file that reaches a buyer says what it is.
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.buildType.name == "release") {
                // defaultConfig, not variant.versionName: the demo flavour adds a
                // "-demo" suffix to the version, and the flavour name is already
                // in the filename. Without this the demo builds as
                // FocusLock-demo-v1.0-demo.apk.
                val v = android.defaultConfig.versionName
                output.outputFileName = "FocusLock-${variant.flavorName}-v$v.apk"
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
}

dependencies {
    // Optional: turns on content matching for Earn Mode photo proof.
    //
    // PhotoProof works without it — it catches reused photos, blank frames and
    // stale files with no model at all, which are the cheats people actually
    // use. Adding this line and implementing PhotoProof.ContentMatcher against
    // ImageLabeling adds a 400-label check of what is in the frame. The model is
    // bundled, runs fully offline, and adds roughly 5-10 MB to the APK.
    //
    // implementation("com.google.mlkit:image-labeling:17.0.9")

    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
