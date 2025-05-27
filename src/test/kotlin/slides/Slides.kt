package co.codecv.slides

import com.hexagontk.http.server.HttpServerSettings
import com.hexagontk.http.server.callbacks.FileCallback
import com.hexagontk.http.server.helidon.HelidonHttpServer
import com.hexagontk.http.server.serve
import java.io.File

fun main() {
    serve(HelidonHttpServer(), HttpServerSettings(bindPort = 9999)) {
        get("/*", FileCallback(File("src/test/resources")))
    }
}
