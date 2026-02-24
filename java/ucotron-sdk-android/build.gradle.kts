plugins {
    kotlin("jvm") version "1.9.22"
}

group = "com.ucotron"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Core SDK dependency
    implementation(project(":ucotron-sdk"))

    // Kotlin coroutines (core — no Android-specific dispatcher)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["java"])
            artifactId = "ucotron-sdk-android"

            pom {
                name.set("Ucotron Android SDK")
                description.set("Kotlin/Android SDK for the Ucotron cognitive memory framework")
                url.set("https://github.com/ucotron-ai/ucotron")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("ucotron")
                        name.set("Ucotron Team")
                        email.set("dev@ucotron.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/ucotron-ai/ucotron.git")
                    developerConnection.set("scm:git:ssh://github.com/ucotron-ai/ucotron.git")
                    url.set("https://github.com/ucotron-ai/ucotron")
                }
            }
        }
    }
}
