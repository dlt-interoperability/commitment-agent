ORG = ""
INIT = ""
LC_ADDRESS = ""

.PHONY: build
build:
	./scripts/get-solidity-contracts.sh
	./gradlew build installDist

.PHONY: start-fabric
start-fabric: build
	./fabric-client/build/install/fabric-client/bin/fabric-client $(ORG) $(INIT)

.PHONY: start-ethereum
start-ethereum: build
	./ethereum-client/build/install/ethereum-client/bin/ethereum-client $(ORG) $(LC_ADDRESS)

.PHONY: clean
clean:
	rm ethereum-client/src/main/solidity/*
	rm wallet/*
