package commitment.agent.fabric.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.google.gson.Gson
import commitment.CommitmentOuterClass
import org.starcoin.rsa.RSAAccumulator
import org.starcoin.rsa.stringToHashBigInteger
import proof.ProofOuterClass.Proof

fun initialiseAccumulator(
        blockNum: Int,
        kvWrites: List<KvWrite>,
        orgName: String,
        seed1: Long,
        seed2: Long,
        seed3: Long
): Either<Error, String> = try {
    println("Initialising the accumulator for blockNum: $blockNum")
    val db = MapDb()

    // If this is the first block we need to initialise the accumulator
    val accumulator = RSAAccumulator(seed1, seed2, seed3)
    // Convert each of the KVWrites to a hash and add to the accumulator.
    // Note that if this is an empty list the accumulator doesn't get updated.
    val kvHash = kvWrites.map { kvWrite ->
        val jsonString = Gson().toJson(kvWrite, KvWrite::class.java)
        val kvHash = stringToHashBigInteger(jsonString)
        // WARNING: this mutates the accumulator
        accumulator.add(kvHash)
    }

    // Convert the accumulator to a JSON string to store in the DB
    val accumulatorJson = Gson().toJson(accumulator, RSAAccumulator::class.java)
        db.start(blockNum, accumulatorJson, orgName).map {
            accumulator.a.toString()
        }
} catch (e: Exception) {
    println("Accumulator Error: Error updating accumulator: ${e.stackTrace}")
    Left(Error("Accumulator Error: Error updating accumulator: ${e.message}"))
}

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
fun updateAccumulator(blockNum: Int, kvWrites: List<KvWrite>, orgName: String): Either<Error, String> = try {
    println("Updating accumulator for blockNum: $blockNum")
    val db = MapDb()

    val eeAccumulator = db.get(blockNum - 1, orgName).map {
            Gson().fromJson(it, RSAAccumulator::class.java)
        }
    eeAccumulator.flatMap { accumulator ->
        // Convert each of the KVWrites to a hash and add to the accumulator.
        // Note that if this is an empty list the accumulator doesn't get updated.
        val kvHash = kvWrites.map { kvWrite ->
            val jsonString = Gson().toJson(kvWrite, KvWrite::class.java)
            val kvHash = stringToHashBigInteger(jsonString)
            // WARNING: this mutates the accumulator
            accumulator.add(kvHash)
        }

        // Convert the accumulator to a JSON string to store back in the DB
        val accumulatorJson = Gson().toJson(accumulator, RSAAccumulator::class.java)
        db.update(blockNum, accumulatorJson, orgName).map {
            accumulator.a.toString()
        }
    }
} catch (e: Exception) {
    println("Accumulator Error: Error updating accumulator: ${e.message}")
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
fun createProof(
        key: String,
        ethCommitment: CommitmentOuterClass.Commitment,
        orgName: String
): Either<Error, Proof> {
    val db = MapDb()
    val proof = db.get(ethCommitment.blockHeight, orgName).map {
        Gson().fromJson(it, RSAAccumulator::class.java)
    }.flatMap { accumulator ->
        // Check that the accumulator stored in the DB at that block height matches the
        // accumulator the external client sent for the block height
        if (accumulator.a.toString().contains(ethCommitment.accumulator)) {
            val fabricClient = FabricClient(orgName)
            // TODO: Need to somehow relate this history with block height
            fabricClient.getStateHistory(key).flatMap { history ->
                // TODO: Until getting state at a block height is figured out, just assume the latest value is the required one.
                val latestKeyModification = history.first()
                // Recreate the kvWrite that was used in the accumulator
                val kvWrite = KvWrite(
                        key = key,
                        isDelete = latestKeyModification.isDelete,
                        value = latestKeyModification.value
                )
                val kvJson = Gson().toJson(kvWrite, KvWrite::class.java)
                val kvHash = stringToHashBigInteger(kvJson)
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
        } else {
            println("The accumulator provided by the external client does not match the stored accumulator for that block height.")
            Left(Error("The accumulator provided by the external client does not match the stored accumulator for that block height."))
        }
    }
    println("Created proof: $proof\n")
    return proof
}

data class KvWrite(val key: String, val value: String, val isDelete: Boolean)
