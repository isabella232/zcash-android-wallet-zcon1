package cash.z.android.wallet.data

import cash.z.wallet.sdk.service.LightWalletService

/**
 * Manage transactions with the main purpose of reporting which ones are still pending, particularly after failed
 * attempts or dropped connectivity. The intent is to help see transactions through to completion.
 */
interface TransactionManager {
    fun start()
    fun stop()
    suspend fun manageCreation(encoder: RawTransactionEncoder, value: Long, toAddress: String, memo: String, currentHeight: Int): RawTransaction
    suspend fun manageSubmission(service: LightWalletService, pendingTransaction: RawTransaction)
    suspend fun getAll(): List<RawTransaction>
}
interface RawTransaction {
    val raw: ByteArray?
}

interface TransactionError {
    val message: String
}