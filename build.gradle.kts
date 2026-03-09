import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.TestedExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.slack.keeper.optInToKeeper
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.kotlin.dsl.KotlinClosure2
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds


// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint.gradle.plugin) apply false
    alias(libs.plugins.keeper) apply false
}

val localProperties = Properties()
if (project.rootProject.file("local.properties").exists()) {
    localProperties.load(project.rootProject.file("local.properties").inputStream())
}
val fatalWarnings = localProperties["fatal_warnings"] != "false"

// can't be obtained inside 'subprojects'
val ktlintVersion: String? = libs.versions.ktlint.get()

// Here we extract per-module "best practices" settings to a single top-level evaluation
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<KtlintExtension> {
        version.set(ktlintVersion)
    }

    afterEvaluate {
        plugins.withId("com.android.application") {
            configureAndroidModule(extensions.getByType<ApplicationExtension>())
        }
        plugins.withId("com.android.library") {
            configureAndroidModule(extensions.getByType<LibraryExtension>())
        }

        tasks.withType<Test>().configureEach {
            // tell backend to avoid rollover time, and disable interval fuzzing
            environment("ANKI_TEST_MODE", "1")

            maxHeapSize = "2g"
            minHeapSize = "1g"

            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                showStackTraces = true
                exceptionFormat = TestExceptionFormat.FULL
            }

            // CI: Log the test results
            afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                if (desc.parent == null) {
                    logTestResultsToGitHubActions(desc, result)
                }
            }))

            maxParallelForks = gradleTestMaxParallelForks
            forkEvery = 40
            systemProperties["junit.jupiter.execution.parallel.enabled"] = true
            systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
        }

        /**
        Kotlin allows concrete function implementations inside interfaces.
        For those to work when Kotlin compilation targets the JVM backend, you have to enable the interoperability via
        'freeCompilerArgs' in your Gradle file, and you have to choose one of the appropriate '-Xjvm-default' modes.

        https://kotlinlang.org/docs/java-to-kotlin-interop.html#default-methods-in-interfaces

        and we used "all" because we don't have downstream consumers
        https://docs.gradle.org/current/userguide/task_configuration_avoidance.html

        Related to ExperimentalCoroutinesApi: this opt-in is added to enable usage of experimental
        coroutines API, this targets all project modules except the "api" module,
        which doesn't use coroutines so the annotation isn't not available. This would normally
        result in a warning, but we treat warnings as errors.
        (see https://youtrack.jetbrains.com/issue/KT-28777/Using-experimental-coroutines-api-causes-unresolved-dependency)
         */
        tasks.withType(KotlinCompile::class.java).configureEach {
            compilerOptions {
                allWarningsAsErrors.set(fatalWarnings)
                val compilerArgs = mutableListOf(
                    "-Xjvm-default=all",
                    // https://youtrack.jetbrains.com/issue/KT-73255
                    // Apply @StringRes to both constructor params and generated properties
                    "-Xannotation-default-target=param-property"
                )
                if (project.name != "api") {
                    compilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
                }
                freeCompilerArgs.addAll(compilerArgs)
            }
        }
    }
}

fun Project.configureAndroidModule(androidExtension: CommonExtension) {
    if (androidExtension is TestedExtension) {
        androidExtension.testOptions.unitTests.isIncludeAndroidResources = true
    }
    
    val androidComponentsExtension = extensions.findByName("androidComponents") as? AndroidComponentsExtension<*, *, *>
    androidComponentsExtension?.beforeVariants { builder ->
        if (testReleaseBuild && builder.name == "playRelease") {
            builder.optInToKeeper()
        }
    }
}

