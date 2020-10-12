package commitment.agent.fabric.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import arrow.mtl.run
import com.google.gson.Gson
import commitment.CommitmentOuterClass
import proof.ProofOuterClass.Proof
import res.dlt.accumulator.RSAAccumulator
import res.dlt.accumulator.add
import res.dlt.accumulator.delete
import res.dlt.accumulator.hashStringToBigInt
import res.dlt.accumulator.isMember
import java.security.MessageDigest
import java.util.Base64

fun initialiseAccumulator(
        blockNum: Int,
        orgName: String,
        seed1: Long,
        seed2: Long,
        seed3: Long
): Either<Error, AccumulatorWrapper> = try {
    println("Initialising the accumulator for blockNum: $blockNum")
    val db = MapDb()

    // Create an accumulator
    val accumulator = RSAAccumulator.newInstance(seed1, seed2, seed3)

    // Initialise the rolling hash for the accumulator
    val digest = MessageDigest.getInstance("SHA-256").digest(accumulator.a.toByteArray())
    val rollingHash = Base64.getEncoder().encodeToString(digest)
    println("Rolling hash: $rollingHash")

    // Convert the accumulator + hash wrapper type to a JSON string and store in the DB
    val accumulatorWrapper = AccumulatorWrapper(accumulator, rollingHash)
    val accumulatorWrapperJson = Gson().toJson(accumulatorWrapper, AccumulatorWrapper::class.java)
    db.start(blockNum, accumulatorWrapperJson, orgName).map { accumulatorWrapper }
} catch (e: Exception) {
    println("Accumulator Error: Error initialising accumulator: ${e.message}")
    Left(Error("Accumulator Error: Error initialising accumulator: ${e.message}"))
}

/**
 * The update accumulator function receives the list of all KVWrites that were
 * present in all the valid transactions in the block. It gets the previous version
 * of the accumulator for the previous block and adds all the KVWrites to it. To do
 * this the KVWrite is first converted to a JSON string, then to a hash using the
 * `hashStringToBigInt` function. The `add` method of the RSA accumulator then
 * finds a prime representation of this hash by hashing it again together with an
 * appropriate nonce. The prime representation is used to update the accumulator.
 * The accumulator stores the original (non-prime) hash together with its nonce in
 * a `data` map so that the accumulated value (the prime representation) can later
 * be determined. Finally, the accumulator for the block is stored back in the DB as
 * a JSON string using the block number as the key.
 */
fun updateAccumulator(
        blockNum: Int,
        kvWrites: List<KvWrite>,
        orgName: String
): Either<Error, AccumulatorWrapper> = try {
    println("Updating accumulator for blockNum: $blockNum")
    val db = MapDb()

    db.get(blockNum - 1, orgName).map {
            Gson().fromJson(it, AccumulatorWrapper::class.java)
        }.flatMap { accumulatorWrapper ->
        val accumulator = accumulatorWrapper.accumulator
        // Convert each of the KVWrites to a hash and add to the accumulator.
        // Note that if this is an empty list the accumulator doesn't get updated.
        val finalAccumulator = kvWrites.fold(accumulator, { acc, kvWrite ->
            val jsonString = Gson().toJson(kvWrite, KvWrite::class.java)
            val kvHash = hashStringToBigInt(jsonString)
            if (kvWrite.isDelete) {
                delete(kvHash).run(acc).a
            } else {
                add(kvHash).run(acc).a
            }
        })

        // Update the rolling hash for the accumulator
        val digest = MessageDigest
                .getInstance("SHA-256")
                .digest((accumulatorWrapper.rollingHash + finalAccumulator.a).toByteArray())
        val rollingHash = Base64.getEncoder().encodeToString(digest)
        println("Rolling hash: $rollingHash")

        // Convert the accumulator + hash wrapper type to a JSON string and  store in the DB
        val newAccumulatorWrapper = AccumulatorWrapper(finalAccumulator, rollingHash)
        val accumulatorWrapperJson = Gson().toJson(newAccumulatorWrapper, AccumulatorWrapper::class.java)
        db.update(blockNum, accumulatorWrapperJson, orgName).map { newAccumulatorWrapper }
    }
} catch (e: Exception) {
    println("Accumulator Error: Error updating accumulator: ${e.message}")
    Left(Error("Accumulator Error: Error updating accumulator: ${e.message}"))
}

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
): Either<Error, Proof> =
    MapDb().get(ethCommitment.blockHeight, orgName).map {
        Gson().fromJson(it, AccumulatorWrapper::class.java)
    }.flatMap { accumulatorWrapper ->
        val accumulator = accumulatorWrapper.accumulator
        // Check that the accumulator stored in the DB at that block height matches the
        // accumulator the external client sent for the block height
        if (accumulator.a.toString().contains(ethCommitment.accumulator)) {
            val fabricClient = FabricClient(orgName)
            fabricClient.connect().flatMap { (_, contract) ->
                fabricClient.getStateHistory(key, contract)
            }.flatMap { history ->
                // Find the first state in the history that is present in the accumulator.
                val keyModification = history.find { isStateInAccumulator(key, it, accumulator) }
                if (keyModification != null) {
                    // Recreate the kvWrite that was used in the accumulator
                    val kvWrite = KvWrite(
                            key = key,
                            isDelete = keyModification.isDelete,
                            value = keyModification.value
                    )
                    val kvJson = Gson().toJson(kvWrite, KvWrite::class.java)
                    val kvHash = hashStringToBigInt(kvJson)
                    val proof = res.dlt.accumulator.createProof(kvHash).run(accumulator).b.map { proof ->
                        Proof.newBuilder()
                                .setState(kvJson)
                                .setProof(proof.proof.toString())
                                .setA(accumulator.a.toString())
                                .setN(proof.n.toString())
                                .build()
                    }
                    println("Created proof: $proof\n")
                    proof
                } else {
                    println("Request Error: No state with that key was found.\n")
                    Left(Error("Request Error: No state with that key was found."))
                }
            }
        } else {
            println("The accumulator provided by the external client does not match the stored accumulator for that block height.")
            Left(Error("The accumulator provided by the external client does not match the stored accumulator for that block height."))
        }
    }

fun isStateInAccumulator(key: String, keyModification: KeyModification, accumulator: RSAAccumulator): Boolean {
    // Recreate the kvWrite that was used in the accumulator
    val kvWrite = KvWrite(
            key = key,
            isDelete = keyModification.isDelete,
            value = keyModification.value
    )
    val kvJson = Gson().toJson(kvWrite, KvWrite::class.java)
    val kvHash = hashStringToBigInt(kvJson)
    return isMember(kvHash).run(accumulator).b
}

data class KvWrite(val key: String, val value: String, val isDelete: Boolean)
data class AccumulatorWrapper(val accumulator: RSAAccumulator, val rollingHash: String)