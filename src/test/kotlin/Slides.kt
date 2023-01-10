package co.codecv

import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.callbacks.FileCallback
import com.hexagonkt.http.server.jetty.serve
import java.awt.Desktop
import java.io.File
import java.net.URI

fun main() {
    val server = serve(HttpServerSettings(bindPort = 9999)) {
        get("/*", FileCallback(File("src/test/resources")))
    }
    Desktop.getDesktop().browse(URI("http://localhost:${server.runtimePort}/revealjs.html"))
}
