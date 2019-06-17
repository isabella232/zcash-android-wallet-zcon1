package cash.z.android.wallet.data

import cash.z.wallet.sdk.service.LightWalletService

/**
 * Manage transactions with the main purpose of reporting which ones are still pending, particularly after failed
 * attempts or dropped connectivity. The intent is to help see transactions through to completion.
 */
interface TransactionManager {
    suspend fun manageCreation(encoder: RawTransactionEncoder, value: Long, toAddress: String, memo: String)
    suspend fun manageSubmission(service: LightWalletService, pendingTransaction: RawTransaction)
    suspend fun getAllPending(currentHeight: Int): List<RawTransaction>
    fun start()
    fun stop()
//
//    /**
//     * Initialize a transaction and return its ID.
//     *
//     * @return the id of the transaction to use with subsequent calls to this manager instance.
//     */
//    fun new(): Long
//
//    /**
//     * Set the rawTransaction data for the given transaction. Typically, this would transition the state of the
//     * transaction to something like CREATED. Some implementations might derive the state, based on whether this raw
//     * transaction data has been provided.
//     *
//     * @param txId the id of the transaction to update
//     * @param rawTransaction the raw transaction data
//     */
//    fun setRawTransaction(txId: Long, rawTransaction: ByteArray)
//
//    /**
//     * Signal that there has been an error while attempting to create a transaction.
//     *
//     * @param txId the id of the transaction to update
//     * @param error information about the error that occurred
//     */
//    fun setCreationError(txId: Long, error: TransactionError)
//
//    fun setSubmissionStarted(txId: Long)
//    fun setSubmissionComplete(txId: Long, isSuccess: Boolean, error: TransactionError? = null)
//    fun getAllPendingRawTransactions(): Map<Long, ByteArray>

}
interface RawTransaction {
    val raw: ByteArray?
}

interface TransactionError {
    val message: String
}