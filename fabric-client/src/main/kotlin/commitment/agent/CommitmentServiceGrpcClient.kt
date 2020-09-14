package commitment.agent.fabric.client

import arrow.core.Left
import arrow.core.Right
import commitment.CommitmentOuterClass
import commitment.CommitmentServiceGrpcKt.CommitmentServiceCoroutineStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.TimeUnit

class CommitmentServiceGrpcClient(private val channel: ManagedChannel) : Closeable {
    private val stub = CommitmentServiceCoroutineStub(channel)

    suspend fun sendCommitment(commitment: CommitmentOuterClass.Commitment) = coroutineScope {
        println("Sending commitment to Ethereum client for block: ${commitment.blockHeight}")
        val response = async { stub.sendCommitment(commitment) }.await()
        println("Received Ack: $response")
        response
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

fun sendCommitmentHelper(accumulator: String, blockNum: Int) = try {
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/config.properties")
            .use { properties.load(it) }
    val client = CommitmentServiceGrpcClient(
            ManagedChannelBuilder.forAddress(
                    properties["COMMITMENT_GRPC_SERVER_HOST"] as String,
                    (properties["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt())
                    .usePlaintext()
                    .executor(Dispatchers.Default.asExecutor())
                    .build())
    val commitment = CommitmentOuterClass.Commitment.newBuilder()
            .setAccumulator(accumulator)
            .setBlockHeight(blockNum)
            .build()
    runBlocking {
        val ack = async { client.sendCommitment(commitment) }.await()
        if (ack.status == CommitmentOuterClass.Ack.STATUS.OK) {
            Right(Unit)
        } else {
            println("Error sending accumulator to Ethereum client: ${ack.message}\n")
            Left(Error("Error sending accumulator to Ethereum client: ${ack.message}\n"))
        }
    }
} catch (e: Exception) {
    println("Error sending accumulator to Ethereum client: ${e.message}\n")
    Left(Error("Error sending accumulator to Ethereum client: ${e.message}\n"))
}