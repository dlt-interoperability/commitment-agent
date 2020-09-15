package commitment.agent.ethereum.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    val ledgerStateManager = LedgerStateManager()
    // Start the gRPC server for the commitment service
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/config.properties")
            .use { properties.load(it) }
    val grpcServerPort = (properties["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt()
    val server = CommitmentServiceGrpcServer(grpcServerPort, ledgerStateManager)
    server.start()
    server.blockUntilShutdown()
}
