.PHONY: build
build: ./scripts/get-solidity-contracts.sh
	./gradlew build

.PHONY: start
start: build
	./gradlew run

.PHONY: stop
stop: 
	rm wallet/admin.id wallet/agentUser.id