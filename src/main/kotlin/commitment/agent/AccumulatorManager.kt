package commitment.agent

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.google.gson.Gson
import org.starcoin.rsa.RSAAccumulator
import org.starcoin.rsa.stringToHashBigInteger
import proof.ProofOuterClass
import proof.ProofOuterClass.Proof

/**
 * The update accumulator function receives the list of all KVWrites that were
 * present in all the valid transactions in the block. It gets the previous version
 * of the accumulator for the previous block and adds all the KVWrites to it. To do
 * this the KVWrite is first converted to a JSON string, then to a hash using the
 * `stringToHashBigInteger` function. The `add` method of the RSA accumulator then
 * finds a prime representation of this hash by hashing it again together with an
 * appropriate nonce. The prime representation is used to update the accumulator.
 * The accumulator stores the original (non-prime) hash together with its nonce in
 * a `data` map so that the accumulated value (the prime representation) can later
 * be determined. Finally, the accumulator for the block is stored back in the DB as
 * a JSON string using the block number as the key.
 */
fun updateAccumulator(blockNum: Int, kvWrites: List<KvWrite>): Either<Error, Unit> = try {
    println("Updating accumulator for blockNum: $blockNum")
    val db = MapDb()

    val eeAccumulator = if (blockNum == 2) {
        // If this is the first block we need to initialise the accumulator
        Right(RSAAccumulator())
    } else {
        // Otherwise get the accumulator for the previous block from the DB
        db.get(blockNum - 1).map {
            Gson().fromJson(it, RSAAccumulator::class.java)
        }
    }
    eeAccumulator.flatMap { accumulator ->
        // Convert each of the KVWrites to a hash and add to the accumulator.
        // Note that if this is an empty list the accumulator doesn't get updated.
        val kvHash = kvWrites.map { kvWrite ->
            val jsonString = Gson().toJson(kvWrite, KvWrite::class.java)
            println("KvWrite to be stored in the accumulator: $jsonString")
            val kvHash = stringToHashBigInteger(jsonString)
            println("Hash representation of this KvWrite used as a key in the accumulator: $kvHash")
            // WARNING: this mutates the accumulator
            accumulator.add(kvHash)
        }

        // Convert the accumulator to a JSON string to store back in the DB
        val accumulatorJson = Gson().toJson(accumulator, RSAAccumulator::class.java)
        if (blockNum == 2) {
            db.start(blockNum, accumulatorJson)
        } else {
            db.update(blockNum, accumulatorJson)
        }
    }
} catch (e: Exception) {
    println("Accumulator Error: Error updating accumulator: ${e.stackTrace}")
    Left(Error("Accumulator Error: Error updating accumulator: ${e.message}"))
}

fun getState(key: String): String = "not yet implemented"

/**
 * The createProof is triggered by the gRPC server requestState function which is used
 * by the external client to get a state and proof of some Fabric ledger state at a particular
 * block height based on the key it was stored under in the ledger. To be able to create a
 * proof of membership, the KVWrite that was used in the accumulator needs to be recreated.
 * The state needs to be fetched from the Fabric ledger, then the hash representation of that
 * state is used as a key by the accumulator to create the proof.
 */
fun createProof(key: String, commitment: ProofOuterClass.Commitment): Either<Error, Proof> {
    val db = MapDb()
    val proof = db.get(commitment.blockHeight).map {
        Gson().fromJson(it, RSAAccumulator::class.java)
    }.flatMap { accumulator ->
        // TODO: Get the state from the Fabric ledger at the block height
        // This is a temporary workaround until the Fabric call to get state is done
        val keyNum = key.substringAfter("key")
        val kvWrite = KvWrite(
                key = key,
                isDelete = false,
                value = "value$keyNum"
        )
        val kvJson = Gson().toJson(kvWrite, KvWrite::class.java)
        println("KvWrite to find in the accumulator: $kvJson")
        val kvHash = stringToHashBigInteger(kvJson)
        println("Hash representation of this KvWrite used as a key in the accumulator: $kvHash")

        accumulator.createProof(kvHash).map { proof ->
            Proof.newBuilder()
                    .setState(kvJson)
                    .setNonce(proof.nonce.toString())
                    .setProof(proof.proof.toString())
                    .setA(accumulator.a.toString())
                    .setN(proof.n.toString())
                    .build()
        }
    }
    println("Created proof: $proof\n")
    return proof
}

data class KvWrite(val key: String, val value: String, val isDelete: Boolean)