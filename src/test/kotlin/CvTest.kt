package co.codecv

import com.hexagonkt.http.client.HttpClient
import com.hexagonkt.http.client.HttpClientSettings
import com.hexagonkt.http.client.jetty.JettyClientAdapter
import com.hexagonkt.http.model.BAD_REQUEST_400
import com.hexagonkt.http.model.HttpResponsePort
import com.hexagonkt.http.model.HttpStatus
import com.hexagonkt.http.model.OK_200
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File
import java.lang.System.getProperty
import java.lang.System.setProperty
import java.net.URL
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
internal class CvTest {

    @BeforeEach fun setUp() {
        setProperty(exitCodeProperty, "")
    }

    @AfterEach fun shutDown() {
        setProperty(exitCodeProperty, "")
        setProperty(preventExitFlag, false.toString())
    }

    @Test fun `Check modular examples`() {
        testCv("file:examples/modular/full.cv", setOf("yml"))
        testCv("file:examples/modular/brief.cv", setOf("yml"))
        testCv("examples/modular/full.cv", setOf("yml"))
        testCv("examples/modular/brief.cv", setOf("yml"))
    }

    @Test fun `Check examples`() {
        testCv("file:examples/full.cv")
        testCv("file:examples/minimum.cv")
        testCv("file:examples/regular.cv")
    }

    @Test fun `Check examples with valid address`() {
        val extensions: Set<String> = setOf("json", "toml", "yml")

        testCv("file:examples/full.cv", extensions, "0.0.0.0")
        testCv("file:examples/minimum.cv", extensions, "0.0.0.0")
        testCv("file:examples/regular.cv", extensions, "0.0.0.0")
    }

    @Test fun `Check create command`() {
        setProperty(preventExitFlag, true.toString())

        main("create")
        checkExitCode()

        main("create", "-f", "json")
        checkExitCode()
        main("create", "-t", "minimum")
        checkExitCode()
        main("create", "-f", "json", "-t", "minimum")
        checkExitCode()

        listOf("regular", "full", "minimum").forEach { t ->
            listOf("yaml", "toml", "json").forEach { f ->
                main("create", "-t", t, "-f", f, "build/$t.cv.$f")
                checkExitCode()
                assertEquals(
                    File("build/$t.cv.$f").readText(),
                    File("examples/$t.cv.${if (f == "yaml") "yml" else f}").readText()
                )
            }
        }
    }

    @Test fun `Check validate command`() {
        setProperty(preventExitFlag, true.toString())

        main("validate", "file:build/x.cv.yml")
        checkExitCode(404)
        main("validate", "file:examples/full.cv.yml")
        checkExitCode()
        main("validate", "examples/full.cv.yml")
        checkExitCode()
        main("validate", "file:src/test/resources/incorrect.cv.yml")
        checkExitCode(400)
    }

    @Test fun `Check serve command`() {
        setProperty(preventExitFlag, true.toString())

        main("serve", "file:examples/full.cv.yml")
        testHttp(server.runtimePort)
        server.stop()
        checkExitCode()

        main("serve", "file:build/invalid.cv.yml")
        checkExitCode(404)
        main("serve", "build/invalid.cv.yml")
        checkExitCode(404)

        main("serve", "file:src/test/resources/incorrect.cv.yml")
        val baseUrl = URL("http://localhost:${server.runtimePort}")
        val settings = HttpClientSettings(baseUrl = baseUrl)
        val http = HttpClient(JettyClientAdapter(), settings)
        http.start()
        assertEquals(BAD_REQUEST_400, http.get("/cv").status)
        server.stop()
    }

    private fun testCv(url: String, extensions: Set<String> = setOf("json", "toml", "yml"), address: String = "127.0.0.1") {
        extensions.forEach {
            main("-a", address, "serve", "${url}.${it}")
            testHttp(server.runtimePort)
            server.stop()
        }
    }

    private fun testHttp(port: Int) {
        val baseUrl = URL("http://localhost:$port")
        val settings = HttpClientSettings(baseUrl = baseUrl)
        val http = HttpClient(JettyClientAdapter(), settings)

        http.start()
        http.get("/openapi.json").checkResponse()
        http.get("/openapi.yaml").checkResponse()
        http.get("/openapi.yml").checkResponse()
        http.get("/cv.json").checkResponse()
        http.get("/cv.toml").checkResponse()
        http.get("/cv.yaml").checkResponse()
        http.get("/cv.yml").checkResponse()
        http.get("/cv").checkResponse()
        http.get("/cv.x").checkResponse(BAD_REQUEST_400)
        http.get()
        http.stop()
    }

    private fun checkExitCode() {
        val code = getProperty(exitCodeProperty)
        assert(code == null || code == "")
        setProperty(exitCodeProperty, "")
    }

    private fun checkExitCode(code: Int) {
        assertEquals(code.toString(), getProperty(exitCodeProperty))
        setProperty(exitCodeProperty, "")
    }

    private fun HttpResponsePort.checkResponse(expectedStatus: HttpStatus = OK_200) {
        assertEquals(expectedStatus, status)
    }
}
