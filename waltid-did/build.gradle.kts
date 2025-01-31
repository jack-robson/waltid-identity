import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import love.forte.plugin.suspendtrans.ClassInfo
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransformGradleExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform") version "0.6.0"
}

group = "id.walt.did"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

suspendTransform {
    enabled = true
    includeRuntime = true
    /*jvm {

    }
    js {

    }*/
    useJsDefault()
}


kotlin {
    jvmToolchain(15)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "15" // JVM got Ed25519 at version 15
        }
        withJava()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
    js(IR) {
        /*browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }*/
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("io.github.optimumcode:json-schema-validator:0.0.8")

                // Ktor client
                implementation("io.ktor:ktor-client-core:2.3.8")
                implementation("io.ktor:ktor-client-serialization:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
                implementation("io.ktor:ktor-client-json:2.3.8")
                implementation("io.ktor:ktor-client-logging:2.3.8")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                // Date
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                // Crypto
                api(project(":waltid-crypto"))

                // Encodings
                implementation("net.thauvin.erik.urlencoder:urlencoder-lib:1.4.0")

                // Logging
                implementation("io.github.oshai:kotlin-logging:6.0.3")

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Ktor client
                implementation("io.ktor:ktor-client-cio:2.3.8")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.12")

                // Json canonicalization
                implementation("io.github.erdtman:java-json-canonicalization:1.1")

                // Multiformat
//                implementation("com.github.multiformats:java-multibase:v1.1.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
                implementation("io.ktor:ktor-server-test-host:2.3.8")
                implementation("io.ktor:ktor-server-content-negotiation:2.3.8")
                implementation("io.ktor:ktor-server-netty:2.3.8")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("canonicalize", "2.0.0"))
                implementation(npm("uuid", "9.0.1"))
            }
        }
        publishing {
            repositories {
                maven {
                    url = uri("https://maven.walt.id/repository/waltid/")
                    val envUsername = System.getenv("MAVEN_USERNAME")
                    val envPassword = System.getenv("MAVEN_PASSWORD")

                    val usernameFile = File("$rootDir/secret_maven_username.txt")
                    val passwordFile = File("$rootDir/secret_maven_password.txt")

                    val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
                    //println("Deploy username length: ${secretMavenUsername.length}")
                    val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }

                    //if (secretMavenPassword.isBlank()) {
                    //   println("WARNING: Password is blank!")
                    //}

                    credentials {
                        username = secretMavenUsername
                        password = secretMavenPassword
                    }
                }
            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

extensions.getByType<SuspendTransformGradleExtension>().apply {
    transformers[TargetPlatform.JS] = mutableListOf(
        SuspendTransformConfiguration.jsPromiseTransformer.copy(
            copyAnnotationExcludes = listOf(
                ClassInfo("kotlin.js", "JsExport.Ignore")
            )
        )
    )
}


tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
    }
}
