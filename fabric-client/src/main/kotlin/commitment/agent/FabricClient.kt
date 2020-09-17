package commitment.agent.fabric.client

import arrow.core.*
import com.google.gson.Gson
import org.hyperledger.fabric.gateway.*
import org.hyperledger.fabric.sdk.BlockEvent
import org.hyperledger.fabric.sdk.Enrollment
import org.hyperledger.fabric.sdk.User
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest
import org.hyperledger.fabric_ca.sdk.HFCAClient
import org.hyperledger.fabric_ca.sdk.RegistrationRequest
import java.io.FileInputStream
import java.nio.file.Paths
import java.security.PrivateKey
import java.util.*

class FabricClient() {
    var gateway: Option<Gateway> = None
    var network: Option<Network> = None
    var contract: Option<Contract> = None
    val config = Properties()

    init {
        FileInputStream("${System.getProperty("user.dir")}/fabric-client/src/main/resources/config.properties")
                .use { config.load(it) }
        // First enroll an admin and user
        // TODO: move this to initialisation script
        enrollAdmin()
        // TODO: move this to initialisation script
        registerUser()
        println("Attempting to connect to Fabric network")
        // Create a gateway connection
        try {
            gateway = Some(connect())
            println("Connected!")
            network = gateway.map { it.getNetwork("mychannel") }
            contract = network.map { it.getContract("basic") }
        } catch (e: Exception) {
            println("Fabric Error: Error creating network connection: ${e.message}")
        }
    }

    fun start() = try {
        // Send the set of public keys to the Ethereum client to initialise the management committee
        // TODO: move this to initialisation script
        getFabricAgentPublicKeys().map {
            sendCommitteeHelper(it)
        }

        // Get all blocks from block 2 onwards and start listening for new block events
        this.network.map { it.addBlockListener(2, ::handleBlockEvent) }
    } catch (e: Exception) {
        println("Fabric Error: Error creating block listener: ${e.stackTrace}")
    }

    fun getState(key: String): Either<Error, String> = try {
        contract.fold({
            println("Fabric Error: Error getting contract")
            Left(Error("Fabric Error: Error getting contract"))
        }, {
            val resultJSON = it.evaluateTransaction("ReadAsset", key).toString(Charsets.UTF_8)
            val result = Gson().fromJson(resultJSON, GetStateResult::class.java)
            Right(result.data.toString(Charsets.UTF_8))
        })
    } catch (e: Exception) {
        println("Fabric Error: Error getting state $key: ${e.message}")
        Left(Error("Fabric Error: Error getting state $key: ${e.message}"))
    }

    fun getStateHistory(key: String): Either<Error, List<KeyModification>> = try {
        contract.fold({
            println("Fabric Error: Error getting contract")
            Left(Error("Fabric Error: Error getting contract"))
        }, {
            val resultJSON = it.evaluateTransaction("GetHistoryForKey", key).toString(Charsets.UTF_8)
            val result = Gson().fromJson(resultJSON, Array<KeyModification>::class.java).toList()
            Right(result)
        })
    } catch (e: Exception) {
        println("Fabric Error: Error getting state history for key $key: ${e.message}")
        Left(Error("Fabric Error: Error getting state history for key $key: ${e.message}"))
    }

    fun createCaClient(): HFCAClient {
        val props = Properties()
        props["pemFile"] = config["CA_PEM_PATH"]
        props["allowAllHostNames"] = "true"
        // TODO: parameterise
        val caClient = HFCAClient.createNewInstance("https://localhost:7054", props)
        val cryptoSuite = CryptoSuiteFactory.getDefault().cryptoSuite
        caClient.cryptoSuite = cryptoSuite
        return caClient
    }

