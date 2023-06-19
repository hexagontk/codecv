import org.gradle.api.JavaVersion.*
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import java.lang.System.getProperty

plugins {
    kotlin("jvm") version("1.8.22")
    id("org.graalvm.buildtools.native") version("0.9.23")
}

val os = getProperty("os.name").lowercase()

val hexagonVersion = "2.8.6"
val hexagonExtraVersion = "2.8.4"
val vertxVersion = "4.4.3"

val gradleScripts = "https://raw.githubusercontent.com/hexagonkt/hexagon/$hexagonVersion/gradle"

ext.set("modules", "java.logging,java.desktop")
ext.set("options", "-Xmx32m")
ext.set("icon", "$projectDir/logo.png")
ext.set("applicationClass", "co.codecv.CvKt")

apply(from = "$gradleScripts/kotlin.gradle")
apply(from = "$gradleScripts/application.gradle")
apply(from = "$gradleScripts/native.gradle")

group = "com.hexagonkt.tools"
version = "0.9.20"
description = "CVs for programmers"

if (current() !in setOf(VERSION_16, VERSION_17, VERSION_18, VERSION_19, VERSION_20))
    error("This build must be run with JDK 16+. Current: ${current()}")

dependencies {
    "implementation"("com.hexagonkt:http_server_netty:$hexagonVersion")
    "implementation"("com.hexagonkt:logging_slf4j_jul:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_json:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_yaml:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_toml:$hexagonVersion")
    "implementation"("com.hexagonkt:templates_pebble:$hexagonVersion")
    "implementation"("com.hexagonkt.extra:helpers:$hexagonExtraVersion")
    "implementation"("com.hexagonkt.extra:args:$hexagonExtraVersion")
    "implementation"("com.hexagonkt.extra:web:$hexagonExtraVersion")

    "implementation"("io.vertx:vertx-json-schema:$vertxVersion")

    "testImplementation"("com.hexagonkt:http_client_jetty:$hexagonVersion")
}

tasks.named("classes") { dependsOn("addResources") }
tasks.named("build") { dependsOn("installDist") }

tasks.named<CreateStartScripts>("startScripts") {
    dependsOn("jacocoTestReport")

    listOf(unixStartScriptGenerator, windowsStartScriptGenerator).forEach {
        val generator = it as DefaultTemplateBasedStartScriptGenerator
        val currentTemplate = generator.template.asString()
        val newTemplate = when (it) {
            windowsStartScriptGenerator ->
                currentTemplate.replace("set CLASSPATH=\$classpath", "set CLASSPATH=\$classpath;.")
            unixStartScriptGenerator ->
                currentTemplate.replace("CLASSPATH=\$classpath", "CLASSPATH=\$classpath:.")
            else ->
                error("Unexpected script")
        }
        generator.template = resources.text.fromString(newTemplate)
    }
}

tasks.create<Copy>("addResources") {
    from(projectDir)
    include("examples/**")
    include("cv.schema.json")
    into(buildDir.resolve("resources/main"))
}

tasks.create("release") {
    dependsOn("build")

    doLast {
        val release = version.toString()
        val actor = System.getenv("GITHUB_ACTOR")

        project.exec { commandLine = listOf("git", "config", "user.name", actor) }
        project.exec { commandLine = listOf("git", "tag", "-m", "Release $release", release) }
        project.exec { commandLine = listOf("git", "push", "--tags") }
    }
}
