import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    application
    kotlin("plugin.serialization") version "1.4.10"
}
group = "me.marcinaman"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
}
dependencies {
    implementation("org.jetbrains.dokka:dokka-analysis:1.4.20-SNAPSHOT")
    implementation("org.jetbrains.dokka:dokka-core:1.4.20-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0-RC2")
    testImplementation(kotlin("test-junit"))
}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
application {
    mainClassName = "MainKt"
}