package co.codecv

import com.hexagonkt.http.client.HttpClient
import com.hexagonkt.http.client.HttpClientSettings
import com.hexagonkt.http.client.jetty.JettyClientAdapter
import com.hexagonkt.http.model.HttpResponsePort
import com.hexagonkt.http.model.OK_200
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.test.assertEquals

internal class CvTest {

    @Test fun `Check modular examples`() {
        testCv("file:examples/modular/full.cv", setOf("yml"))
        testCv("file:examples/modular/brief.cv", setOf("yml"))
    }

    @Test fun `Check examples`() {
        testCv("file:examples/full.cv")
        testCv("file:examples/minimum.cv")
        testCv("file:examples/regular.cv")
    }

    private fun testCv(url: String, extensions: Set<String> = setOf("json", "toml", "yml")) {
        extensions.forEach {
            main("${url}.${it}")

            val baseUrl = URL("http://localhost:${server.runtimePort}")
            val settings = HttpClientSettings(baseUrl = baseUrl)
            val http = HttpClient(JettyClientAdapter(), settings)

            http.start()
            http.get("/openapi.json").checkResponse()
            http.get("/openapi.yaml").checkResponse()
            http.get("/openapi.yml").checkResponse()
            http.get("/cv.json").checkResponse()
            http.get("/cv.yaml").checkResponse()
            http.get("/cv.yml").checkResponse()
            http.get("/cv").checkResponse()
            http.get()
            http.stop()

            server.stop()
        }
    }

    private fun HttpResponsePort.checkResponse() {
        assertEquals(OK_200, status)
    }
}
