package commitment.agent.ethereum.client

import commitment.CommitmentOuterClass
import commitment.CommitmentServiceGrpcKt
import io.grpc.ServerBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CommitmentServiceGrpcServer(private val port: Int, private val ledgerStateManager: LedgerStateManager) {

    val server = ServerBuilder
            .forPort(port)
            .addService(GrpcService(ledgerStateManager))
            .build()

    fun start() {
        server.start()
        println("CommitmentService gRPC server started on port $port")
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    println("Shutting down, stopping CommitmentService gRPC server...")
                }
        )
    }

    private fun stop() {
       server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }
}

class GrpcService(val ledgerStateManager: LedgerStateManager) : CommitmentServiceGrpcKt.CommitmentServiceCoroutineImplBase() {

    override suspend fun sendCommittee(request: CommitmentOuterClass.Committee): CommitmentOuterClass.Ack {
        println("Received committee from Fabric client: $request")
        val ack = CommitmentOuterClass.Ack.newBuilder()
                .setStatus(CommitmentOuterClass.Ack.STATUS.OK)
                .setMessage("Received public keys for the management committee")
                .build()
        val publicKeys = request.publicKeysList.asByteStringList().map {
            it.toStringUtf8()
        }
        ledgerStateManager.setManagementCommittee(publicKeys)
        println("Sending back ack: $ack\n")
        return ack
    }

    override suspend fun sendCommitment(request: CommitmentOuterClass.Commitment): CommitmentOuterClass.Ack {
        println("Received commitment from Fabric client: $request")
        val ack = CommitmentOuterClass.Ack.newBuilder()
                .setStatus(CommitmentOuterClass.Ack.STATUS.OK)
                .setMessage("Received commitment for block ${request.blockHeight}")
                .build()
        ledgerStateManager.postCommitment(request.accumulator, request.rollingHash, request.blockHeight)
        println("Sending back ack: $ack\n")
        return ack
    }
}