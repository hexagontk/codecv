package co.codecv

import com.hexagonkt.args.Command
import com.hexagonkt.core.*
import com.hexagonkt.core.OsKind.*
import com.hexagonkt.core.logging.logger
import com.hexagonkt.core.media.mediaTypeOfOrNull
import com.hexagonkt.core.text.wordsToCamel
import com.hexagonkt.http.handlers.HttpContext
import com.hexagonkt.http.model.ContentType
import com.hexagonkt.http.model.Header
import com.hexagonkt.http.server.HttpServer
import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.callbacks.UrlCallback
import com.hexagonkt.http.server.netty.NettyServerAdapter
import com.hexagonkt.http.server.serve
import com.hexagonkt.serialization.SerializationManager
import com.hexagonkt.serialization.jackson.json.Json
import com.hexagonkt.serialization.jackson.toml.Toml
import com.hexagonkt.serialization.jackson.yaml.Yaml
import com.hexagonkt.serialization.parseMap
import com.hexagonkt.serialization.serialize
import com.hexagonkt.templates.TemplateManager
import com.hexagonkt.templates.pebble.PebbleAdapter
import com.hexagonkt.web.callContext
import com.hexagonkt.web.template
import io.vertx.core.json.JsonObject
import io.vertx.json.schema.Draft.DRAFT7
import io.vertx.json.schema.JsonSchema
import io.vertx.json.schema.JsonSchemaOptions
import io.vertx.json.schema.OutputFormat.Basic
import io.vertx.json.schema.Validator
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.logging.LogManager
import kotlin.system.exitProcess

const val preventExitFlag: String = "PREVENT_EXIT"
const val exitCodeProperty: String = "EXIT_CODE"

const val spec: String = "classpath:spec.yml"
const val schema: String = "classpath:cv.schema.json"
const val defaultTemplate: String = "classpath:examples/cv.html"
const val buildProperties: String = "classpath:META-INF/build.properties"
const val mainPage: String = "classpath:ui.html"

const val serveCommandName: String = "serve"
const val createCommandName: String = "create"
const val validateCommandName: String = "validate"

const val urlParamName: String = "url"
const val addressParamName: String = "address"
const val fileParamName: String = "file"
const val templateOptShortName: Char = 't'
const val formatOptShortName: Char = 'f'

private val browseCommand: List<String> by lazy {
    when (Jvm.osKind) {
        WINDOWS -> listOf("start")
        MACOS -> listOf("open")
        LINUX, UNIX -> listOf("xdg-open")
    }
}

lateinit var server: HttpServer

fun main(vararg args: String) {
    try {
        val buildProperties = properties(urlOf(buildProperties))
        val project = buildProperties.require("project")
        val loggingConfiguration = urlOf("classpath:logging.properties")

        System.setProperty("hexagonkt_logging_logger_name", project)
        LogManager.getLogManager().readConfiguration(loggingConfiguration.openStream())
        SerializationManager.formats = linkedSetOf(Yaml, Json, Toml)

        val program = createProgram(buildProperties)
        val command = program.parse(args)

        when (command.name) {
            serveCommandName, project -> serve(command)
            createCommandName -> create(command)
            validateCommandName -> validate(command)
        }
    }
    catch (e: Exception) {
        exit(e)
    }
}

private fun exit(exception: Exception) {
    logger.error(exception) { exception.message }
    val code = (exception as? CodedException)?.code ?: 500

    if (Jvm.systemFlag(preventExitFlag))
        System.setProperty(exitCodeProperty, code.toString())
    else
        exitProcess(code)
}

private fun create(command: Command) {
    val template = command.propertyValueOrNull<String>(templateOptShortName.toString())
    val format = command.propertyValueOrNull<String>(formatOptShortName.toString())
    val extension = if (format == "yaml") "yml" else format
    val file = command.propertyValueOrNull<String>(fileParamName)

    val url = urlOf("classpath:examples/$template.cv.$extension")
    val content = url.readText()

    if (file != null)
        File(file).writeText(content)
    else
        logger.info { content }
}

private fun urlParameter(command: Command): URL {
    val urlParameter = command.propertyValue<String>(urlParamName)
        .let { if (URI(it).scheme != null) it else "file:$it" }

    val url = urlOf(urlParameter)
    return if (!url.exists()) throw CodedException(404, "CV url not found: $url") else url
}

