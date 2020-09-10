package commitment.agent

import arrow.core.Either
import arrow.core.flatMap
import com.google.gson.Gson
import org.hyperledger.fabric.protos.common.Common
import org.starcoin.rsa.KVWrite
import org.starcoin.rsa.RSAAccumulator
import org.starcoin.rsa.kvWriteToBigInteger
import proof.ProofOuterClass
import proof.ProofOuterClass.Proof

data class AccumulatorManager(val accumulator: RSAAccumulator = RSAAccumulator()) {

        // Start - initialise accumulator and store it in the DB

        // Add - add a key and store new accumulator and data map in the DB

        // Delete - delete a key and store new accumulator and data map in the DB
}

fun initialiseAccumulator() {
    val accumulator = RSAAccumulator()
    val db = MapDb()
    val accumulatorJsonString = Gson().toJson(accumulator, RSAAccumulator::class.java)
    db.start(accumulatorJsonString)
}

fun updateAccumulator(block: Common.Block) {
    val db = MapDb()
    val accumulator = db.get(block.header.number.toInt()).map {
        Gson().fromJson(it, RSAAccumulator::class.java)
    }.map {
        // iterate through KVWrites in the block here.
        val state1 = KV("key1", "value1")
        val kv1 = KVWrite(
                key = "key1",
                isDelete = false,
                value = state1.toString().toByteArray()
        )
        val key1 = kvWriteToBigInteger(kv1)
        it.add(key1)
        val accumulatorJson = Gson().toJson(it, RSAAccumulator::class.java)
        db.update(block.header.number.toInt(), accumulatorJson)
    }
}

fun fakeUpdateAccumulator(blockHeight: Int) {
    val db = MapDb()
    val accumulator = db.get(blockHeight-1).map {
        Gson().fromJson(it, RSAAccumulator::class.java)
    }.map {
        val state0 = KV("key0", "value")
        val kv0 = KVWrite(
                key = "key0",
                isDelete = false,
                value = state0.toString().toByteArray()
        )
        val key0 = kvWriteToBigInteger(kv0)
        it.add(key0)

        val state1 = KV("key1", "value1")
        val kv1 = KVWrite(
                key = "key1",
                isDelete = false,
                value = state1.toString().toByteArray()
        )
        val key1 = kvWriteToBigInteger(kv1)
        it.add(key1)
        val accumulatorJson = Gson().toJson(it, RSAAccumulator::class.java)
        db.update(blockHeight, accumulatorJson)
    }
}

fun getState(key: String): String = "not yet implemented"

fun createProof(key: String, commitment: ProofOuterClass.Commitment): Either<Error, Proof> {
    val db = MapDb()
    val proof = db.get(commitment.blockHeight).map {
        Gson().fromJson(it, RSAAccumulator::class.java)
    }.flatMap { accumulator ->
        val state1 = KV("key1", "value1")
        val kv1 = KVWrite(
                key = "key1",
                isDelete = false,
                value = state1.toString().toByteArray()
        )
        val key1 = kvWriteToBigInteger(kv1)
        println("\nCreating proof for key $key1\n")

        accumulator.createProof(key1).map { proof ->
            println("accumulator created proof $proof")
            Proof.newBuilder()
                    // TODO: This should return a JSON string fo the KVWrite
                    .setState(key1.toString())
                    .setNonce(proof.nonce.toString())
                    .setProof(proof.proof.toString())
                    .setA(accumulator.a.toString())
                    .setN(proof.n.toString())
                    .build()
        }
    }
    println("Created proof: $proof")
    return proof
}

data class KV(val key: String, val value: String)