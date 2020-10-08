package commitment.agent.fabric.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

fun main(args: Array<String>) {
    val orgName = try { args[0] } catch (e: Exception) { "" }

    // Start the Fabric client
    GlobalScope.launch {
        val fabricClient = FabricClient(orgName)
        val isInitializer = try { args[1] == "true" } catch (e: Exception) { false }
        if (isInitializer) {
            fabricClient.initialize()
        }
        fabricClient.start()
    }

    // Start the gRPC server for the external client to make state requests to
    val config = Properties()
    object {}::class.java.getResourceAsStream("/${orgName}config.properties")
            .use { config.load(it) }
    val grpcServerPort = (config["STATE_PROOF_GRPC_SERVER_PORT"] as String).toInt()
    val server = StateProofGrpcServer(grpcServerPort, orgName)
    server.start()
    server.blockUntilShutdown()
}
