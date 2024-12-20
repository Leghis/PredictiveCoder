plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.predictivecoder.ayinamaerik"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3") }

intellij {
    version.set("2023.2.5")
    type.set("IC")
    plugins.set(listOf("java"))
    updateSinceUntilBuild.set(false)
    sandboxDir.set("${projectDir}/idea-sandbox")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
        version.set(project.version.toString())
    }

    buildSearchableOptions {
        enabled = false
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    runIde {
        autoReloadPlugins.set(true)
        systemProperty("idea.log.debug.categories", "#com.predictivecoder.ayinamaerik")
        jvmArgs("-Xmx2g")
        jvmArgs("-XX:+UseG1GC")
        jvmArgs("-XX:SoftRefLRUPolicyMSPerMB=50")
        jvmArgs("-ea")
        jvmArgs("-Djdk.module.illegalAccess.silent=true")
    }

    test {
        useJUnitPlatform()
    }

    clean {
        delete("${projectDir}/idea-sandbox")
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/kotlin")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs("src/test/java", "src/test/kotlin")
        resources.srcDirs("src/test/resources")
    }
}