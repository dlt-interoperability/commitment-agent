package commitment.agent

import arrow.core.Either
import org.apache.milagro.amcl.RSA2048.RSA
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset
import org.starcoin.rsa.KVWrite
import org.starcoin.rsa.RSAAccumulator
import org.starcoin.rsa.kvWriteToBigInteger
import proof.ProofOuterClass.Proof

data class AccumulatorManager(val accumulator: RSAAccumulator = RSAAccumulator()) {

        // Start - initialise accumulator and store it in the DB

        // Add - add a key and store new accumulator and data map in the DB

        // Delete - delete a key and store new accumulator and data map in the DB
}

fun initialiseAccumulator(): RSAAccumulator {
    val accumulator = RSAAccumulator()
    val state0 = KV("key0", "value0")
    val kv0 = KVWrite(
            key = "key0",
            isDelete = false,
            value = state0.toString().toByteArray()

    )
    accumulator.add(kvWriteToBigInteger(kv0))

    val state1 = KV("key1", "value1")
    val kv1 = KVWrite(
            key = "key1",
            isDelete = false,
            value = state1.toString().toByteArray()
    )
    val key1 = kvWriteToBigInteger(kv1)
    accumulator.add(key1)
    println("Accumulator after adding key1: ${accumulator.a}")
    return accumulator
}

fun getState(key: String): String = "not yet implemented"

fun createProof(key: String, accumulator: RSAAccumulator): Either<Error, Proof> {
    val state1 = KV("key1", "value1")
    val kv1 = KVWrite(
            key = "key1",
            isDelete = false,
            value = state1.toString().toByteArray()
    )
    val key1 = kvWriteToBigInteger(kv1)

    return accumulator.createProof(key1).map { proof ->
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

data class KV(val key: String, val value: String)