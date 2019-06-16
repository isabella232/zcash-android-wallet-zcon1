package cash.z.android.wallet.data

import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.service.LightWalletService

class PersistentTransactionManager: TransactionManager {
    init {

    }
    
    override suspend fun manageCreation(encoder: RawTransactionEncoder, value: Long, toAddress: String, memo: String) {
        twig("managing the creation of a transaction")
        encoder.create(value, toAddress, memo)

    }

    override suspend fun manageSubmission(service: LightWalletService, rawTransaction: ByteArray) {
        try {
            // TODO: stuff before you send
            twig("managing the preparation to submit transaction")
            val response = service.submitTransaction(rawTransaction)
            twig("management of submit transaction completed with response: ${response.errorCode}: ${response.errorMessage}")
            // TODO: stuff after sending
            if (response.errorCode < 0) {
            } else {
            }
        } catch (t: Throwable) {
            twig("error while managing submitting transaction: ${t.message}")
        }
    }

    override suspend fun getAllPending(): List<ByteArray> {
        return listOf()
    }
}