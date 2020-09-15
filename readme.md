# State Commitment Agent

The state commitment agent is used to maintain a commitment of the entire state
of a Fabric ledger in an RSA accumulator. The accumulator is periodically
published to a public bulletin board, the Ethereum mainnet. The agent can be
queried by an external agent to retrieve state and a proof of membership of that
state in the accumulator.

This project has two modules:

1. The Fabric client is a process that subscribes to block events coming from the Fabric
   peer. It also maintains the accumulator and runs a gRPC server for the
   external client to make requests to.
2. The Ethereum client runs as a separate process and connects to the Ethereum
   network to publish commitments. It runs a gRPC server to receive commitments
   from the Fabric process.

## Prerequisites

### Build the RSA accumulator library and publish to MavenLocal

The commitment agent uses the
[rsa-accumulator-kotlin](https://github.com/dlt-interoperability/rsa-accumulator-kotlin)
library to maintain an RSA accumulator of the entire state of the Fabric ledger.
This repository needs to be cloned, built, and published to a local Maven
repository. Follow instructions in the repo to do this. **Change line 21 in the
`build.gradle` to point to your local Maven repository directory.**

### Update the config properties

Update the config file in `fabric-client/src/main/resources/config.properties` to point to
the correct location of the network connection profile for the Fabric network and
the CA certificate.

The state proof service and commitment service gRPC ports can also be changed from this file if needed.

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

In two separate terminal panes, run:

```
make start-ethereum
make start-fabric
```

**Note on restarting the agent**: If the Fabric network is stopped and started,
the user and admin credentials for the agent need to be deleted so they can be
reissued by the Fabric network CA. This can be done with:

```
make clean
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

Conventions:

- Use immutable state.
- Catch exceptions as close as possible to their source and convert to [Arrow's
  `Either`
  type](https://arrow-kt.io/docs/apidocs/arrow-core-data/arrow.core/-either/).
- Implement functions as expressions. Flows that produce errors can be composed
  using `map`, `flatMap` and `fold`. Avoid statements with side effects in functions.
- Use recursion over loops (when tail recursion is possible to improve performance and avoid stack overflow).

Example Gists:

- [How to catch exceptions and convert to and Either type](https://gist.github.com/airvin/79f1fb2a3821a9e5d227db3ee9561f42).
- [Using flatMap to compose functions that return Eithers](https://gist.github.com/airvin/3bfae1f3e622e466ba9072b53684555a).
- [Folding over an Either Error to reduce to a single type](https://gist.github.com/airvin/eabc99a9552a0573afd2dd9a13e75948).

## TODO

Fabric

- We will need an agent running for every peer, so FabricClient will need to be
  parameterised.

RSA Accumulators

- Add a config file to store path to local Maven repository for the
  `build.gradle` file.
- Update function should trigger the Ethereum publication function every _k_
  blocks (with signature). Currently, it is triggering publication on every
  block.
- Function to get state based on key and block height from the peer.
- Figure out how the RSA accumulator can be initialised deterministically so it
  will be the same across all agents.

Ethereum Client

- Fix the type of the commitment on the bulletin board to fit the entire commitment
