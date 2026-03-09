import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.ichi2.anki"
version = "2.0.0"


extensions.configure<LibraryExtension> {
    namespace = "com.ichi2.anki.api"
    compileSdk = libs.versions.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField(
            "String",
            "READ_WRITE_PERMISSION",
            "\"com.ichi2.anki.permission.READ_WRITE_DATABASE\""
        )
        buildConfigField("String", "AUTHORITY", "\"com.ichi2.anki.flashcards\"")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "READ_WRITE_PERMISSION",
                "\"com.ichi2.anki.debug.permission.READ_WRITE_DATABASE\""
            )
            buildConfigField("String", "AUTHORITY", "\"com.ichi2.anki.debug.flashcards\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        if (!name.contains("test", ignoreCase = true)) {
            freeCompilerArgs.add("-Xexplicit-api=strict")
        }
    }
}

apply(from = "../lint.gradle")

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)

    lintChecks(project(":lint-rules"))
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                version = version
                groupId = group.toString()
                artifactId = "api"

                pom {
                    name = "AnkiDroid API"
                    description = "A programmatic API exported by AnkiDroid"
                    url = "https://github.com/ankidroid/Anki-Android/tree/main/api"
                    licenses {
                        license {
                            name = "GNU LESSER GENERAL PUBLIC LICENSE, v3"
                            url =
                                "https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER"
                        }
                    }
                    scm {
                        connection = "scm:git:git://github.com/ankidroid/Anki-Android.git"
                        url = "https://github.com/ankidroid/Anki-Android"
                    }
                }

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
        repositories {
            maven {
                val releasesRepoUrl = layout.buildDirectory.dir("repos/releases")
                val snapshotsRepoUrl = layout.buildDirectory.dir("repos/snapshots")
                url = uri(
                    if (version.toString()
                            .endsWith("SNAPSHOT")
                    ) snapshotsRepoUrl else releasesRepoUrl
                )
            }
        }
    }
}

val zipReleaseProvider = tasks.register<Zip>("zipRelease") {
    from(layout.buildDirectory.dir("repos/releases"))
    destinationDirectory.set(layout.buildDirectory)
    archiveFileName.set("release-$version.zip")
}

val generateRelease: TaskProvider<Task> = tasks.register("generateRelease") {
    doLast {
        println("Release $version can be found at ${layout.buildDirectory.get()}/repos/releases/")
        println("Release $version zipped can be found ${layout.buildDirectory.get()}/release-$version.zip")
    }
}

generateRelease.configure {
    dependsOn(tasks.named("publish"))
    dependsOn(zipReleaseProvider)
}
