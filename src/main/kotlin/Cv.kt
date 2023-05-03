package co.codecv

import com.hexagonkt.args.Command
import com.hexagonkt.args.Option
import com.hexagonkt.args.Parameter
import com.hexagonkt.args.Program
import com.hexagonkt.args.Property.Companion.HELP
import com.hexagonkt.args.Property.Companion.VERSION
import com.hexagonkt.core.exists
import com.hexagonkt.core.getPath
import com.hexagonkt.core.logging.LoggingManager
import com.hexagonkt.core.logging.logger
import com.hexagonkt.core.media.mediaTypeOfOrNull
import com.hexagonkt.core.require
import com.hexagonkt.core.merge
import com.hexagonkt.helpers.properties
import com.hexagonkt.helpers.wordsToCamel
import com.hexagonkt.logging.jul.JulLoggingAdapter
import com.hexagonkt.http.model.ContentType
import com.hexagonkt.http.model.Header
import com.hexagonkt.http.server.HttpServer
import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.callbacks.UrlCallback
import com.hexagonkt.http.handlers.HttpContext
import com.hexagonkt.http.server.netty.NettyServerAdapter
import com.hexagonkt.http.server.serve
import com.hexagonkt.serialization.*
import com.hexagonkt.serialization.jackson.json.Json
import com.hexagonkt.serialization.jackson.toml.Toml
import com.hexagonkt.serialization.jackson.yaml.Yaml
import com.hexagonkt.templates.TemplateManager
import com.hexagonkt.templates.pebble.PebbleAdapter
import com.hexagonkt.web.callContext
import com.hexagonkt.web.template
import io.vertx.core.json.JsonObject
import io.vertx.json.schema.Draft.DRAFT7
import io.vertx.json.schema.JsonSchema
import io.vertx.json.schema.JsonSchemaOptions
import io.vertx.json.schema.Validator
import java.net.URI
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.system.exitProcess

const val spec: String = "classpath:spec.yml"
const val schema: String = "classpath:cv.schema.json"
const val defaultTemplate: String = "classpath:templates/cv.html"
const val buildProperties: String = "classpath:META-INF/build.properties"

lateinit var server: HttpServer

fun main(vararg args: String) {
    val buildProperties = properties(URL(buildProperties))
    val project = buildProperties.require("project")

    LoggingManager.adapter = JulLoggingAdapter(messageOnly = true, stream = System.err)
    LoggingManager.defaultLoggerName = project
    SerializationManager.formats = linkedSetOf(Yaml, Json, Toml)

    val program = createProgram(buildProperties)
    val command = program.parse(args)

    when (command.name) {
        project -> serve(command)

        else -> error("")
    }
}

private fun serve(command: Command) {
    val url = command.parametersMap.require("url").values.first().let {
        val urlValue = it as String
        if (URI(urlValue).scheme != null) urlValue else "file:$urlValue"
    }

    if(!URL(url).exists()) {
        logger.error { "CV url not found: $url" }
        exitProcess(1)
    }

    TemplateManager.defaultAdapter = PebbleAdapter(false, 1 * 1024 * 1024)
    val serverSettings = HttpServerSettings(zip = true)
    val protocol = serverSettings.protocol.toString().lowercase()
    val hostName = serverSettings.bindAddress.hostName
    val bindPort = serverSettings.bindPort
    val base = "$protocol://$hostName:$bindPort"
    val scriptSources = "https://unpkg.com/rapidoc/ 'unsafe-inline'"
    val adapter = NettyServerAdapter(executorThreads = 4, soBacklog = 1024)

    server = serve(adapter, serverSettings) {
        after("*") { addHeaders(scriptSources) }

        get("/openapi.{format}") { getReformattedData(spec) }
        get("/schema.{format}") { getReformattedData(schema) }
        get("/cv.{format}") { getReformattedData(url) }
        get("/cv") { renderCv(url, base) }
        get(callback = UrlCallback(URL("classpath:ui.html")))
    }
}

