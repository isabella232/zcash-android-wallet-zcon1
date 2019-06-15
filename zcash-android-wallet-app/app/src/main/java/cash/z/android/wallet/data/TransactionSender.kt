package cash.z.android.wallet.data

interface TransactionSender {
    fun sendToAddress(zatoshi: Long, toAddress: String, memo: String, fromAccountId: Int)
}