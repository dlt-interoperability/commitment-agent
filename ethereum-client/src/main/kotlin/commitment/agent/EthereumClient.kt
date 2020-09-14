package commitment.agent.ethereum.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import commitment.agent.contracts.generated.LedgerState
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

class EthereumClient() {
    // This defaults to http://localhost:8545/
    val web3j = Web3j.build(HttpService())

    // This is the private key for the first account created in the
    // deterministic Ganache-CLI local Ethereum network
    val credentials = Credentials.create("0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d")

    fun getCurrentBlockNumber(): Either<Error, Int> = try {
        val blockNumber = web3j.ethBlockNumber().sendAsync().get().blockNumber.intValueExact()
        println("Current block number is $blockNumber\n")
        Right(blockNumber)
    } catch (e: Exception) {
        println("Ethereum Error: Error getting current block number: ${e.message}")
        Left(Error("Ethereum Error: Error getting current block number: ${e.message}"))
    }

    fun getAccountBalance(accountAddress: String): Either<Error, String> = try {
        val accountBalance = web3j
                .ethGetBalance(accountAddress, DefaultBlockParameter.valueOf("latest"))
                .sendAsync()
                .get()
                .balance
        println("Account balance: $accountBalance")
        Right("$accountBalance")
    } catch (e: Exception) {
        println("Ethereum Error: Error deploying ledger contract: ${e.message}\n")
        Left(Error("Ethereum Error: Error deploying ledger contract${e.message}"))
    }

    fun deployLedgerContract(): Either<Error, String> = try {
        val ledgerContractGasProvider = LedgerContractGasProvider()
        println("Gas price: ${ledgerContractGasProvider.gasPrice}")
        println("Gas limit: ${ledgerContractGasProvider.gasLimit}")
        val ledgerContractAddress = LedgerState
                .deploy(web3j, credentials, ledgerContractGasProvider)
                .sendAsync()
                .get()
                .contractAddress
        println("LedgerState contract has been deployed to address $ledgerContractAddress\n")
        Right(ledgerContractAddress)
    } catch (e: Exception) {
        println("Ethereum Error: Error deploying ledger contract: ${e.message}\n")
        Left(Error("Ethereum Error: Error deploying ledger contract${e.message}"))
    }

    fun deployManagementCommitteeContract(): Either<Error, String> = try {
        val managementCommitteeContractGasProvider = LedgerContractGasProvider()
        println("Gas price: ${managementCommitteeContractGasProvider.gasPrice}")
        println("Gas limit: ${managementCommitteeContractGasProvider.gasLimit}")
        val managementCommitteeContractAddress = LedgerState
                .deploy(web3j, credentials, managementCommitteeContractGasProvider)
                .sendAsync()
                .get()
                .contractAddress
        println("Management committee contract has been deployed to address $managementCommitteeContractAddress\n")
        Right(managementCommitteeContractAddress)
    } catch (e: Exception) {
        println("Ethereum Error: Error deploying management committee contract: ${e.message}\n")
        Left(Error("Ethereum Error: Error deploying management committee contract${e.message}"))
    }
}