plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
}

subprojects {
    group = "study"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
