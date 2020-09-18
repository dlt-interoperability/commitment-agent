package commitment.agent.ethereum.client

import arrow.core.*
import commitment.agent.contracts.generated.LedgerState
import commitment.agent.contracts.generated.ManagementCommittee
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider
import java.io.FileInputStream
import java.math.BigInteger
import java.util.Properties

class LedgerStateManager(var ledgerContractAddress: Option<String>) {
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
        if (ledgerContractAddress.isEmpty()) {
            ledgerContractAddress = deployLedgerStateContract()
                    .flatMap { ledgerContractAddress ->
                        val quorum = (properties["POLICY_QUORUM"] as String).toInt()
                        setPolicy(ledgerContractAddress, quorum).map { ledgerContractAddress }
                    }.fold({ None }, { Some(it) })
        }
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

    fun setManagementCommittee(publicKeys: List<String>): Either<Error, TransactionReceipt> = try {
        println("Submitting management committee with public keys: $publicKeys")
        ledgerContractAddress.fold({
            Left(Error("Ethereum Error: Ledger contract failed to initiate"))
        }, { lcAddress ->
            val lcInstance = LedgerState.load(
                    lcAddress,
                    web3j,
                    credentials,
                    gasProvider)
            // Get the corresponding Ethereum accounts for the public keys
            val ethereumAccounts = (properties["ETHEREUM_ACCOUNTS"] as String)
                    .split(",")
                    .subList(0, publicKeys.size)
            println("Fabric public keys: $publicKeys")
            println("Ethereum accounts to be linked with Fabric public keys: $ethereumAccounts")
            // Create the management committee instance
            val mcAddress = lcInstance.committee().sendAsync().get()
            val mcInstance = ManagementCommittee.load(
                    mcAddress,
                    web3j,
                    credentials,
                    gasProvider)
            // Submit the setCommittee transaction
            val txReceipt = mcInstance.setCommittee(publicKeys, ethereumAccounts).sendAsync().get()
            // A status of "0x1 indicates a successful transaction
            if (txReceipt.status == "0x1") {
                println("Successfully set the management commitee: $txReceipt")
                Right(txReceipt)
            } else {
                println("Ethereum Error: setCommittee transaction failed : $txReceipt")
                Left(Error("Ethereum Error: setCommittee transaction failed : $txReceipt"))
            }
        })
    } catch (e: Exception) {
        println("Ethereum Error: Error setting management committee: ${e.message}\n")
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
            println("Ethereum Error: setPolicy transaction failed : ${txReceipt}\n")
            Left(Error("Ethereum Error: setPolicy transaction failed : ${txReceipt}"))
        }
    } catch (e: Exception) {
        println("Ethereum Error: Error setting ledger state policy: ${e.message}\n")
        Left(Error("Ethereum Error: Error setting ledger state policy: ${e.message}"))
    }

    fun postCommitment(commitment: String, blockHeight: Int): Either<Error, TransactionReceipt> = try {
        println("Submitting commitment for block height $blockHeight")
        ledgerContractAddress.fold({
            Left(Error("Ethereum Error: Ledger contract failed to initiate"))
        }, {
            val ledgerState = LedgerState.load(
                    it,
                    web3j,
                    credentials,
                    gasProvider)
            val commitmentByteArray = stringToBytes32ByteArray(commitment)
            commitmentByteArray.flatMap { byteArray ->
                val txReceipt = ledgerState
                        .postCommitment(byteArray, BigInteger.valueOf(blockHeight.toLong()))
                        .sendAsync()
                        .get()
                // A status of "0x1 indicates a successful transaction
                if (txReceipt.status == "0x1") {
                    println("Successfully submitted the commitment: $txReceipt")
                    println("Ledger state contract address is $ledgerContractAddress\n")
                    Right(txReceipt)
                } else {
                    println("Ethereum Error: submitting the commitment failed: ${txReceipt}\n")
                    Left(Error("Ethereum Error: submitting the commitment failed: ${txReceipt}"))
                }
            }
        })
    } catch (e: Exception) {
        println("Ethereum Error: Error posting commitment: ${e.message}\n")
        Left(Error("Ethereum Error: Error posting commitment: ${e.message}"))
    }
}