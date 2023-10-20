package co.codecv

import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.callbacks.FileCallback
import com.hexagonkt.http.server.netty.serve
import java.io.File

fun main() {
    serve(HttpServerSettings(bindPort = 9999)) {
        get("/*", FileCallback(File("src/test/resources")))
    }
}
