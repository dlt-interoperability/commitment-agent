# State Commitment Agent

The state commitment agent is used to maintain a commitment of the entire state
of a Fabric ledger in an RSA accumulator. The accumulator is periodically
published to a public bulletin board, the Ethereum mainnet. The agent can be
queried by an external agent to retrieve state and a proof of membership of that
state in the accumulator.

## Prerequisites

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

## TODO

Fabric

- Use a different version of chaincode?
- We will need an agent running for every peer, so FabricClient will need to be
  parameterised
- Add an BlockEvent listener

RSA Accumulator

- Import RSA accumulator library.
- Expose function that will be triggered by the Fabric block event listener to
  update RSA accumulator after every block is received.
- Update function should trigger the Ethereum publication function every _k_
  blocks (with signature).
- Decide how Fabric state will be stored. The Kotlin RSA library is creating a
  nonce for every state added to the accumulator to make it a prime. A map of the state and
  prime is stored in memory, but should we use persistent storage for this?
- Expose function to get state based on key.
- Expose function to create a membership proof for the key.

Ethereum Client

- Import Web3J.
- Implement functions in the smart contract interface to publish accumulator.
