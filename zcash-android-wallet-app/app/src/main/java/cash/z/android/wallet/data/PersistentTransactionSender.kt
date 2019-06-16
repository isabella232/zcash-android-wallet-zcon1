package cash.z.android.wallet.data

import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

class PersistentTransactionMonitor (
    private val manager: TransactionManager,
    private val service: LightWalletService
) : TransactionSender {
    private lateinit var channel: SendChannel<TransactionUpdateRequest>
    private var monitoringJob: Job? = null
    private val initialMonitorDelay = 45_000L

    fun CoroutineScope.requestUpdate() = launch {
        if (!channel.isClosedForSend) {
            channel.send(SubmitPending)
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
                is SubmitPending -> submitPendingTransactions()
            }
        }
    }

    private fun CoroutineScope.startMonitor() = launch {
        while (!channel.isClosedForSend && isActive) {
            delay(calculateDelay())
            requestUpdate()
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
    ) = withContext(IO) {
        manager.manageCreation(encoder, zatoshi, toAddress, memo)
        requestUpdate()
        Unit
    }

    /**
     * Submit all pending transactions that have not expired.
     */
    private suspend fun submitPendingTransactions() = withContext(IO) {
        twig("received request to submit pending transactions")
        with(manager) {
            getAllPending().also { twig("found ${it.size} pending txs to submit") }.forEach { rawTx ->
                manageSubmission(service, rawTx)
            }
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