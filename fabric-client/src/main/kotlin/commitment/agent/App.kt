package commitment.agent.fabric.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    // Start the Fabric client
    GlobalScope.launch {
        val fabricClient = FabricClient()
        fabricClient.start()
    }

    // Start the gRPC server for the external client to make state requests to
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/config.properties")
            .use { properties.load(it) }
    val grpcServerPort = (properties["GRPC_SERVER_PORT"] as String).toInt()
    val server = GrpcServer(grpcServerPort)
    server.start()
    server.blockUntilShutdown()
}
