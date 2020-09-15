package commitment.agent.ethereum.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import commitment.agent.contracts.generated.LedgerState
import commitment.agent.contracts.generated.ManagementCommittee
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider
import java.io.FileInputStream
import java.math.BigInteger
import java.util.Properties

class LedgerStateManager() {
    // This defaults to http://localhost:8545/
    // TODO add this to config
    val web3j = Web3j.build(HttpService())
    val gasProvider = StaticGasProvider(BigInteger.valueOf(20000000000),BigInteger.valueOf(6721975))
    val properties = Properties()
    var credentials: Credentials

    init {
        FileInputStream("${System.getProperty("user.dir")}/ethereum-client/src/main/resources/config.properties")
                .use { properties.load(it) }
        // By default his is the private key of the first account created by the ganache-cli deterministic network
        val privateKey = (properties["ETHEREUM_PRIVATE_KEY"] as String)
        credentials = Credentials.create(privateKey)
        deployLedgerStateContract()
                .flatMap {  ledgerContractAddress ->
                    setManagementCommittee(ledgerContractAddress).map { ledgerContractAddress }
                }
                .flatMap { ledgerContractAddress ->
                    val quorum = (properties["POLICY_QUORUM"] as String).toInt()
                    setPolicy(ledgerContractAddress, quorum)
                }
    }

    fun getCurrentBlockNumber(): Either<Error, Int> = try {
        val blockNumber = web3j.ethBlockNumber().sendAsync().get().blockNumber.intValueExact()
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
        Right("$accountBalance")
    } catch (e: Exception) {
        println("Ethereum Error: Error deploying ledger contract: ${e.message}\n")
        Left(Error("Ethereum Error: Error deploying ledger contract${e.message}"))
    }

    fun deployLedgerStateContract(): Either<Error, String> = try {
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
            lsContractAddress: String
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

        // Get the Fabric agent public keys from the Fabric wallet
        getFabricAgentPublicKeys().flatMap {
            val ethereumAccounts = (properties["ETHEREUM_ACCOUNTS"] as String)
                    .split(",")
                    .subList(0, it.size)
            // Submit the setCommittee transaction
            val txReceipt = mcInstance.setCommittee(it, ethereumAccounts).sendAsync().get()
            // A status of "0x1 indicates a successful transaction
            if (txReceipt.status == "0x1") {
                println("Successfully set the management commitee: $txReceipt")
                Right(txReceipt)
            } else {
                println("Ethereum Error: setCommittee transaction failed : $txReceipt")
                Left(Error("Ethereum Error: setCommittee transaction failed : $txReceipt"))
            }
        }
    } catch (e: Exception) {
        println("Ethereum Error: Error setting management committee: ${e.message}")
        Left(Error("Ethereum Error: Error setting management committee: ${e.message}"))
    }

    /**
     * The policy defines how many Fabric agents need to vote on a commitment in order for it to be activated.
     */
    fun setPolicy(ledgerContractAddress: String, quorum: Int): Either<Error, TransactionReceipt> = try {
        println("Setting policy of ledger contract to $quorum")
        val txReceipt = LedgerState.load(
                ledgerContractAddress,
                web3j,
                credentials,
                gasProvider)
                .setPolicy(BigInteger.valueOf(quorum.toLong()))
                .sendAsync()
                .get()
        // A status of "0x1 indicates a successful transaction
        if (txReceipt.status == "0x1") {
            println("Successfully set the policy: $txReceipt")
            Right(txReceipt)
        } else {
            println("Ethereum Error: setPolicy transaction failed : ${txReceipt}")
            Left(Error("Ethereum Error: setPolicy transaction failed : ${txReceipt}"))
        }
    } catch (e: Exception) {
        println("Ethereum Error: Error setting ledger state policy: ${e.message}")
        Left(Error("Ethereum Error: Error setting ledger state policy: ${e.message}"))
    }
}