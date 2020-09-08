package commitment.agent

import proof.ProofOuterClass.Proof

fun getState(key: String): String = "not yet implemented"

fun createProof(key: String): Proof = Proof.newBuilder()
        .setState("test")
        .setN("test")
        .setNonce("test")
        .setProof("test")
        .build()