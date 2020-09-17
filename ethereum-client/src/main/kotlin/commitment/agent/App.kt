package commitment.agent.ethereum.client

import arrow.core.None
import arrow.core.Some
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    // Set up the config properties
    val properties = Properties()
    val orgName = try { args[0] } catch (e: Exception) { "" }
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/${orgName}config.properties")
            .use { properties.load(it) }

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
    val ledgerStateManager = LedgerStateManager(ledgerContractAddress)

    // Start the gRPC server for the commitment service
    val grpcServerPort = (properties["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt()
    val server = CommitmentServiceGrpcServer(grpcServerPort, ledgerStateManager)
    server.start()
    server.blockUntilShutdown()
}
