package commitment.agent.fabric.client

import io.grpc.Server
import io.grpc.ServerBuilder
import proof.AgentGrpcKt
import proof.ProofOuterClass

class GrpcServer(private val port: Int) {
    val server: Server = ServerBuilder
            .forPort(port)
            .addService(GrpcService())
            .build()

    fun start() {
        server.start()
        println("Agent gRPC server started. Listening on port $port")
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    println("Shutting down, stopping Corda driver gRPC server...")
                    this@GrpcServer.stop()
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

class GrpcService : AgentGrpcKt.AgentCoroutineImplBase() {
    override suspend fun requestState(request: ProofOuterClass.Request): ProofOuterClass.ProofResponse {
        println("Received request for state and proof: $request")
        return createProof(request.key, request.commitment).fold({ error ->
            println("Returning error: ${error.message}\n")
            ProofOuterClass.ProofResponse.newBuilder()
                    .setError(error.message)
                    .build()
        }, { proof ->
            println("Returning proof: $proof\n")
            ProofOuterClass.ProofResponse.newBuilder()
                    .setProof(proof)
                    .build()
        })
    }
}