plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tihon.shtorka"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tihon.shtorka"
        minSdk = 26
        // 28 — осознанно: так подключение Bluetooth-наушников и их заряд
        // приходят без запроса разрешения «Устройства поблизости», а скрытый
        // getBatteryLevel() у наушников остаётся доступным.
        targetSdk = 28
        versionCode = 4
        versionName = "2.2"
    }

    // Ключ подписи в репозиторий не попадает. Если он лежит рядом —
    // релиз подписывается им, иначе собирается отладочной подписью.
    val releaseKeystore = rootProject.file("keystore/shtorka.jks")
    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.gradleProperty("shtorka.storePassword").getOrElse("shtorka")
                keyAlias = providers.gradleProperty("shtorka.keyAlias").getOrElse("shtorka")
                keyPassword = providers.gradleProperty("shtorka.keyPassword").getOrElse("shtorka")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        // Старый targetSdk выбран намеренно (см. выше), магазин нам не нужен.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("shots.dir", layout.buildDirectory.dir("shots").get().asFile.absolutePath)
                test.maxHeapSize = "2g"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Снимки экранов без телефона: ./gradlew :shtorka:testDebugUnitTest → build/shots/*.png
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
