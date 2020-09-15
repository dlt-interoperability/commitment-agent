package commitment.agent.ethereum.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import commitment.agent.contracts.generated.LedgerState
import commitment.agent.contracts.generated.ManagementCommittee
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider
import java.math.BigInteger

class EthereumClient() {
    // This defaults to http://localhost:8545/
    val web3j = Web3j.build(HttpService())

    // This is the private key for the first account created in the
    // deterministic Ganache-CLI local Ethereum network
    val credentials = Credentials.create("0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d")
    val gasProvider = StaticGasProvider(BigInteger.valueOf(20000000000),BigInteger.valueOf(6721975))

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
        val lsInstance = LedgerState
                .deploy(web3j, credentials, gasProvider)
                .sendAsync()
                .get()

        val ledgerContractAddress = lsInstance.contractAddress

        println("LedgerState contract has been deployed to address $ledgerContractAddress\n")
        Right(ledgerContractAddress)
    } catch (e: Exception) {
        println("Ethereum Error: Error initializing ledger contract: ${e.message}\n")
        Left(Error("Ethereum Error: Error initializing ledger contract${e.message}"))
    }

    fun setManagementCommittee(
            lsContractAddress: String,
            fabricPublicKeys: List<String>,
            ethereumAccounts: List<String>
    ): Either<Error, TransactionReceipt> = try {
        val lsInstance = LedgerState.load(
                lsContractAddress,
                web3j,
                credentials,
                gasProvider)
        val mcContractAddress = lsInstance.committee().sendAsync().get()
        println("Management Committee contract address: ${mcContractAddress}")
        val mcInstance = ManagementCommittee.load(
                mcContractAddress,
                web3j,
                credentials,
                gasProvider)
        val txReceipt = mcInstance.setCommittee(fabricPublicKeys, ethereumAccounts).sendAsync().get()
        if (txReceipt.status == "0x1") {
            println("Successfully set the management commitee: $txReceipt")
            Right(txReceipt)
        } else {
            println("Ethereum Error: setCommittee transaction failed : ${txReceipt}")
            Left(Error("Ethereum Error: setCommittee transaction failed : ${txReceipt}"))
        }
    } catch (e: Exception) {
        println("Ethereum Error: Error setting management committee: ${e.message}")
        Left(Error("Ethereum Error: Error setting management committee: ${e.message}"))
    }

    fun setPolicy(ledgerContractAddress: String, quorum: Int): Either<Error, TransactionReceipt> = try {
        println("Setting policy of ledger contract to $quorum")
        val transactionReceipt = LedgerState.load(
                ledgerContractAddress,
                web3j,
                credentials,
                gasProvider)
                .setPolicy(BigInteger.valueOf(quorum.toLong()))
                .sendAsync()
                .get()
        println("Received receipt from Ethereum: $transactionReceipt\n")
        Right(transactionReceipt)
    } catch (e: Exception) {
        println("Ethereum Error: Error setting ledger state policy: ${e.message}")
        Left(Error("Ethereum Error: Error setting ledger state policy: ${e.message}"))
    }

}