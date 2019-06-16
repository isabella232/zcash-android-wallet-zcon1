package cash.z.android.wallet.data

import kotlinx.coroutines.CoroutineScope

interface TransactionSender {
    fun start(scope: CoroutineScope)
    fun stop()
    suspend fun sendToAddress(encoder: RawTransactionEncoder, zatoshi: Long, toAddress: String, memo: String = "", fromAccountId: Int = 0)
}