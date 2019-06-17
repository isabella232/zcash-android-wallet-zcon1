package cash.z.android.wallet.data

import android.os.Handler
import android.os.Looper
import cash.z.android.wallet.data.db.PendingTransactionEntity
import cash.z.wallet.sdk.block.CompactBlockProcessor
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.exception.WalletException
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * A synchronizer that attempts to remain operational, despite any number of errors that can occur.
 */
@ExperimentalCoroutinesApi
class StableSynchronizer @Inject constructor(
    private val wallet: Wallet,
    private val encoder: RawTransactionEncoder,
    private val sender: TransactionSender,
    private val processor: CompactBlockProcessor
) : DataSyncronizer {

    /** This listener will not be called on the main thread. So it will need to switch to do anything with UI, like dialogs */
    override var onCriticalErrorListener: ((Throwable) -> Boolean)? = null

    private var syncJob: Job? = null

    private val balanceChannel = ConflatedBroadcastChannel<Wallet.WalletBalance>()
    private val progressChannel = ConflatedBroadcastChannel<Int>()
    private val pendingChannel = ConflatedBroadcastChannel<List<PendingTransactionEntity>>()

    override val isConnected: Boolean get() = processor.isConnected
    override val isSyncing: Boolean get() = processor.isSyncing
    override val isScanning: Boolean get() = processor.isScanning

    override fun start(scope: CoroutineScope) {
        twig("Staring sender!")
        try {
            wallet.initialize()
        } catch (e: WalletException.AlreadyInitializedException) {
            twig("Warning: wallet already initialized but this is safe to ignore " +
                    "because the SDK now automatically detects where to start downloading.")
        }
        sender.start(scope)
        syncJob = scope.onReady()
    }

    override fun stop() {
        sender.stop()
        syncJob?.cancel().also { syncJob = null }
    }

    private fun CoroutineScope.onReady() = launch(CoroutineExceptionHandler(::onCriticalError)) {
        twig("Synchronizer Ready. Starting processor!")
        processor.onErrorListener = ::onProcessorError
        processor.start()
        twig("Synchronizer onReady complete. Processor start has exited!")
    }

    private fun onCriticalError(unused: CoroutineContext, error: Throwable) {
        twig("********")
        twig("********  ERROR: $error")
        if (error.cause != null) twig("******** caused by ${error.cause}")
        if (error.cause?.cause != null) twig("******** caused by ${error.cause?.cause}")
        twig("********")


        onCriticalErrorListener?.invoke(error)
    }

    var sameErrorCount = 0
    var processorErrorMessage: String? = ""
    private fun onProcessorError(error: Throwable): Boolean {
        if (processorErrorMessage == error.message) sameErrorCount++
        if (sameErrorCount == 5 || sameErrorCount.rem(20) == 0) {
            onCriticalError(CoroutineName("bob"), error)
        }

        processorErrorMessage = error.message
        twig("synchronizer sees your error and ignores it, willfully! Keep retrying ($sameErrorCount), processor!")
        return true
    }

    //
    // Channels
    //

    override fun balances(): ReceiveChannel<Wallet.WalletBalance> {
        return balanceChannel.openSubscription()
    }

    override fun progress(): ReceiveChannel<Int> {
        return progressChannel.openSubscription()
    }

    override fun pendingTransactions(): ReceiveChannel<List<PendingTransactionEntity>> {
        return pendingChannel.openSubscription()
    }


    //
    // Send / Receive
    //

    override suspend fun getAddress(accountId: Int): String = withContext(IO) { wallet.getAddress() }

    override suspend fun sendToAddress(
        zatoshi: Long,
        toAddress: String,
        memo: String,
        fromAccountId: Int
    ) = withContext(IO) {
        sender.sendToAddress(encoder, zatoshi, toAddress, memo, fromAccountId)
    }


//    override fun activeTransactions(): Flow<Pair<ActiveTransaction, TransactionState>> {
//    }
//
//    override fun transactions(): Flow<WalletTransaction> {
//    }
//
//    override fun progress(): Flow<Int> {
//    }
//
//    override fun status(): Flow<FlowSynchronizer.SyncStatus> {
//    }

}

interface DataSyncronizer {
    fun start(scope: CoroutineScope)
    fun stop()

    suspend fun getAddress(accountId: Int = 0): String
    suspend fun sendToAddress(zatoshi: Long, toAddress: String, memo: String = "", fromAccountId: Int = 0)

    fun balances(): ReceiveChannel<Wallet.WalletBalance>
    fun progress(): ReceiveChannel<Int>
    fun pendingTransactions(): ReceiveChannel<List<PendingTransactionEntity>>

    val isConnected: Boolean
    val isSyncing: Boolean
    val isScanning: Boolean
    var onCriticalErrorListener: ((Throwable) -> Boolean)?
}