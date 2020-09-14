package commitment.agent.ethereum.client

import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

class ManagementCommiteeContractGasProvider : ContractGasProvider {
    override fun getGasPrice(contractFunc: String): BigInteger = BigInteger.valueOf(20000000000)

    override fun getGasLimit(contractFunc: String): BigInteger = BigInteger.valueOf(6721975)

    @Deprecated("getGasPrice without a specified contract function has been deprecated.")
    override fun getGasPrice(): BigInteger? = BigInteger.valueOf(20000000000)

    @Deprecated("getGasLimit without a specified contract function has been deprecated.")
    override fun getGasLimit(): BigInteger? = BigInteger.valueOf(6721975)
}