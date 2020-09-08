package commitment.agent

import org.hyperledger.fabric.gateway.*
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

    fun start() {
        // First enroll an admin and user
        enrollAdmin()
        registerUser()

        println("Attempting to connect to Fabric network")
        // Create a gateway connection
        try {
            val gateway = connect()
                println("Connected!")
                // Obtain a smart contract deployed on the network.
                val network = gateway.getNetwork("mychannel")
        } catch (e: ContractException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}

fun createCaClient(): HFCAClient {
    val config = Properties()
    FileInputStream("${System.getProperty("user.dir")}/src/main/resources/config.properties")
            .use { config.load(it) }
    val props = Properties()
    props["pemFile"] = config["CA_PEM_PATH"]
    props["allowAllHostNames"] = "true"
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
    if (wallet["admin"] != null) {
        println("An identity for the admin user \"admin\" already exists in the wallet")
        return
    } else {
        val enrollmentRequestTLS = EnrollmentRequest()
        enrollmentRequestTLS.addHost("localhost")
        enrollmentRequestTLS.profile = "tls"
        val enrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS)
        val user: Identity = Identities.newX509Identity("Org1MSP", enrollment)
        wallet.put("admin", user)
        println("Successfully enrolled user \"admin\" and imported it into the wallet")
    }
}

fun registerUser() {
    val caClient = createCaClient()
    // Create a wallet for managing identities
    val wallet = Wallets.newFileSystemWallet(Paths.get("wallet"))

    // Check to see if we've already enrolled the user.
    if (wallet["agentUser"] != null) {
        println("An identity for the user \"agentUser\" already exists in the wallet")
        return
    }

    val adminIdentity = wallet["admin"] as X509Identity
    if (adminIdentity == null) {
        println("\"admin\" needs to be enrolled and added to the wallet first")
        return
    }
    val admin: User = object : User {
        override fun getName(): String {
            return "admin"
        }

        override fun getRoles(): Set<String> {
            return setOf()
        }

        override fun getAccount(): String {
            return ""
        }

        override fun getAffiliation(): String {
            return "org1.department1"
        }

        override fun getEnrollment(): Enrollment {
            return object : Enrollment {
                override fun getKey(): PrivateKey {
                    return adminIdentity.privateKey
                }

                override fun getCert(): String {
                    return Identities.toPemString(adminIdentity.certificate)
                }
            }
        }

        override fun getMspId(): String {
            return "Org1MSP"
        }
    }

    // Register the user, enroll the user, and import the new identity into the wallet.
    val registrationRequest = RegistrationRequest("agentUser")
    // TODO: parameterise the affiliation
    registrationRequest.affiliation = "org1.department1"
    registrationRequest.enrollmentID = "agentUser"
    val enrollmentSecret = caClient.register(registrationRequest, admin)
    val enrollment = caClient.enroll("agentUser", enrollmentSecret)
    // TODO: parameterise the MSP name
    val user: Identity = Identities.newX509Identity("Org1MSP", enrollment)
    wallet.put("agentUser", user)
    println("Successfully enrolled user \"agentUser\" and imported it into the wallet")
}

// Helper function for connecting to the gateway
fun connect(): Gateway {
    // Load a file system based wallet for managing identities.
    val walletPath = Paths.get("wallet")
    val wallet = Wallets.newFileSystemWallet(walletPath)

    // Path to a common connection profile describing the network.
    val properties = Properties()
    FileInputStream("${System.getProperty("user.dir")}/src/main/resources/config.properties")
            .use { properties.load(it) }
    val networkConfigFile = Paths.get(properties["NETWORK_CONFIG_PATH"] as String)

    // Configure the gateway connection used to access the network.
    val builder = Gateway.createBuilder()
            .identity(wallet, "agentUser")
            .networkConfig(networkConfigFile)
            .discovery(true)
    return builder.connect()
}