.PHONY: build
build: ./scripts/get-solidity-contracts.sh
	./gradlew build

.PHONY: start
start: build
	./gradlew run

.PHONY: clean
clean:
	rm wallet/admin.id wallet/agentUser.id