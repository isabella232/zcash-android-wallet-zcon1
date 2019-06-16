package cash.z.android.wallet.data

interface RawTransactionEncoder {
    /**
     * Creates a raw transaction that is unsigned.
     */
    suspend fun create(zatoshi: Long, toAddress: String, memo: String = ""): ByteArray
}