val jvmVersion = Jvm.current().javaVersion?.majorVersion
val compileSdkVersion: String? = libs.versions.compileSdk.get()
if (jvmVersion != "17" && jvmVersion != "21" && jvmVersion != "24") {
    println("\n\n\n")
    println("**************************************************************************************************************")
    println("\n\n\n")
    println("ERROR: AnkiDroid builds with JVM version 17, 21 and 24.")
    println("  Incompatible major version detected: '$jvmVersion'")
    if (jvmVersion.parseIntOrDefault(defaultValue = 0) > 24) {
        println("\n\n\n")
        println("  If you receive this error because you want to use a newer JDK, we may accept PRs to support new versions.")
        println("  Edit the main build.gradle file, find this message in the file, and add support for the new version.")
        println("  Please make sure the `jacocoTestReport` target works on an emulator with our minSdk (currently $compileSdkVersion).")
    }
    println("\n\n\n")
    println("**************************************************************************************************************")
    println("\n\n\n")
    exitProcess(1)
}

val ciBuild by extra(System.getenv("CI") == "true") // works for Travis CI or GitHub Actions
// allows for -Dpre-dex=false to be set
val preDexEnabled by extra("true" == System.getProperty("pre-dex", "true"))
// allows for universal APKs to be generated
val universalApkEnabled by extra("true" == System.getProperty("universal-apk", "false"))

val testReleaseBuild by extra(System.getenv("TEST_RELEASE_BUILD") == "true")
var androidTestVariantName by extra(
    if (testReleaseBuild) "Release" else "Debug"
)

val gradleTestMaxParallelForks by extra(
    if (System.getProperty("os.name") == "Mac OS X") {
        // macOS reports hardware cores. This is accurate for CI, Intel (halved due to SMT) and Apple Silicon
        providers.exec {
            commandLine("sysctl", "-n", "hw.physicalcpu")
        }.standardOutput.asText.get().trim().toInt()
    } else if (ciBuild) {
        // GitHub Actions run on Standard_D4ads_v5 Azure Compute Units with 4 vCPUs
        // They appear to be 2:1 vCPU to CPU on Linux/Windows with two vCPU cores but with performance 1:1-similar
        // Sources to determine the correct Azure Compute Unit (and get CPU count) to tune this:
        // Which Azure compute unit in use? https://github.com/github/docs/blob/a25a33bb6cbf86a629d0a0c7bef624743991f97e/content/actions/using-github-hosted-runners/about-github-hosted-runners/about-github-hosted-runners.md?plain=1#L176
        // What is that compute unit? https://learn.microsoft.com/en-us/azure/virtual-machines/dasv5-dadsv5-series#dadsv5-series
        // How does it perform? https://learn.microsoft.com/en-gb/azure/virtual-machines/linux/compute-benchmark-scores#dadsv5 (vs previous Standard_DS2_v2 https://learn.microsoft.com/en-gb/azure/virtual-machines/linux/compute-benchmark-scores#dv2---general-compute)
        4
    } else {
        // Use 50% of cores to account for SMT which doesn't help this workload
        max(1, Runtime.getRuntime().availableProcessors() / 2)
    }
)

private fun String?.parseIntOrDefault(defaultValue: Int): Int = this?.toIntOrNull() ?: defaultValue

private fun logTestResultsToGitHubActions(desc: TestDescriptor, result: TestResult) {
    if (!ciBuild) return

    val elapsed = (result.endTime - result.startTime).milliseconds

    val tests = result.testCount
    val passed = result.successfulTestCount
    val failed = result.failedTestCount
    val skipped = result.skippedTestCount

    // Gradle Test Run :AnkiDroid:testPlayDebugUnitTest returned SUCCESS in 5m 30s
    val markdownSummary = """
                        |${desc.displayName} returned **${result.resultType}** in $elapsed
                        || Tests | Passed | Failed | Skipped |
                        ||-------|--------|--------|---------|
                        || $tests| $passed| $failed| $skipped|
                        |----
                    """.trimMargin()

    appendToGitHubActionsSummary(markdownSummary)
}

private fun appendToGitHubActionsSummary(message: String) {
    if (!ciBuild) return
    val summaryPath = System.getenv("GITHUB_STEP_SUMMARY") ?: return
    Files.writeString(
        Paths.get(summaryPath),
        message,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
    )
}
