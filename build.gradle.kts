plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
}

group = "me.vals.worldborder"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("org.imanity.imanityspigot:server:2023.10.1")
}

tasks.shadowJar {
    archiveBaseName.set("WorldBorder")
    archiveClassifier.set("")
    archiveVersion.set("")
    exclude("META-INF/**")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifact(tasks.shadowJar)
            groupId = project.group.toString()
            artifactId = "WorldBorder"
            version = project.version.toString()
        }
    }
    repositories {
        mavenLocal()
    }
}