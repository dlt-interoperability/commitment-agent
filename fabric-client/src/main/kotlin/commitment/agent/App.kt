package commitment.agent.fabric.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    val orgName = try { args[0] } catch (e: Exception) { "" }

    // Start the Fabric client
    GlobalScope.launch {
        val fabricClient = FabricClient(orgName)
        fabricClient.start()
        val isInitializer = try { args[1] == "true" } catch (e: Exception) { false }
        if (isInitializer) {
            fabricClient.initialize()
        }
    }

    // Start the gRPC server for the external client to make state requests to
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/${orgName}config.properties")
            .use { properties.load(it) }
    val grpcServerPort = (properties["STATE_PROOF_GRPC_SERVER_PORT"] as String).toInt()
    val server = StateProofGrpcServer(grpcServerPort, orgName)
    server.start()
    server.blockUntilShutdown()
}
