import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import com.github.triplet.gradle.play.PlayPublisherExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.tripletPlay)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.keeper)
    alias(libs.plugins.compose.compiler)
    id("idea")
}

repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

keeper {
    traceReferences {
        // Silence missing definitions
        arguments.set(listOf("--map-diagnostics:MissingDefinitionsDiagnostic", "error", "none"))
    }
}

idea {
    module {
        isDownloadJavadoc = System.getenv("CI") != "true"
        isDownloadSources = System.getenv("CI") != "true"
    }
}

val homePath: String? = System.getProperty("user.home")
val baseVersionCode = 22300121
val baseVersionName = "1.1.7"

fun gitCommitHash(): String =
    try {
        ProcessBuilder("git", "rev-parse", "HEAD")
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
    } catch (_: Exception) {
        ""
    }

extensions.configure<ApplicationExtension> {
    namespace = "com.ichi2.anki"

    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    buildFeatures {
        buildConfig = true
        aidl = true
        compose = true
        resValues = true
    }

    val testReleaseBuild =
        rootProject.extra.has("testReleaseBuild") && (rootProject.extra.get("testReleaseBuild") as? Boolean) == true
    testBuildType = if (testReleaseBuild) "release" else "debug"

    androidResources {
        val enableLanguages =
            if (project.rootProject.file("local.properties").exists()) {
                project.rootProject.file("local.properties").inputStream().use { stream ->
                    val localProps = Properties()
                    localProps.load(stream)
                    localProps["enable_languages"] != "false"
                }
            } else {
                true
            }
        if (enableLanguages) {
            localeFilters +=
                listOf(
                    "af",
                    "am",
                    "ar",
                    "az",
                    "be",
                    "bg",
                    "bn",
                    "ca",
                    "ckb",
                    "cs",
                    "da",
                    "de",
                    "el",
                    "en",
                    "eo",
                    "es",
                    "es-rAR",
                    "es-rES",
                    "et",
                    "eu",
                    "fa",
                    "fi",
                    "fil",
                    "fr",
                    "fy",
                    "ga",
                    "gl",
                    "got",
                    "gu",
                    "he",
                    "hi",
                    "hr",
                    "hu",
                    "hy",
                    "id",
                    "it",
                    "iw",
                    "ja",
                    "ka",
                    "kk",
                    "km",
                    "kn",
                    "ko",
                    "ku",
                    "ky",
                    "lt",
                    "lv",
                    "mk",
                    "ml",
                    "mn",
                    "mr",
                    "ms",
                    "my",
                    "nl",
                    "nn",
                    "no",
                    "or",
                    "pa",
                    "pl",
                    "pt-rBR",
                    "pt-rPT",
                    "ro",
                    "ru",
                    "sat",
                    "sc",
                    "sk",
                    "sl",
                    "sq",
                    "sr",
                    "sv",
                    "ta",
                    "te",
                    "tl",
                    "th",
                    "ti",
                    "tr",
                    "tt",
                    "ug",
                    "uk",
                    "ur",
                    "uz",
                    "vi",
                    "zh-rCN",
                    "zh-rTW",
                )
        } else {
            localeFilters += listOf("en")
        }
    }

    defaultConfig {
        applicationId = "com.hirameki.flashcards"
        buildConfigField("Boolean", "CI", (System.getenv("CI") == "true").toString())
        buildConfigField("String", "ACRA_URL", "\"\"")
        buildConfigField("String", "BACKEND_VERSION", "\"${libs.versions.ankiBackend.get()}\"")
        buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "false")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash()}\"")
        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString())
        resValue("string", "app_name", "Hirameki")

        versionCode = baseVersionCode
        versionName = baseVersionName
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testApplicationId = "com.ichi2.anki.tests"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.ichi2.testutils.NewCollectionPathTestRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTOREPATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTOREPWD") ?: System.getenv("KSTOREPWD")
                keyAlias = System.getenv("KEYALIAS")
                keyPassword = System.getenv("KEYPWD")
            } else {
                storeFile = file("$rootDir/tools/fallback-release-keystore.jks")
                storePassword = "Test@123"
                keyAlias = "my-key"
                keyPassword = "Test@123"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-debug"
            isDebuggable = true
            applicationIdSuffix = ".debug"

            if (project.rootProject.file("local.properties").exists()) {
                val localProperties = Properties()
                project.rootProject.file("local.properties").inputStream().use { stream ->
                    localProperties.load(stream)
                }
                enableUnitTestCoverage = localProperties["enable_coverage"] != "false"
                enableAndroidTestCoverage = localProperties["enable_coverage"] != "false"
                if (localProperties["enable_leak_canary"] != null) {
                    buildConfigField(
                        "Boolean",
                        "ENABLE_LEAK_CANARY",
                        localProperties["enable_leak_canary"].toString(),
                    )
                } else {
                    buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "true")
                }
            } else {
                enableUnitTestCoverage = true
                enableAndroidTestCoverage = true
            }

            resValue("color", "anki_foreground_icon_color_0", "#FFFF0000")
            resValue("color", "anki_foreground_icon_color_1", "#FFFF0000")
            resValue(
                "string",
                "applicationId",
                "${defaultConfig.applicationId}$applicationIdSuffix",
            )
        }

        getByName("release") {
            isMinifyEnabled = System.getenv("MINIFY_ENABLED")?.let { it != "false" } ?: true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            testProguardFile("proguard-test-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            if (project.hasProperty("customSuffix")) {
                val customSuffix = project.property("customSuffix") as String
                applicationIdSuffix =
                    if (customSuffix.startsWith(".")) customSuffix else ".$customSuffix"
                resValue(
                    "string",
                    "applicationId",
                    "${defaultConfig.applicationId}$applicationIdSuffix",
                )
            } else {
                resValue("string", "applicationId", defaultConfig.applicationId ?: "")
            }
            if (project.hasProperty("customName")) {
                resValue("string", "app_name", project.property("customName") as String)
            }

            resValue("color", "anki_foreground_icon_color_0", "#FF29B6F6")
            resValue("color", "anki_foreground_icon_color_1", "#FF0288D1")
            enableUnitTestCoverage = testReleaseBuild
            enableAndroidTestCoverage = testReleaseBuild
        }
    }

    flavorDimensions += "appStore"
    productFlavors {
        create("play") {
            isDefault = true
            dimension = "appStore"
        }

        create("full") {
            dimension = "appStore"
        }
    }

    val enableSeparateBuildPerCPUArchitecture = true

    splits {
        abi {
            isEnable = enableSeparateBuildPerCPUArchitecture
            reset()
            include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
            isUniversalApk =
                rootProject.extra.has("universalApkEnabled") &&
                (rootProject.extra.get("universalApkEnabled") as? Boolean) == true
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        checkTestSources = true
        explainIssues = false
        lintConfig = file("../lint-release.xml")
        showAll = true
        warningsAsErrors = true

        if (System.getenv("CI") == "true") {
            // 14853: we want this to appear in the IDE, but it adds noise to CI
            disable += "WrongThread"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants(selector().all()) { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                val abiFilter =
                    output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }
                if (abiFilter != null) {
                    val abi = abiFilter.identifier
                    val versionCodes =
                        mapOf("armeabi-v7a" to 1, "x86" to 2, "arm64-v8a" to 3, "x86_64" to 4)
                    val abiVersionCode = versionCodes[abi]
                    if (abiVersionCode != null) {
                        output.versionCode.set(abiVersionCode * 100000000 + baseVersionCode)
                    }
                }
            }
        }
    }
}