    fun enrollAdmin() {
        val caClient = createCaClient()
        // Create a wallet for managing identities
        val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

        // Check to see if we've already enrolled the admin user.
        // TODO: parameterise
        if (wallet["admin"] != null) {
            println("An identity for the admin user \"admin\" already exists in the wallet")
            return
        } else {
            val enrollmentRequestTLS = EnrollmentRequest()
            // TODO: parameterise
            enrollmentRequestTLS.addHost("localhost")
            enrollmentRequestTLS.profile = "tls"
            // TODO: parameterise
            val enrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS)
            // TODO: parameterise
            val user: Identity = Identities.newX509Identity("Org1MSP", enrollment)
            // TODO: parameterise
            wallet.put("admin", user)
            println("Successfully enrolled user \"admin\" and imported it into the wallet")
        }
    }

    fun registerUser() {
        val caClient = createCaClient()
        // Create a wallet for managing identities
        val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

        // Check to see if we've already enrolled the user.
        // TODO: parameterise
        if (wallet["agentUser"] != null) {
            println("An identity for the user \"agentUser\" already exists in the wallet")
            return
        }
        // TODO: parameterise
        val adminIdentity = wallet["admin"] as X509Identity
        if (adminIdentity == null) {
            println("\"admin\" needs to be enrolled and added to the wallet first")
            return
        }
        val admin: User = object : User {
            // TODO: parameterise
            override fun getName(): String = "admin"
            override fun getRoles(): Set<String> = setOf()
            override fun getAccount(): String = ""
            // TODO: parameterise
            override fun getAffiliation(): String = "org1.department1"
            override fun getEnrollment(): Enrollment {
                return object : Enrollment {
                    override fun getKey(): PrivateKey = adminIdentity.privateKey
                    override fun getCert(): String = Identities.toPemString(adminIdentity.certificate)
                }
            }
            // TODO: parameterise
            override fun getMspId(): String = "Org1MSP"
        }

        // Register the user, enroll the user, and import the new identity into the wallet.
        // TODO: parameterise
        val registrationRequest = RegistrationRequest("agentUser")
        // TODO: parameterise
        registrationRequest.affiliation = "org1.department1"
        // TODO: parameterise
        registrationRequest.enrollmentID = "agentUser"
        val enrollmentSecret = caClient.register(registrationRequest, admin)
        // TODO: parameterise
        val enrollment = caClient.enroll("agentUser", enrollmentSecret)
        // TODO: parameterise
        val user: Identity = Identities.newX509Identity("Org1MSP", enrollment)
        // TODO: parameterise
        wallet.put("agentUser", user)
        println("Successfully enrolled user \"agentUser\" and imported it into the wallet")
    }

    // Helper function for connecting to the gateway
    fun connect(): Gateway {
        // Load a file system based wallet for managing identities.
        val walletPath = Paths.get("wallet")
        val wallet = Wallets.newFileSystemWallet(walletPath)

        val networkConfigFile = Paths.get(config["NETWORK_CONFIG_PATH"] as String)

        // Configure the gateway connection used to access the network.
        // TODO: parameterise
        val builder = Gateway.createBuilder()
                .identity(wallet, "agentUser")
                .networkConfig(networkConfigFile)
                .discovery(true)
        return builder.connect()
    }
}

/**
 * The handleBlockEvent function processes every block that's received from the Fabric
 * peer to determine how the accumulator should be updated. If the block is a config
 * (anything else?) block, the accumulator should not be modified, but a new entry of the
 * accumulator should be stored in the accumulator DB for that block height. Otherwise,
 * the block will contain updates to the application state in the Fabric ledger, and the
 * accumulator should be updated accordingly.
 */
fun handleBlockEvent(blockEvent: BlockEvent) {
    val blockNum = blockEvent.blockNumber.toInt()
    println("Processing block $blockNum")
    val kvWrites = blockEvent.transactionEvents
            // Filter the valid transactions
            .filter { it.isValid }
            // Get the set of KVWrites across all transactions
            .flatMap { txEvent ->
                txEvent.transactionActionInfos.flatMap { txActionInfo ->
                    txActionInfo.txReadWriteSet.nsRwsetInfos.flatMap { nsRwsetInfo ->
                        nsRwsetInfo.rwset.writesList.map { kvWrite ->
                            KvWrite(kvWrite.key, kvWrite.value.toStringUtf8(), kvWrite.isDelete)
                        }
                    }
                }
            }
    // Trigger the update of the accumulator for the block with the list of all KVWrites for the block
    updateAccumulator(blockNum, kvWrites).flatMap { accumulator ->
        // Then send the accumulator to the Ethereum client for publishing
        sendCommitmentHelper(accumulator, blockNum)
    }
}

data class GetStateResult(val type: String, val data: ByteArray)
data class KeyModification(val timestamp: Timestamp, val value: String, val txId: String, val isDelete: Boolean)
data class Timestamp(val seconds: String, val nanos: Int)
