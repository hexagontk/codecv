import org.gradle.api.JavaVersion.*
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import java.io.BufferedReader

plugins {
    kotlin("jvm") version("2.2.20")
    id("org.graalvm.buildtools.native") version("0.11.1")
}

val hexagonVersion = "4.2.3"
val vertxVersion = "5.0.4"
val slf4jVersion = "2.0.17"

val gradleScripts = "https://raw.githubusercontent.com/hexagontk/hexagon/$hexagonVersion/gradle"

ext.set("modules", "java.logging")
ext.set("options", "-Xmx32m")
ext.set("icon", "$projectDir/logo.png")
ext.set("applicationClass", "co.codecv.CvKt")

apply(from = "$gradleScripts/kotlin.gradle")
apply(from = "$gradleScripts/application.gradle")
apply(from = "$gradleScripts/native.gradle")

group = "co.codecv.tools"
version = "0.9.26"
description = "CVs for programmers"

if (current() !in setOf(VERSION_17, VERSION_18, VERSION_19, VERSION_20, VERSION_21))
    error("This build must be run with JDK 17+. Current: ${current()}")

dependencies {
    "implementation"("com.hexagontk:helpers:$hexagonVersion")
    "implementation"("com.hexagontk.http:http_server_helidon:$hexagonVersion")
    "implementation"("com.hexagontk.serialization:serialization_jackson_json:$hexagonVersion")
    "implementation"("com.hexagontk.serialization:serialization_jackson_yaml:$hexagonVersion")
    "implementation"("com.hexagontk.serialization:serialization_jackson_toml:$hexagonVersion")
    "implementation"("com.hexagontk.templates:templates_pebble:$hexagonVersion")
    "implementation"("com.hexagontk.http:web:$hexagonVersion")
    "implementation"("com.hexagontk.extra:shell:$hexagonVersion")

    "implementation"("io.vertx:vertx-json-schema:$vertxVersion")
    "implementation"("org.slf4j:log4j-over-slf4j:$slf4jVersion")
    "implementation"("org.slf4j:jcl-over-slf4j:$slf4jVersion")
    "implementation"("org.slf4j:slf4j-jdk14:$slf4jVersion")

    "testImplementation"("com.hexagontk.http:http_client_jetty:$hexagonVersion")
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

tasks.register<Copy>("addResources") {
    from(projectDir)
    include("examples/**")
    include("cv.schema.json")
    into(layout.buildDirectory.file("resources/main"))
}

tasks.register("release") {
    dependsOn("build")

    doLast {
        val release = version.toString()
        val actor = System.getenv("GITHUB_ACTOR")

        execute(listOf("git", "config", "user.name", actor))
        execute(listOf("git", "tag", "-m", "Release $release", release))
        execute(listOf("git", "push", "--tags"))
    }
}

private fun execute(command: List<String>) {
    Runtime
        .getRuntime()
        .exec(command.toTypedArray())
        .let { process ->
            process.waitFor()
            val output = process.inputStream.use {
                it.bufferedReader().use(BufferedReader::readText)
            }
            process.destroy()
            output.trim()
        }
}

tasks.wrapper {
    gradleVersion = "9.1.0"
    distributionType = ALL
}
