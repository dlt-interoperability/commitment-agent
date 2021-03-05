package commitment.agent.fabric.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mapdb.DBMaker
import java.util.*

fun main(args: Array<String>) {
    val orgName = try { args[0] } catch (e: Exception) { "" }
    val isInitialise = try { args[1] == "true" } catch (e: Exception) { false }
    val isPrimaryOrg = try { args[2] == "true" } catch (e: Exception) { false }

    // Start the Fabric client. If the isInitialise flag was provided the admin and
    // user credentials will be created and registered with the Fabric network.
    // If the isPrimaryOrg flag was provided, the management committee will be registered
    // with the bulletin board. If neither flag is provided, the client will start
    // listening for block events from the Fabric network.
    val fabricClient = FabricClient(orgName)
    if (isInitialise) {
        fabricClient.initialize()
        if (isPrimaryOrg) fabricClient.setManagementCommittee()
    } else {
        val db = DBMaker.memoryDB().make()
        GlobalScope.launch {
            fabricClient.start(db)
        }

        // Start the gRPC server for the external client to make state requests to
        val config = Properties()
        object {}::class.java.getResourceAsStream("/${orgName}config.properties")
                .use { config.load(it) }
        val grpcServerPort = (config["STATE_PROOF_GRPC_SERVER_PORT"] as String).toInt()
        val server = StateProofGrpcServer(grpcServerPort, orgName, db)
        server.start()
        server.blockUntilShutdown()
    }

}
