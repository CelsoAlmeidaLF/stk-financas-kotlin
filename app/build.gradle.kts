import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "br.com.systekna.financas"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.systekna.financascasa.kt"
        minSdk = 26 // java.util.Base64 e PBKDF2 sobre HMAC-SHA256 nativos
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-piloto"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    // Domínio (regras + criptografia), Kotlin puro e testado sem Android
    implementation(project(":dominio"))

    // Room = camada oficial do Google sobre o SQLite
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Interface com Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    val lifecycle = "2.8.7"
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")

    // Biometria como alternativa ao PIN (BiometricPrompt + chave do Keystore presa à digital/rosto)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Testes no JVM: aplicação (PIN, backup) e repositório cifrado com um DAO em memória.
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
