plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("io.ktor.plugin") version "3.0.3"
}
group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://nexus.inmo.dev/repository/maven-releases/")
}

dependencies {
    implementation("dev.inmo:tgbotapi:24.0.1-rc") 
    implementation ("me.y9san9.ksm:telegram:0.0.1-dev009")
    implementation ("me.y9san9.ksm:kotlinx-serialization-json:0.0.1-dev009")
}

application {
    mainClass = "org.example.MainKt"
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}