package co.codecv

import com.hexagonkt.core.keys
import com.hexagonkt.core.logging.LoggingManager
import com.hexagonkt.core.media.mediaTypeOfOrNull
import com.hexagonkt.core.merge
import com.hexagonkt.core.require
import com.hexagonkt.core.wordsToCamel
import com.hexagonkt.logging.jul.JulLoggingAdapter
import com.hexagonkt.http.model.ContentType
import com.hexagonkt.http.model.Header
import com.hexagonkt.http.server.HttpServer
import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.callbacks.UrlCallback
import com.hexagonkt.http.server.handlers.HttpServerContext
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
import java.net.URI
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.exists

const val spec: String = "classpath:spec.yml"
const val schema: String = "classpath:cv.schema.json"
const val defaultTemplate: String = "classpath:templates/cv.html"

lateinit var server: HttpServer

fun main(vararg args: String) {
    val url = args.firstOrNull()
        ?.let { if (URI(it).scheme != null) it else "file:$it" }
        ?: "file:examples/full.cv.yml"

    val serverSettings = HttpServerSettings(zip = true)
    val protocol = serverSettings.protocol.toString().lowercase()
    val hostName = serverSettings.bindAddress.hostName
    val bindPort = serverSettings.bindPort
    val base = "$protocol://$hostName:$bindPort"
    val scriptSources = "https://unpkg.com/rapidoc/ 'unsafe-inline'"
    val adapter = NettyServerAdapter(executorThreads = 4, soBacklog = 1024)

    LoggingManager.adapter = JulLoggingAdapter(messageOnly = true, stream = System.err)
    SerializationManager.formats = linkedSetOf(Yaml, Json, Toml)
    TemplateManager.defaultAdapter = PebbleAdapter(false, 1 * 1024 * 1024)

    server = serve(adapter, serverSettings) {
        after("*") { addHeaders(scriptSources) }

        get("/openapi.{format}") { getReformattedData(spec) }
        get("/schema.{format}") { getReformattedData(schema) }
        get("/cv.{format}") { getReformattedData(url) }
        get("/cv") { renderCv(url, base) }
        get(callback = UrlCallback(URL("classpath:ui.html")))
    }
}

private fun HttpServerContext.addHeaders(scriptSources: String): HttpServerContext {
    val contentSecurityValues = listOf("script-src $scriptSources", "object-src none")
    val contentSecurityPolicy = Header("content-security-policy", contentSecurityValues)
    val xUaCompatible = Header("x-ua-compatible", "IE=edge")

    return send(headers = response.headers + contentSecurityPolicy + xUaCompatible)
}

private fun HttpServerContext.getReformattedData(url: String): HttpServerContext {
    val data = URL(url).parseMap()
    val format = pathParameters.require("format")
    val mediaType = mediaTypeOfOrNull(format)
        ?: return badRequest("Invalid extension (only 'yaml', 'yml' and 'json' allowed): $format")

    return ok(data.serialize(mediaType), contentType = ContentType(mediaType))
}

private fun HttpServerContext.renderCv(cvUrl: String, base: String): HttpServerContext {
    val url = URL(cvUrl)
    val cvData = url.parseMap()
    val errors = validate(cvData)
    if (errors.isNotEmpty()) {
        val errorSeparator = "\n - "
        val errorsText = errors.joinToString(errorSeparator, errorSeparator)
        return badRequest("CV does not complain with schema:$errorsText")
    }

    val cv = decode(cvData, url)
    val template = cv.keys<Collection<String>>("templates")?.firstOrNull() ?: defaultTemplate
    val variables = cv.keys<Map<String, Any>>("variables") ?: emptyMap()
    val templateContext = variables + mapOf("cv" to cv, "base" to base)

    return template(URL(template), templateContext + callContext())
}

private fun decode(data: Map<*, *>, url: URL): Map<*, *> {
    val fullMap = mergeIncludes(data, url)
    return toCamelCase(fullMap) as Map<*, *>
}

private fun mergeIncludes(data: Map<*, *>, base: URL): Map<*, *> {
    val baseDir by lazy { Path.of(base.file).parent }
    val includes = data.keys<List<String>>("Resources")
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
    val validator = io.vertx.json.schema.Validator.create(jsonSchema, options)

    return validator.validate(JsonObject.mapFrom(data))
        .errors
        ?.map {
            val error = it.error
            val location = it.instanceLocation
            val keyword = it.keyword
            val keywordLocation = it.keywordLocation
            "$error at $location. Cause: $keyword at $keywordLocation"
        }
        ?: emptyList()
}