private fun createProgram(buildProperties: Map<String, String>): Program {
    val urlParameterDescription = "URL to the CV file to use. If no schema, 'file' is assumed"
    val urlParameter = Parameter(String::class, "url", urlParameterDescription, optional = false)
    val kindOption = Option(String::class, 'k', "kind", "desc", Regex("(regular|full|minimum)"), value = "regular")
    val formatOption = Option(String::class, 'f', "format", "desc", Regex("(yaml|json|toml)"), value = "yaml")
    return  Program(
        name = buildProperties.require("project"),
        version = buildProperties.require("version"),
        description = buildProperties.require("description"),
        properties = setOf(
            VERSION,
            HELP,
            urlParameter
        ),
        commands = setOf(
            Command(
                name = "create",
                title = "title",
                description = "description",
                properties = setOf(HELP, kindOption, formatOption, urlParameter),
            ),
            Command(
                name = "validate",
                title = "title",
                description = "description",
                properties = setOf(HELP, urlParameter),
            ),
        ),
    )
}

private fun HttpContext.addHeaders(scriptSources: String): HttpContext {
    val contentSecurityValues = listOf("script-src $scriptSources", "object-src none")
    val contentSecurityPolicy = Header("content-security-policy", contentSecurityValues)
    val xUaCompatible = Header("x-ua-compatible", "IE=edge")

    return send(headers = response.headers + contentSecurityPolicy + xUaCompatible)
}

private fun HttpContext.getReformattedData(url: String): HttpContext {
    val data = URL(url).parseMap()
    val format = pathParameters.require("format")
    val mediaType = mediaTypeOfOrNull(format)
        ?: return badRequest("Invalid extension (only 'yaml', 'yml' and 'json' allowed): $format")

    return ok(data.serialize(mediaType), contentType = ContentType(mediaType))
}

private fun HttpContext.renderCv(cvUrl: String, base: String): HttpContext {
    val url = URL(cvUrl)
    val cvData = url.parseMap()
    val errors = validate(cvData)
    if (errors.isNotEmpty()) {
        val errorSeparator = "\n - "
        val errorsText = errors.joinToString(errorSeparator, errorSeparator)
        return badRequest("CV does not complain with schema:$errorsText")
    }

    val cv = decode(cvData, url)
    val template = cv.getPath<Collection<String>>("templates")?.firstOrNull() ?: defaultTemplate
    val variables = cv.getPath<Map<String, Any>>("variables") ?: emptyMap()
    val templateContext = variables + mapOf("cv" to cv, "base" to base)

    return template(URL(template), templateContext + callContext())
}

private fun decode(data: Map<*, *>, url: URL): Map<*, *> {
    val fullMap = mergeIncludes(data, url)
    return toCamelCase(fullMap) as Map<*, *>
}

private fun mergeIncludes(data: Map<*, *>, base: URL): Map<*, *> {
    val baseDir by lazy { Path.of(base.file).parent }
    val includes = data.getPath<List<String>>("Resources")
        ?.map(::URL)
        ?.map {
            if (it.protocol == "file")
                Path.of(it.file).let { p ->
                    if (p.exists() || p.isAbsolute) it
                    else baseDir.resolve(p).toUri().toURL()
                }
            else
                it
        }
        ?: emptyList()

    val allMaps = listOf(data) + includes.map(URL::parseMap)
    return merge(allMaps)
}

private fun toCamelCase(data: Any?): Any? =
    when (data) {
        is Map<*, *> ->
            data.mapKeys { it.key.toString().split(" ").wordsToCamel() }
                .mapValues { (k, v) ->
                    if (k.lowercase() == "variables") v
                    else toCamelCase(v)
                }
        is List<*> ->
            data.map { toCamelCase(it) }
        else ->
            data
    }

private fun validate(data: Map<*, *>): List<String> {
    val schemaMap = URL(schema).parseMap()
    val jsonSchema = JsonSchema.of(JsonObject.mapFrom(schemaMap))
    val options = JsonSchemaOptions().setDraft(DRAFT7).apply { baseUri = "file:./" }
    val validator = Validator.create(jsonSchema, options)

    return validator.validate(JsonObject.mapFrom(data))
        .errors
        ?.map {
            val error = it.error
            val location = it.instanceLocation
            val keywordLocation = it.keywordLocation
            "$error at $location. Cause at: $keywordLocation"
        }
        ?: emptyList()
}
