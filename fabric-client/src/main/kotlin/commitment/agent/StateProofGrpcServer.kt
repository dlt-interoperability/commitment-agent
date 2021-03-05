package commitment.agent.fabric.client

import io.grpc.Server
import io.grpc.ServerBuilder
import org.mapdb.DB
import proof.StateProofServiceGrpcKt
import proof.ProofOuterClass


class StateProofGrpcServer(private val port: Int, val orgName: String, val db: DB) {
    val server: Server = ServerBuilder
            .forPort(port)
            .addService(GrpcService(orgName, db))
            .build()

    fun start() {
        server.start()
        println("StateProofService gRPC server started. Listening on port $port")
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    println("Shutting down, stopping StateProofService gRPC server...")
                    this@StateProofGrpcServer.stop()
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

class GrpcService(val orgName: String, val db: DB) : StateProofServiceGrpcKt.StateProofServiceCoroutineImplBase() {
    override suspend fun requestStateProof(request: ProofOuterClass.StateProofRequest): ProofOuterClass.StateProofResponse {
        println("Received request for state and proof: $request")
        return createProof(db, request.key, request.commitment, orgName).fold({ error ->
            println("Returning error: ${error.message}\n")
            ProofOuterClass.StateProofResponse.newBuilder()
                    .setError(error.message)
                    .build()
        }, { proof ->
            println("Returning proof: $proof\n")
            ProofOuterClass.StateProofResponse.newBuilder()
                    .setProof(proof)
                    .build()
        })
    }
}