private fun validate(command: Command) {
    val url = urlParameter(command)

    if (!url.exists())
        throw CodedException(404, "CV url not found: $url")

    val errors = validate(url.parseMap())

    if (errors.isNotEmpty()) {
        val errorsDetail = errors.joinToString("\n - ", "\n")
        val message = "Document in '$url' don't comply with CV schema. Errors: $errorsDetail"
        throw CodedException(400, message)
    }
}

private fun serve(command: Command) {
    TemplateManager.defaultAdapter = PebbleAdapter(false, 1 * 1024 * 1024)

    val address = addressParameter(command)
    val url = urlParameter(command)
    val urlString = url.toString()
    val serverSettings = HttpServerSettings(address, zip = true)
    val scriptSources = "https://unpkg.com/rapidoc/ 'unsafe-inline'"
    val adapter = NettyServerAdapter(executorThreads = 4, soBacklog = 1024)

    server = serve(adapter, serverSettings) {
        after("*") { addHeaders(scriptSources) }

        get("/openapi.{format}") { getReformattedData(spec) }
        get("/schema.{format}") { getReformattedData(schema) }
        get("/cv.{format}") { getReformattedData(urlString) }
        get("/cv") { renderCv(urlString, serverSettings.bindUrl.toString()) }
        get(callback = UrlCallback(urlOf(mainPage)))
    }

    if (command.propertyValueOrNull<Boolean>("b") == true)
        (browseCommand + "http://localhost:${server.runtimePort}/cv").exec()
}

private fun addressParameter(command: Command): InetAddress {
    command.propertyValue<String>(addressParamName).split('.').let {
        if (it.size == 4)
            return InetAddress.getByAddress(it.map(String::toInt).map(Int::toByte).toByteArray())
    }
    return InetAddress.getByAddress(byteArrayOf(127, 0, 0, 0))
}

private fun HttpContext.addHeaders(scriptSources: String): HttpContext {
    val contentSecurityValues = listOf("script-src $scriptSources", "object-src 'none'")
    val contentSecurityPolicy = Header("content-security-policy", contentSecurityValues)
    val xUaCompatible = Header("x-ua-compatible", "IE=edge")

    return send(headers = response.headers + contentSecurityPolicy + xUaCompatible)
}

private fun HttpContext.getReformattedData(url: String): HttpContext {
    val data = urlOf(url).parseMap()
    val format = pathParameters.require("format")
    val mediaType = mediaTypeOfOrNull(format)
        ?: return badRequest("Invalid extension (only 'yaml', 'yml', 'toml' and 'json'): $format")

    return ok(data.serialize(mediaType), contentType = ContentType(mediaType))
}

private fun HttpContext.renderCv(cvUrl: String, bindUrl: String): HttpContext {
    val url = urlOf(cvUrl)
    val cvData = url.parseMap()
    val errors = validate(cvData)
    if (errors.isNotEmpty()) {
        val errorsDetail = errors.joinToString("\n - ", "\n")
        return badRequest("CV does not complain with schema. Errors: $errorsDetail")
    }

    val cv = decode(cvData, url)
    val template = cv.getPath<Collection<String>>("templates")?.firstOrNull() ?: defaultTemplate
    val templateUrl = resolve(template, url)
    val variables = cv.getPath<Map<String, Any>>("variables") ?: emptyMap()
    val templateContext = variables + mapOf("cv" to cv, "bindUrl" to bindUrl)

    return template(templateUrl, templateContext + callContext())
}

private fun resolve(url: String, base: URL): URL {
    return if (!url.startsWith("classpath:")) {
        val cvBase = Path.of(base.path).parent
        cvBase.resolve(Path.of(url)).toUri().toURL()
    }
    else {
        urlOf(url)
    }
}

private fun decode(data: Map<*, *>, url: URL): Map<*, *> {
    val fullMap = mergeIncludes(data, url)
    return toCamelCase(fullMap) as Map<*, *>
}

private fun mergeIncludes(data: Map<*, *>, base: URL): Map<*, *> {
    val includes = data.getPath<List<String>>("Resources")
        ?.map { resolve(it, base) }
        ?: emptyList()

    val allMaps = includes.map(URL::parseMap) + listOf(data)
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
    val schemaMap = urlOf(schema).parseMap()
    val jsonSchema = JsonSchema.of(JsonObject.mapFrom(schemaMap))
    val draft = JsonSchemaOptions().setDraft(DRAFT7)
    val options = draft.setOutputFormat(Basic).setBaseUri("file:./")
    val validator = Validator.create(jsonSchema, options)

    return validator.validate(JsonObject.mapFrom(data)).errors?.map { e -> e.error } ?: emptyList()
}
