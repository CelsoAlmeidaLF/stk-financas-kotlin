import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    // JSON dos registros cifrados (o mesmo formato do JSON.stringify do PWA).
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
    // O teste de interoperabilidade grava aqui o que o Kotlin cifrou, para o PWA conferir (scripts/conferir-interop.mjs).
    systemProperty("interop.saida", layout.buildDirectory.file("interop-kotlin.json").get().asFile.absolutePath)
    testLogging { events("passed", "failed"); showStandardStreams = false }
}
