.PHONY: start
start: ./scripts/get-solidity-contracts.sh
	./gradlew run