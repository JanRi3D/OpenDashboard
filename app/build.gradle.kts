import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "me.ri3d.dashboard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "me.ri3d.dashboard"
        // The head unit (ADAYO AC822X) is API 17; the HeadUnit_API16 test AVD is API 16.
        minSdk = 16
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // Release signing: keystore.properties (ignored by git) names the keystore and its passwords.
    // Without it the release build stays unsigned. CI writes the file from repository secrets.
    val keystoreProperties = rootProject.file("keystore.properties")
    signingConfigs {
        getByName("debug") {
            // Android 4.x only verifies v1 (JAR) signatures.
            enableV1Signing = true
            enableV2Signing = true
        }
        if (keystoreProperties.exists()) {
            val props = Properties().apply { keystoreProperties.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 drops the unused Kotlin standard library: a smaller APK for the head unit.
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf(
            // versions are pinned on purpose
            "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi",
            // @RequiresApi is AndroidX (API 21+); the platform @TargetApi is used instead
            "UseRequiresApi",
            // views are built in code, never inflated
            "ViewConstructor", "SetTextI18n",
            // landscape head-unit app
            "DiscouragedApi", "LockedOrientationActivity",
            // the APK is side-loaded, not distributed as a bundle
            "AppBundleLocaleChanges",
            // "N of 4/5 slots" is never singular
            "PluralsCandidate",
        )
    }
}

dependencies {
    testImplementation(libs.junit)
}
