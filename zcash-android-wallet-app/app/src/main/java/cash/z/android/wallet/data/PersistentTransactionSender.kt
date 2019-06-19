package cash.z.android.wallet.data

import cash.z.android.wallet.data.db.PendingTransactionEntity
import cash.z.android.wallet.data.db.isPending
import cash.z.android.wallet.extention.onErrorReturn
import cash.z.android.wallet.extention.tryNull
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

class PersistentTransactionSender (
    private val manager: TransactionManager,
    private val service: LightWalletService
) : TransactionSender {

    private lateinit var channel: SendChannel<TransactionUpdateRequest>
    private var monitoringJob: Job? = null
    private val initialMonitorDelay = 45_000L
    private var listenerChannel: SendChannel<List<PendingTransactionEntity>>? = null
    override var onSubmissionError: ((Throwable) -> Unit)? = null

    fun CoroutineScope.requestUpdate(triggerSend: Boolean) = launch {
        if (!channel.isClosedForSend) {
            channel.send(if (triggerSend) SubmitPendingTx else RefreshSentTx)
        }
    }

    /**
     * Start an actor that listens for signals about what to do with transactions. This actor's lifespan is within the
     * provided [scope] and it will live until the scope is cancelled.
     */
    private fun CoroutineScope.startActor() = actor<TransactionUpdateRequest> {
        var pendingTransactionDao = 0 // actor state:
        for (msg in channel) { // iterate over incoming messages
            when (msg) {
                is SubmitPendingTx -> submitPendingTransactions()
                is RefreshSentTx -> refreshSentTransasctions()
            }
        }
    }

    private fun CoroutineScope.startMonitor() = launch {
        delay(5000) // todo see if we need a formal initial delay
        while (!channel.isClosedForSend && isActive) {
            requestUpdate(true)
            delay(calculateDelay())
        }
        twig("TransactionMonitor stopping!")
    }

    private fun calculateDelay(): Long {
        return initialMonitorDelay
    }

    override fun start(scope: CoroutineScope) {
        twig("TransactionMonitor starting!")
        channel = scope.startActor()
        monitoringJob?.cancel()
        monitoringJob = scope.startMonitor()
    }

    override fun stop() {
        channel.close()
        monitoringJob?.cancel()?.also { monitoringJob = null }
        manager.stop()
    }

    override fun notifyOnChange(channel: SendChannel<List<PendingTransactionEntity>>) {
        listenerChannel?.close()
        listenerChannel = channel
    }

    /**
     * Generates newly persisted information about a transaction so that other processes can send.
     */
    override suspend fun sendToAddress(
        encoder: RawTransactionEncoder,
        zatoshi: Long,
        toAddress: String,
        memo: String,
        fromAccountId: Int
    ): PendingTransactionEntity = withContext(IO) {
        val currentHeight = tryNull { service.getLatestBlockHeight() } ?: -1
        (manager as PersistentTransactionManager).manageCreation(encoder, zatoshi, toAddress, memo, currentHeight).also {
            requestUpdate(true)
        }
    }

    override suspend fun prepareTransaction(
        zatoshiValue: Long,
        address: String,
        memo: String
    ): PendingTransactionEntity? = withContext(IO) {
        (manager as PersistentTransactionManager).initPlaceholder(zatoshiValue, address, memo).also {
            // update UI to show what we've just created. No need to submit, it has no raw data yet!
            requestUpdate(false)
        }
    }

    override suspend fun sendPreparedTransaction(
        encoder: RawTransactionEncoder,
        tx: PendingTransactionEntity
    ): PendingTransactionEntity = withContext(IO) {
        val currentHeight = tryNull { service.getLatestBlockHeight() } ?: -1
        (manager as PersistentTransactionManager).manageCreation(encoder, tx, currentHeight).also {
            // submit what we've just created
            requestUpdate(true)
        }
    }

    override suspend fun cleanupPreparedTransaction(tx: PendingTransactionEntity) {
        if (tx.raw == null) {
            (manager as PersistentTransactionManager).abortTransaction(tx)
        }
    }

    //  TODO: get this from the channel instead
    var previousPending: List<PendingTransactionEntity>? = null

    private suspend fun notifyIfChanged(currentPending: List<PendingTransactionEntity>) = withContext(IO) {
        if (hasChanged(previousPending, currentPending) && listenerChannel?.isClosedForSend != true) {
            listenerChannel?.send(currentPending)
            previousPending = currentPending
        }
    }

    override suspend fun cancel(existingTransaction: PendingTransactionEntity) = withContext(IO) {
        (manager as PersistentTransactionManager).abortTransaction(existingTransaction). also {
            requestUpdate(false)
        }
    }

    private fun hasChanged(
        previousPending: List<PendingTransactionEntity>?,
        currentPending: List<PendingTransactionEntity>
    ): Boolean {
        // shortcuts first
        if (currentPending.isEmpty() && previousPending == null) return false.also { twig("checking pending txs: detected nothing happened yet") } // if nothing has happened, that doesn't count as a change
        if (previousPending == null) return true.also { twig("checking pending txs: detected first set of txs!") } // the first set of transactions is automatically a change
        if (previousPending.size != currentPending.size) return true.also { twig("checking pending txs: detected size change from ${previousPending.size} to ${currentPending.size}") } // can't be the same and have different sizes, duh

        for (tx in currentPending) {
            if (!previousPending.contains(tx)) return true.also { twig("checking pending txs: detected change for $tx") }
        }
        return false.also { twig("checking pending txs: detected no changes in pending txs") }
    }

    /**
     * Submit all pending transactions that have not expired.
     */
    private suspend fun refreshSentTransasctions(): List<PendingTransactionEntity> = withContext(IO) {
        twig("refreshing all sent transactions")
        val allSentTransactions = (manager as PersistentTransactionManager).getAll() // TODO: make this crash and catch error gracefully
        notifyIfChanged(allSentTransactions)
        allSentTransactions
    }

    /**
     * Submit all pending transactions that have not expired.
     */
    private suspend fun submitPendingTransactions() = withContext(IO) {
        twig("received request to submit pending transactions")
        val allTransactions = refreshSentTransasctions()

        var pendingCount = 0
        val currentHeight = tryNull { service.getLatestBlockHeight() } ?: -1
        allTransactions.filter { it.isPending(currentHeight) }.forEach { tx ->
            pendingCount++
            onErrorReturn(onSubmissionError ?: {}) {
                manager.manageSubmission(service, tx)
            }
        }
        twig("given current height $currentHeight, we found $pendingCount pending txs to submit")
    }
}

sealed class TransactionUpdateRequest
object SubmitPendingTx : TransactionUpdateRequest()
object RefreshSentTx : TransactionUpdateRequest()



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

bookkeeper, register, treasurer, mint, ledger


    private fun checkTx(transactionId: Long) {
        if (transactionId < 0) {
            throw SweepException.Creation
        } else {
            twig("successfully created transaction!")
        }
    }

    private fun checkRawTx(transactionRaw: ByteArray?) {
        if (transactionRaw == null) {
            throw SweepException.Disappeared
        } else {
            twig("found raw transaction in the dataDb")
        }
    }

    private fun checkResponse(response: Service.SendResponse) {
        if (response.errorCode < 0) {
            throw SweepException.IncompletePass(response)
        } else {
            twig("successfully submitted. error code: ${response.errorCode}")
        }
    }

    sealed class SweepException(val errorMessage: String) : RuntimeException(errorMessage) {
        object Creation : SweepException("failed to create raw transaction")
        object Disappeared : SweepException("unable to find a matching raw transaction. This means the rust backend said it created a TX but when we looked for it in the DB it was missing!")
        class IncompletePass(response: Service.SendResponse) : SweepException("submit failed with error code: ${response.errorCode} and message ${response.errorMessage}")
    }

 */