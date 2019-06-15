package cash.z.android.wallet.data

interface RawTransactionFactory {
    /**
     * Creates a raw transaction that is unsigned.
     */
    fun create(value: Long, toAddress: String, memo: String = ""): ByteArray
}