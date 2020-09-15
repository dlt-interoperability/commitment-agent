package commitment.agent.ethereum.client

import arrow.core.flatMap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    // Start the Ethereum client
    GlobalScope.launch {
        val ethereumClient = EthereumClient()
        val txReceipt = ethereumClient.deployLedgerContract()
                .flatMap {
                    ethereumClient.setManagementCommittee(
                        it,
                        listOf("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEF8aDG+qooCnUXLwwYvOa/Uc5uOrSDxFvVZAZaklQimwljrU3RgTekYvCqYQLwO6yE7p+WFQR19HnqVghy5tQPw=="),
                        listOf("0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1"))
                }
    }

    // Start the gRPC server for the commitment service
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/config.properties")
            .use { properties.load(it) }
    val grpcServerPort = (properties["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt()
    val server = CommitmentServiceGrpcServer(grpcServerPort)
    server.start()
    server.blockUntilShutdown()
}
