package commitment.agent.ethereum.client

import arrow.core.*
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilenameFilter
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

fun getFabricAgentPublicKeys(): Either<Error, List<String>> = try {
    val walletDir = File("${System.getProperty("user.dir")}/wallet")
    val userCredentialFiles = walletDir.list(FilenameFilter { _, name ->
        name.contains("User")
    }).toList()
    userCredentialFiles.map { getPublicKeyFromWalletFile(it) }
            .traverse(Either.applicative<Error>(), ::identity)
            .fix().map { it.fix() }
} catch (e: Exception) {
    println("Error getting credentials from the Fabric wallet: ${e.message}")
    Left(Error("Error getting credentials from the Fabric wallet: ${e.message}"))
}

fun getPublicKeyFromWalletFile(fileName: String): Either<Error, String> = try {
    // First get all the sets of credentials in the wallet that end in "user.id"
    val fileContents = File("${System.getProperty("user.dir")}/wallet/$fileName")
            .inputStream()
            .readBytes()
            .toString(Charsets.UTF_8)

    // Then convert from JSON string to a FabricCredentials
    val fabCredentials = Gson().fromJson(fileContents, FabricCredentials::class.java)
    getCertificateFromString(fabCredentials.credentials.certificate).map {
        Base64.getEncoder().encodeToString(it.publicKey.encoded)
    }
} catch (e: Exception) {
    println("Error getting credentials from the Fabric wallet: ${e.message}")
    Left(Error("Error getting credentials from the Fabric wallet: ${e.message}"))
}

fun getCertificateFromString(certificateString: String): Either<Error, X509Certificate> = try {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val certificate = certificateFactory.generateCertificate(
            ByteArrayInputStream(
                    Base64.getDecoder().decode(
                            certificateString.replace("\\n".toRegex(), "")
                                    .removePrefix("-----BEGIN CERTIFICATE-----")
                                    .removeSuffix("-----END CERTIFICATE-----")
                                    .toByteArray())
            )
    )
    Right(certificate as X509Certificate)
} catch (e: Exception) {
    println("Error converting certificate string to an X509 certificate: ${e.message}")
    Left(Error("Error converting certificate string to an X509 certificate: ${e.message}"))
}

data class FabricCredentials(
        val version: String,
        val mspId: String,
        val type: String,
        val credentials: Credentials
)

data class Credentials(
        val certificate: String,
        val privateKey: String
)