package commitment.agent

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import commitment.agent.contracts.generated.LedgerState
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.EthBlockNumber
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.DefaultGasProvider

class EthereumClient() {
    // This defaults to http://localhost:8545/
    val web3j = Web3j.build(HttpService())
    // This is the private key for the first account created in the
    // deterministic Ganache-CLI local Ethereum network
    val credentials = Credentials.create("0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d")
    val gasProvider = DefaultGasProvider()

    fun getCurrentBlockNumber(): Either<Error, Int> = try {
        val blockNumber = web3j.ethBlockNumber().send().blockNumber.intValueExact()
        println("Current block number is $blockNumber\n")
        Right(blockNumber)
    } catch (e: Exception) {
        println("Ethereum Error: Error getting current block number: ${e.stackTrace}")
        Left(Error("Ethereum Error: Error getting current block number: ${e.stackTrace}"))
    }

    // This produces an error: Error processing transaction request: Exceeds block gas limit
    fun deployLedgerContract() = LedgerState.deploy(web3j, credentials, gasProvider).send()
}