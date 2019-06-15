package cash.z.android.wallet.data

interface TransactionManager {
    /**
     * Initialize a transaction and return its ID.
     *
     * @return the id of the transaction to use with subsequent calls to this manager instance.
     */
    fun new(): Long

    /**
     * Set the rawTransaction data for the given transaction. Typically, this would transition the state of the
     * transaction to something like CREATED. Some implementations might derive the state, based on whether this raw
     * transaction data has been provided.
     *
     * @param txId the id of the transaction to update
     * @param rawTransaction the raw transaction data
     */
    fun setRawTransaction(txId: Long, rawTransaction: ByteArray)

    /**
     * Signal that there has been an error while attempting to create a transaction.
     *
     * @param txId the id of the transaction to update
     * @param error information about the error that occurred
     */
    fun setCreationError(txId: Long, error: TransactionError)

    fun setSubmissionStarted(txId: Long)
    fun setSubmissionComplete(txId: Long, isSuccess: Boolean, error: TransactionError? = null)
    fun getAllPendingRawTransactions(): Map<Long, ByteArray>
}


interface TransactionError {
    val message: String
}