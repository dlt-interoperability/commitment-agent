package commitment.agent.ethereum.client

import commitment.CommitmentOuterClass
import commitment.CommitmentServiceGrpcKt
import io.grpc.ServerBuilder

class CommitmentServiceGrpcServer(private val port: Int) {
    val server = ServerBuilder
            .forPort(port)
            .addService(GrpcService())
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

class GrpcService : CommitmentServiceGrpcKt.CommitmentServiceCoroutineImplBase() {
    override suspend fun sendCommitment(commitment: CommitmentOuterClass.Commitment): CommitmentOuterClass.Ack {
        println("Received commitment from Fabric client: $commitment")
        val ack = CommitmentOuterClass.Ack.newBuilder()
                .setStatus(CommitmentOuterClass.Ack.STATUS.OK)
                .setMessage("Received commitment for block ${commitment.blockHeight}")
                .build()
        println("Sending back ack: $ack\n")
        return ack
    }
}