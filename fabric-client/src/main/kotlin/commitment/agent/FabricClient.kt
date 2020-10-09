package commitment.agent.fabric.client

import arrow.core.*
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val config = Properties()

    init {
        // Load the config properties from the file in src/main/resources
        this::class.java.getResourceAsStream("/${orgId}config.properties")
                .use { config.load(it) }
    }

    fun initialize() {
        enrollAdmin()
        registerUser()
        val orgName = config["ORG"] as String
        val seed1 = (config["SEED1"] as String).toLong()
        val seed2 = (config["SEED2"] as String).toLong()
        val seed3 = (config["SEED3"] as String).toLong()
        initialiseAccumulator(1, orgName, seed1, seed2, seed3)
    }

    fun setManagementCommittee() = try {
        println("setting the management committee")
        // Send the set of public keys to the Ethereum client to initialise the management committee
        getFabricAgentPublicKeys().map {
            runBlocking { sendCommitteeHelper(it, config) }
        }
    } catch (e: Exception) {
        println("Fabric Error: Error creating block listener: ${e.message}")
        Left(Error("Fabric Error: Error creating block listener: ${e.message}"))
    }

    fun start() {
        // Create a connection to the Fabric peer
        println("Attempting to connect to Fabric network")
        connect().map { (network, _) ->
            // The first block that is streamable from the Fabric peer is block 2.
            // Get all blocks from block 2 onwards and start listening for new block events.
            // All block events are processed by the handleBlockEvent function.
            network.addBlockListener(2, ::handleBlockEvent)
        }
}

    fun enrollAdmin() {
        val caClient = createCaClient()
        // Create a wallet for managing identities
        val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

        // Check to see if we've already enrolled the admin user.
        val admin = config["ADMIN"] as String
        if (wallet[admin] != null) {
            println("An identity for the admin user '$admin' already exists in the wallet")
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
        } else {
            val adminName = config["ADMIN"] as String
            val adminIdentity: X509Identity? = wallet[adminName] as X509Identity
            if (adminIdentity == null) {
                println("'$adminName' needs to be enrolled and added to the wallet first")
            } else {
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
                val user = Identities.newX509Identity(msp, enrollment)
                wallet.put(username, user)
                println("Successfully enrolled user '$username' and imported it into the wallet")
            }
        }
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

    // Helper function for connecting to the Fabric network
    fun connect(): Either<Error, Tuple2<Network, Contract>> {
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
        val gateway = builder.connect()
        println("Connected!")
        val network: Network? = gateway.getNetwork(config["CHANNEL"] as String)
        return if (network == null) {
            println("Fabric Error: Error creating Fabric network connection")
            Left(Error("Fabric Error: Error creating Fabric network connection"))
        } else {
            val contract: Contract? = network?.getContract(config["CHAINCODE"] as String)
            if (contract == null) {
                println("Fabric Error: Error getting Fabric network contract")
                Left(Error("Fabric Error: Error getting Fabric network contract"))
            } else {
                Right(Tuple2(network, contract))
            }
        }
    }

    /**
     * The handleBlockEvent function processes every block that's received from the Fabric
     * peer to determine how the accumulator should be updated. The accumulator is updated with
     * all of the KVWrites across all the valid transactions in the block. If there are no KVWrites
     * the accumulator is not updated but stored as-is under a new block height.
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
                                println("KvWrite: $kvWrite")
                                KvWrite(kvWrite.key, kvWrite.value.toStringUtf8(), kvWrite.isDelete)
                            }
                        }
                    }
                }

        // Trigger the update of the accumulator for the block with the list of all KVWrites for the block
        updateAccumulator(blockNum, kvWrites, orgName).map { accumulatorWrapper ->
            // Then send the accumulator to the Ethereum client for publishing
            sendCommitmentHelper(accumulatorWrapper, blockNum, config)
        }
    }

    /**
     * The getStateHistory function is used to retrieve a state from the Fabric ledger on request from
     * an external client. This function returns every version of the state that has existed. This means
     * the caller of the function (createProof function in AccumulatorManager) can check if the
     * latest version of the state is present in the accumulator version the external client has
     * requested. If not, it can check the next oldest version of the state until a match is found.
     */
    fun getStateHistory(key: String, contract: Contract): Either<Error, List<KeyModification>> = try {
        val queryHistoryChaincodeFn = config["QUERY_HISTORY_CC_FN"] as String
        val resultJSON = contract.evaluateTransaction(queryHistoryChaincodeFn, key).toString(Charsets.UTF_8)
        val result = Gson().fromJson(resultJSON, Array<KeyModification>::class.java).toList()
        Right(result)
    } catch (e: Exception) {
        println("Fabric Error: Error getting state history for key $key: ${e.message}")
        Left(Error("Fabric Error: Error getting state history for key $key: ${e.message}"))
    }
}

data class KeyModification(val timestamp: Timestamp, val value: String, val txId: String, val isDelete: Boolean)
data class Timestamp(val seconds: String, val nanos: Int)
