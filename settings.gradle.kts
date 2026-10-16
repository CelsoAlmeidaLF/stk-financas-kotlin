pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FinancasCasaKotlin"

// dominio = Kotlin puro (regras + criptografia), testável sem Android SDK.
// app = Android (Compose + Room), depende do dominio.
include(":dominio", ":app")
