# State Commitment Agent

The state commitment agent is used to maintain a commitment of the entire state
of a Fabric ledger in an RSA accumulator. The accumulator is periodically
published to a public bulletin board, the Ethereum mainnet. The agent can be
queried by an external agent to retrieve state and a proof of membership of that
state in the accumulator.

## Prerequisites

### Build the RSA accumulator library and publish to MavenLocal

The external client uses the
[rsa-accumulator-kotlin](https://github.com/dlt-interoperability/rsa-accumulator-kotlin)
library to verify membership proofs provided by the Fabric agent. This
repository needs to be cloned, built, and published to a local Maven repository.
Follow instructions in the repo to do this.
**Change line 20 in the `build.gradle` to point to your local Maven repository directory.**

### Start the Fabric network

Before running the agent, start up a Fabric network. The recommended network is
the
[test-network](https://github.com/hyperledger/fabric-samples/tree/master/test-network)
in the fabric-samples repository. It is also recommended to use images for
Fabric v2.2. After cloning or pulling the latest version of the fabric-samples repository,
run the following from the test-network directory:

```
./network.sh up createChannel -c mychannel -ca
./network.sh deployCC -ccn basic -ccl javascript
```

### Update the config properties

Update the config file in `/src/main/resources/config.properties` to point to
the correct location of the network connection profile for the test network and
the CA certificate.

The driver port can also be changed from this file if needed.

### Running the agent

The agent can be run with:

```
./gradlew run
```

### Making queries from the external client

The [external client](https://github.com/dlt-interoperability/external-client)
is a command line application that can make requests to the agent. To do so,
clone the repo, update path to local Maven repository, and run:

```
./gradlew installDist
./build/install/external-client/bin/external-client get-proof key1
```

### Troubleshooting gRPC

[BloomRPC](https://github.com/uw-labs/bloomrpc) can be used if the gRPC server
needs to be tested from a dummy client. BloomRPC is like Postman for gRPC.

## Coding principles

This codebase uses functional programming principles as much as possible. A
functional library for Kotlin, called [Arrow](https://arrow-kt.io/docs/core/) is
used, primarily for error handling with the `Either` type.

Conventions

- Use immutable state.
- Catch exceptions as close as possible to their source and convert to [Arrow's
  `Either`
  type](https://arrow-kt.io/docs/apidocs/arrow-core-data/arrow.core/-either/).
- Implement functions as expressions. Flows that produce errors can be composed
  using `map`, `flatMap` and `fold`. Avoid statements with side effects in functions.
- Use recursion over loops (when tail recursion is possible to avoid stack overflow).

An example of how to catch exceptions and convert to and Either type is shown in
[this gist](https://gist.github.com/airvin/79f1fb2a3821a9e5d227db3ee9561f42).

An example of folding over an Either Error to reduce to a single type is
demonstrated in [this
gist](https://gist.github.com/airvin/eabc99a9552a0573afd2dd9a13e75948).

## TODO

Fabric

- Possibly use a different Fabric test network and version of chaincode. Fabric
  samples test network is currently being used and it is quite bulky.
- We will need an agent running for every peer, so FabricClient will need to be
  parameterised.
- Add an BlockEvent listener.

RSA Accumulator

- Add a config file to store path to local Maven repository for the
  `build.gradle` file.
- Expose function that will be triggered by the Fabric block event listener to
  update RSA accumulator after every block is received.
- Update function should trigger the Ethereum publication function every _k_
  blocks (with signature).
- Decide how Fabric state will be stored. The Kotlin RSA library is creating a
  nonce for every state added to the accumulator to make it a prime. A map of
  the state and prime is stored in memory, but we should use persistent storage
  (either [jankotek/mapdb](https://github.com/jankotek/mapdb),
  [JetBrains/xodus](https://github.com/JetBrains/xodus) or
  [JetBrains/Exposed](https://github.com/JetBrains/Exposed)).
- Expose function to get state based on key.
- Expose function to create a membership proof for the key.
- Use Gson (or Moshi) to JSON stringify the KV that is stored in accumulator and
  is returned in the proof.
- Figure out how the RSA accumulator can be initialised deterministically so it
  will be the same across all agents.

Ethereum Client

- Import Web3J.
- Implement functions in the smart contract interface to publish accumulator.
