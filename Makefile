ORGNAME = ""

.PHONY: build
build:
	./scripts/get-solidity-contracts.sh
	./gradlew build installDist

.PHONY: start-fabric
start-fabric: build
	./fabric-client/build/install/fabric-client/bin/fabric-client $(ORGNAME)

.PHONY: start-ethereum
start-ethereum: build
	./ethereum-client/build/install/ethereum-client/bin/ethereum-client $(ORGNAME)

.PHONY: clean
clean:
	rm ethereum-client/src/main/solidity/*
	rm wallet/*
