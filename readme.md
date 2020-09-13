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
**Change line 21 in the `build.gradle` to point to your local Maven repository directory.**

### Update the config properties

Update the config file in `/src/main/resources/config.properties` to point to
the correct location of the network connection profile for the Fabric network and
the CA certificate.

The driver port can also be changed from this file if needed.

### Clone the bulletin board repo

This project copies the solidity smart contracts defined in the [bulletin
board](https://github.com/dlt-interoperability/bulletin-board) repo and
generates Java wrapper files from them. Ensure the bulletin board project is
present at the same level as the directory structure as the commitment-agent
project.

### Start the Fabric network

Please use the example [Fabric
network](https://github.com/dlt-interoperability/fabric-network) that comes
complete with chaincode and application. Start the Fabric network and deploy and
invoke the chaincode with:

```
make start
make deploy-cc
make invoke-cc
```

The `invoke-cc` make target starts a Fabric node.js application that submits
`CreateAsset` transactions every 10 seconds. This can be cancelled with
`ctrl-c`. The `make invoke-cc` can be used repeatedly without needing to
restart the network.

## Start the Fabric agent

```
./gradlew run
```

**Note on restarting the agent**: If the Fabric network is stopped and started, the user and admin credentials for
the agent need to be deleted so they can be reissued by the Fabric network CA.

```
rm wallet/admin.id wallet/agentUser.id
```

### Making queries from the external client

The [external client](https://github.com/dlt-interoperability/external-client)
is a command line application that can make requests to the agent. To do so,
clone the repo, update path to local Maven repository, and run:

```
./gradlew installDist
./build/install/external-client/bin/external-client get-proof key1 7
```

Note that the last argument is the block height that the client wishes to
retrieve a state and proof for. The requirement to include this block height is
a temporary workaround while the external client is making a dummy commitment
instead of getting it from Ethereum.

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

- We will need an agent running for every peer, so FabricClient will need to be
  parameterised.

RSA Accumulators

- Add a config file to store path to local Maven repository for the
  `build.gradle` file.
- Update function should trigger the Ethereum publication function every _k_
  blocks (with signature).
- Function to get state based on key and block height from the peer.
- Figure out how the RSA accumulator can be initialised deterministically so it
  will be the same across all agents.

Ethereum Client

- Import Web3J.
- Implement functions in the smart contract interface to publish accumulator.
