package co.codecv.slides

import com.hexagontk.http.server.HttpServerSettings
import com.hexagontk.http.server.callbacks.FileCallback
import com.hexagontk.http.server.netty.NettyHttpServer
import com.hexagontk.http.server.serve
import java.io.File

fun main() {
    serve(NettyHttpServer(), HttpServerSettings(bindPort = 9999)) {
        get("/*", FileCallback(File("src/test/resources")))
    }
}
