package cash.z.android.wallet.data

import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch

class PersistentTransactionSender(
    private val manager: TransactionManager,
    private val factory: RawTransactionFactory
) : TransactionSender {

    /**
     * Generates newly persisted information about a transaction so that other processes can send.
     */
    override fun sendToAddress(zatoshi: Long, toAddress: String, memo: String, fromAccountId: Int) {
        val txId = manager.new() // creates a new transaction with a lifecycle of CREATING
        try {
            val rawTransaction = factory.create(zatoshi, toAddress, memo)
            manager.setRawTransaction(txId, rawTransaction) // returns new transaction with lifecycle of CREATED
        } catch (t: Throwable) {
            manager.setCreationError(txId, t.message.toTxError())
            return
        }
    }
}

class PersistentTransactionMonitor (
    private val scope: CoroutineScope,
    private val manager: TransactionManager,
    private val service: LightWalletService
) {
    private val channel: SendChannel<TransactionUpdateRequest>

    init {
        channel = scope.startActor()
    }

    fun update() =  scope.launch {
        channel.send(SubmitPending)
    }

    /**
     * Start an actor that listens for signals about what to do with transactions. This actor's lifespan is within the
     * provided [scope] and it will live until the scope is cancelled.
     */
    fun CoroutineScope.startActor() = actor<TransactionUpdateRequest> {
        var pendingTransactionDao = 0 // actor state:
        for (msg in channel) { // iterate over incoming messages
            when (msg) {
                is SubmitPending -> submitPendingTransactions()
            }
        }
    }

    private fun submitPendingTransactions() {
        val transactions = manager.getAllPendingRawTransactions().forEach { (txId, rawTransaction) ->
            submitPendingTransaction(txId, rawTransaction)
        }
    }

    private fun submitPendingTransaction(txId: Long, rawTransaction: ByteArray) {
        try {
            manager.setSubmissionStarted(txId)
            val response = service.submitTransaction(rawTransaction)
            if (response.errorCode < 0) {
                manager.setSubmissionComplete(txId, false, response.errorMessage.toTxError())
            } else {
                manager.setSubmissionComplete(txId, true)
            }
        } catch (t: Throwable) {
            manager.setSubmissionComplete(txId, false, t.message.toTxError())
        }
    }
}

sealed class TransactionUpdateRequest
object SubmitPending : TransactionUpdateRequest()



private fun String?.toTxError(): TransactionError {
    return FailedTransaction("$this")
}

data class FailedTransaction(override val message: String) : TransactionError

/*
states:
** creating
** failed to create
CREATED
EXPIRED
MINED
SUBMITTED
INVALID
** attempting submission
** attempted submission
 */