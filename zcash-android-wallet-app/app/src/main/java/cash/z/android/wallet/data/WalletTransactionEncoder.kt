package cash.z.android.wallet.data

import cash.z.wallet.sdk.data.TransactionRepository
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class WalletTransactionEncoder(
    private val wallet: Wallet,
    private val repository: TransactionRepository
) : RawTransactionEncoder {
    override suspend fun create(zatoshi: Long, toAddress: String, memo: String): ByteArray = withContext(IO) {
        val transactionId = wallet.createRawSendTransaction(zatoshi, toAddress, memo)
        repository.findTransactionById(transactionId)?.raw!!
    }
}