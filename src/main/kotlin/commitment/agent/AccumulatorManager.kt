package commitment.agent

import proof.ProofOuterClass.Proof

fun createProof(key: String): Proof = Proof.newBuilder()
        .setState("test")
        .setN("test")
        .setNonce("test")
        .setProof("test")
        .build()