configure<PlayPublisherExtension> {
    serviceAccountCredentials.set(file("${homePath ?: ""}/src/AnkiDroid-GCP-Publish-Credentials.json"))
    track.set("alpha")
    releaseName.set(baseVersionName)
}

val installGitHook =
    tasks.register<Copy>("installGitHook") {
        from(File(rootProject.rootDir, "pre-commit"))
        into(File(rootProject.rootDir, ".git/hooks"))
        filePermissions {
            user {
                read = true
                write = true
                execute = true
            }
        }
    }

tasks.named("preBuild").configure {
    dependsOn(installGitHook)
}

val copyTestLibIntoAndroidTest =
    tasks.register<Copy>("copyTestLibIntoAndroidTest") {
        into(File(rootProject.rootDir, "AnkiDroid/src/androidTest/java/com/ichi2/testutils"))
        from(File(rootProject.rootDir, "testlib/src/main/java/com/ichi2/testutils"))
    }

tasks.named("preBuild").configure {
    dependsOn(copyTestLibIntoAndroidTest)
}

tasks.register("assertNonzeroAndroidTests") {
    val folder = file("./build/outputs/androidTest-results/connected/flavors/play")
    doLast {
        val listOfFiles = folder.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()
        if (listOfFiles.isEmpty()) {
            throw GradleException("No androidTest result files found in $folder")
        }
        for (file in listOfFiles) {
            val lines = file.readLines()
            val matches = lines.filter { it.contains("<testsuite") }
            if (matches.size != 1) {
                throw GradleException("Unable to determine count of tests executed for ${file.name}. Regex pattern out of date?")
            }
            if (!Regex(""".* tests="\d+" .*""").containsMatchIn(matches[0]) ||
                matches[0].contains(
                    """tests="0"""",
                )
            ) {
                throw GradleException(
                    "androidTest executed 0 tests for ${file.name} - Probably a bug with the emulator. Try another image.",
                )
            }
        }
    }
}

