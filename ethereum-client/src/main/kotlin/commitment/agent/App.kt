package commitment.agent.ethereum.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    // Start the Ethereum client
    val ethereumClient = EthereumClient()
    ethereumClient.deployLedgerContract()
    ethereumClient.deployManagementCommitteeContract()
}
