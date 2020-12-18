## Running performance experiments on a VM

### List of components and their repositories:

- RSA accumulator:
  `/opt/gopath/src/github.com/dlt-interoperability/rsa-accumulator`
- Fabric network:
  `/opt/gopath/src/github.com/dlt-interoperability/fabric-network`
- Fabric smallbank testnet:
  `/opt/gopath/src/github.com/dlt-interoperability/network-setups/dev/fabric`
- Fabric Trade testnet: `/opt/gopath/src/github.com/HyperledgerHandsOn/trade-network/bash`
- Bulletin Board:
  `/opt/gopath/src/github.com/dlt-interoperability/bulletin-board`
- Ethereum client:
  `/opt/gopath/src/github.com/dlt-interoperability/commitment-agent`
- Fabric client:
  `/opt/gopath/src/github.com/dlt-interoperability/commitment-agent`
- External client:
  `/opt/gopath/src/github.com/dlt-interoperability/external-client`
- Caliper: `/opt/gopath/src/github.com/hyperledger/caliper-benchmarks`

### VM Setup

A VM has been set up to run performance analysis of the commitment agent at `fc-2020-currency-test-1.sl.cloud9.ibm.com`. 
It has the required components set up in panes in a tmux session. 
To attach to this session use `tmux attach`. Each of the repositories is in a separate window, which are visible in the bottom row of the tmux session.
To switch between windows use `ctrl-a <window number>`. 
Some windows have multiple panes for when multiple processes need to be run simultaneously. 
To move between panes use `ctrl-h, ctrl-j, ctrl-k, ctrl-l` for left, down, up, right, respectively. 
To create a new window use `ctrl-a c` and then to rename it use `ctrl-a ,`.
To create a new pane with a vertical split use `ctrl-a |` or `ctrl-a \`. 
To create a new pane with a horizontal split use `ctrl-a -`.

### Pre-requisite for all scenarios:

RSA accumulator: `./gradlew build publishToMavenLocal`

### Simple Fabric network with one agent

- Fabric network: `make start`
- Bulletin board: `npx ganache-cli --deterministic`
- Ethereum client: `make start-ethereum ORG="org1"`
- Fabric client: `make start-fabric ORG="org1" INIT="true" PRIMARY_ORG="true"`
- Fabric client: `make start-fabric ORG="org1"`
- Fabric network: `make deploy-cc`
- Fabric network: `make invoke-cc`
- External client: `./gradlew installDist`
- External client: `./build/install/external-client/bin/external-client get-proof key1 0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab org1`

### Simple Fabric network with two agents

- Fabric network: `make start`
- Bulletin board: `npx ganache-cli --deterministic`
- Ethereum client: `make start-ethereum ORG="org1"`
- Ethereum client: `make start-ethereum ORG="org2" LC_ADDRESS="0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab"`
- Fabric client: `make start-fabric ORG="org1" INIT="true" PRIMARY_ORG="true"`
- Fabric client: `make start-fabric ORG="org1"`
- Fabric network: `make deploy-cc`
- Fabric network: `make invoke-cc`
- External client: `./gradlew installDist`
- External client: `./build/install/external-client/bin/external-client get-proof key1 0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab org1`

### Smallbank Fabric network with one agent

- Fabric smallbank testnet (network-setups): `make start-network1`
- Bulletin board: `npx ganache-cli --deterministic`
- Ethereum client: `make start-ethereum ORG="org1"`
- Fabric client: `make start-fabric ORG="testnet" INIT="true" PRIMARY_ORG="true"`
- Fabric client: `make start-fabric ORG="testnet"`
- Caliper: `npx caliper launch master --caliper-workspace . --caliper-benchconfig benchmarks/scenario/smallbank/config.yaml --caliper-networkconfig networks/fabric/v2/v2.1.0/testnet/testnet.yaml --caliper-flow-only-test --caliper-fabric-gateway-usegateway`
- External client: `./gradlew installDist`
- External client: `./build/install/external-client/bin/external-client get-proof <copy-key-from-fabric-client-here> 0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab testnet`

### Trade Fabric network with two agents

- Fabric trade testnet: `./trade.sh up -m prod`
- Fabric trade testnet: `./startAndJoinChannels.sh`
- Fabric trade testnet: `./trade.sh installcontract -c tradechannel -p trade -o 3`
- Fabric trade testnet: `./trade.sh initcontract -c tradechannel -p trade -t init`
- Bulletin board: `npx ganache-cli --deterministic`
- Ethereum client: `make start-ethereum ORG="tradeimp"`
- Ethereum client: `make start-ethereum ORG="tradeexp" LC_ADDRESS="0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab"`
- Ethereum client: `make start-ethereum ORG="tradereg" LC_ADDRESS="0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab"`
- Fabric client: `make start-fabric ORG="tradeimp" INIT="true"`
- Fabric client: `make start-fabric ORG="tradeexp" INIT="true"`
- Fabric client: `make start-fabric ORG="tradereg" INIT="true" PRIMARY_ORG="true"`
- Fabric client: `make start-fabric ORG="tradeimp"`
- Fabric client: `make start-fabric ORG="tradeexp"`
- Fabric client: `make start-fabric ORG="tradereg"`
- Caliper: `npx caliper launch master --caliper-workspace . --caliper-benchconfig benchmarks/samples/fabric/trade/config.yaml --caliper-networkconfig networks/fabric/v2/v2.1.0/trade/trade.yaml --caliper-flow-only-test --caliper-fabric-gateway-usegateway`
- External client: `./gradlew installDist`
- External client: `./build/install/external-client/bin/external-client get-proof <copy-key-from-fabric-client-here> 0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab tradeexp`