apply(from = "./robolectricDownloader.gradle")
apply(from = "./jacoco.gradle")

dependencies {
    configurations.configureEach {
        resolutionStrategy {
            force(libs.jetbrains.annotations)
        }
    }
    api(project(":api"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.material3.windowsize)
    implementation(libs.androidx.navigation3.ui)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.preview)
    debugImplementation(libs.androidx.glance.appwidget.preview)
    lintChecks(project(":lint-rules"))
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    // modules
    implementation(project(":common"))
    implementation(project(":libanki"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.draganddrop)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.google.material)
    implementation(libs.android.image.cropper)
    implementation(libs.nanohttpd)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.seismic)

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.fragment.testing.manifest)

    // Backend libraries
    implementation(libs.protobuf.kotlin.lite)

    val localProperties = Properties()
    if (project.rootProject.file("local.properties").exists()) {
        project.rootProject.file("local.properties").inputStream().use { stream ->
            localProperties.load(stream)
        }
    }
    if (localProperties["local_backend"] == "true") {
        implementation(files("../../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar"))
        testImplementation(files("../../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar"))
    } else {
        implementation(libs.ankiBackend.backend)
        testImplementation(libs.ankiBackend.testing)
    }

    implementation(libs.acra.limiter)
    implementation(libs.acra.toast)
    implementation(libs.acra.dialog)
    implementation(libs.acra.http)
    implementation(libs.acra.mail)

    implementation(libs.commons.compress)
    implementation(libs.commons.collections4)
    implementation(libs.commons.io)
    implementation(libs.mikehardy.google.analytics.java7)
    implementation(libs.okhttp)
    implementation(libs.slf4j.timber)
    implementation(libs.jakewharton.timber)
    implementation(libs.jsoup)
    implementation(libs.java.semver)
    implementation(libs.drakeet.drawer)
    implementation(libs.tapTargetPrompt)
    implementation(libs.colorpicker)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.test)
    implementation(libs.search.preference)

    implementation(libs.leakcanary.android)

    testImplementation(project(":testlib"))
    testImplementation(project(":libanki:testutils"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin) {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation(libs.hamcrest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.fragment.testing)
    testImplementation(libs.json)
    testImplementation(libs.ivanshafran.shared.preferences.mock)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.cashapp.turbine)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.fragment.testing)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
}
