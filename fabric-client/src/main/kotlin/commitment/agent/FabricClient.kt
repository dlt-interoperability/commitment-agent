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
import java.nio.file.Paths
import java.security.PrivateKey
import java.util.*

class FabricClient(val orgId: String) {
    var gateway: Option<Gateway> = None
    var network: Option<Network> = None
    var contract: Option<Contract> = None
    val config = Properties()

    init {
        this::class.java.getResourceAsStream("/${orgId}config.properties")
                .use { config.load(it) }
        // First enroll an admin and user
        enrollAdmin()
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
        // Get all blocks from block 2 onwards and start listening for new block events
        this.network.map { it.addBlockListener(2, ::handleBlockEvent) }
    } catch (e: Exception) {
        println("Fabric Error: Error creating block listener: ${e.message}")
    }

    fun initialize() = try {
        // Send the set of public keys to the Ethereum client to initialise the management committee
        getFabricAgentPublicKeys().flatMap {
            sendCommitteeHelper(it, config)
        }
    } catch (e: Exception) {
        println("Fabric Error: Error creating block listener: ${e.message}")
        Left(Error("Fabric Error: Error creating block listener: ${e.message}"))
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
        val caUrl = config["CA_URL"] as String
        val caClient = HFCAClient.createNewInstance(caUrl, props)
        val cryptoSuite = CryptoSuiteFactory.getDefault().cryptoSuite
        caClient.cryptoSuite = cryptoSuite
        return caClient
    }

    fun enrollAdmin() {
        val caClient = createCaClient()
        // Create a wallet for managing identities
        val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

        // Check to see if we've already enrolled the admin user.
        val admin = config["ADMIN"] as String
        if (wallet[admin] != null) {
            println("An identity for the admin user '$admin' already exists in the wallet")
            return
        } else {
            val enrollmentRequestTLS = EnrollmentRequest()
            val hostname = config["HOSTNAME"] as String
            enrollmentRequestTLS.addHost(hostname)
            enrollmentRequestTLS.profile = "tls"
            val enrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS)
            val msp = config["MSP"] as String
            val user: Identity = Identities.newX509Identity(msp, enrollment)
            wallet.put(admin, user)
            println("Successfully enrolled user '$admin' and imported it into the wallet")
        }
    }

    fun registerUser() {
        val caClient = createCaClient()
        // Create a wallet for managing identities
        val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

        // Check to see if we've already enrolled the user.
        val username = config["USER"] as String
        if (wallet[username] != null) {
            println("An identity for the user '$username' already exists in the wallet")
            return
        }
        val adminName = config["ADMIN"] as String
        val adminIdentity = wallet[adminName] as X509Identity
        if (adminIdentity == null) {
            println("'$adminName' needs to be enrolled and added to the wallet first")
            return
        }
        val affiliation = config["AFFILIATION"] as String
        val msp = config["MSP"] as String
        val admin: User = object : User {
            override fun getName(): String = adminName
            override fun getRoles(): Set<String> = setOf()
            override fun getAccount(): String = ""
            override fun getAffiliation(): String = affiliation
            override fun getEnrollment(): Enrollment {
                return object : Enrollment {
                    override fun getKey(): PrivateKey = adminIdentity.privateKey
                    override fun getCert(): String = Identities.toPemString(adminIdentity.certificate)
                }
            }
            override fun getMspId(): String = msp
        }

        // Register the user, enroll the user, and import the new identity into the wallet.
        val registrationRequest = RegistrationRequest(username)
        registrationRequest.affiliation = affiliation
        registrationRequest.enrollmentID = username
        val enrollmentSecret = caClient.register(registrationRequest, admin)
        val enrollment = caClient.enroll(username, enrollmentSecret)
        val user: Identity = Identities.newX509Identity(msp, enrollment)
        wallet.put(username, user)
        println("Successfully enrolled user '$username' and imported it into the wallet")
    }

    // Helper function for connecting to the gateway
    fun connect(): Gateway {
        // Load a file system based wallet for managing identities.
        val walletPath = Paths.get("wallet")
        val wallet = Wallets.newFileSystemWallet(walletPath)

        val networkConfigFile = Paths.get(config["NETWORK_CONFIG_PATH"] as String)

        // Configure the gateway connection used to access the network.
        val user = config["USER"] as String
        val builder = Gateway.createBuilder()
                .identity(wallet, user)
                .networkConfig(networkConfigFile)
                .discovery(true)
        return builder.connect()
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
        val orgName = config["ORG"] as String
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
        // If this is the first block streamed (which is block 2), initialise the accumulator
        if (blockNum == 2) {
            val seed1 = (config["SEED1"] as String).toLong()
            val seed2 = (config["SEED2"] as String).toLong()
            val seed3 = (config["SEED3"] as String).toLong()
           initialiseAccumulator(blockNum, kvWrites, orgName, seed1, seed2, seed3)
        } else {
            // Trigger the update of the accumulator for the block with the list of all KVWrites for the block
            updateAccumulator(blockNum, kvWrites, orgName).flatMap { accumulator ->
                // Then send the accumulator to the Ethereum client for publishing
                sendCommitmentHelper(accumulator, blockNum, config)
            }
        }
    }
}

data class GetStateResult(val type: String, val data: ByteArray)
data class KeyModification(val timestamp: Timestamp, val value: String, val txId: String, val isDelete: Boolean)
data class Timestamp(val seconds: String, val nanos: Int)
