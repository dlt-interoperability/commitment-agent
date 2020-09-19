package commitment.agent.ethereum.client

import arrow.core.None
import arrow.core.Some
import java.util.*

fun main(args: Array<String>) {
    // Set up the config properties
    val config = Properties()
    val orgName = try { args[0] } catch (e: Exception) { "" }
    object {}::class.java.getResourceAsStream("/${orgName}config.properties")
            .use { config.load(it) }

    // If the ledger contract has been deployed by one of the other agents it
    // needs to be provided as the second CLI argument. Otherwise the ledger contract
    // will be deployed.
    val ledgerContractAddress = try {
        if (args[1].length > 0) {
            Some(args[1])
        } else (None)
    } catch (e: Exception) {
        None
    }
    val ledgerStateManager = LedgerStateManager(ledgerContractAddress, orgName)

    // Start the gRPC server for the commitment service
    val grpcServerPort = (config["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt()
    val server = CommitmentServiceGrpcServer(grpcServerPort, ledgerStateManager)
    server.start()
    server.blockUntilShutdown()
}
