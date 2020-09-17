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

/**
 * This function currently takes the first 32 bytes of the accumulator and returns it as a bytearray
 */
fun stringToBytes32ByteArray(string: String): Either<Error, ByteArray> = try {
    val stringByteArray = string.substring(0, 32).toByteArray()
    val bytesLength32 = ByteArray(32)
    stringByteArray.copyInto(bytesLength32)
    if (bytesLength32.size == 32) {
        Right(bytesLength32)
    } else {
        println("Error converting string to bytearray of length 32. ")
        Left(Error("Error converting string to bytearray of length 32. "))
    }
} catch (e: Exception) {
    println("Error converting string to 32 byte bytearray: ${e.message}")
    Left(Error("Error converting string to 32 byte bytearray: ${e.message}"))
}