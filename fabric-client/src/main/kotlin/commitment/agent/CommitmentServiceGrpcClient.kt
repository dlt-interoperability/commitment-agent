package commitment.agent.fabric.client

import arrow.core.Left
import arrow.core.Right
import commitment.CommitmentOuterClass
import commitment.CommitmentServiceGrpcKt.CommitmentServiceCoroutineStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.*
import java.util.concurrent.TimeUnit

class CommitmentServiceGrpcClient(private val channel: ManagedChannel) : Closeable {
    private val stub = CommitmentServiceCoroutineStub(channel)

    suspend fun sendCommittee(committee: CommitmentOuterClass.Committee) = coroutineScope {
        println("Sending Fabric agent user public keys to Ethereum client: $committee")
        val response = async { stub.sendCommittee(committee) }.await()
        println("Received Ack: $response")
        response
    }

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

fun sendCommitteeHelper(publicKeys: List<String>, config: Properties) = try {
    val client = CommitmentServiceGrpcClient(
            ManagedChannelBuilder.forAddress(
                    config["COMMITMENT_GRPC_SERVER_HOST"] as String,
                    (config["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt())
                    .usePlaintext()
                    .executor(Dispatchers.Default.asExecutor())
                    .build())
    val committeeBuilder = CommitmentOuterClass.Committee.newBuilder()
    publicKeys.map { committeeBuilder.addPublicKeys(it) }
    val committee = committeeBuilder.build()
    runBlocking {
        val ack = async { client.sendCommittee(committee) }.await()
        if (ack.status == CommitmentOuterClass.Ack.STATUS.OK) {
            Right(Unit)
        } else {
            println("Error sending management committee to Ethereum client: ${ack.message}\n")
            Left(Error("Error sending management committee to Ethereum client: ${ack.message}\n"))
        }
    }
} catch (e: Exception) {
    println("Error sending management committee to Ethereum client: ${e.message}\n")
    Left(Error("Error sending management committee to Ethereum client: ${e.message}\n"))
}

fun sendCommitmentHelper(accumulatorWrapper: AccumulatorWrapper, blockNum: Int, config: Properties) = try {
    val client = CommitmentServiceGrpcClient(
            ManagedChannelBuilder.forAddress(
                    config["COMMITMENT_GRPC_SERVER_HOST"] as String,
                    (config["COMMITMENT_GRPC_SERVER_PORT"] as String).toInt())
                    .usePlaintext()
                    .executor(Dispatchers.Default.asExecutor())
                    .build())
    val commitment = CommitmentOuterClass.Commitment.newBuilder()
            .setAccumulator(accumulatorWrapper.accumulator.a.toString())
            .setRollingHash(accumulatorWrapper.rollingHash)
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