package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.fragment.Zcon1HomeFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.dao.ClearedTransaction
import cash.z.wallet.sdk.data.DataSynchronizer
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.db.*
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class TransactionPresenter @Inject constructor(
    private val view: Zcon1HomeFragment,
    private val synchronizer: DataSynchronizer
) : Presenter {

    interface TransactionView : PresenterView {
        fun setTransactions(transactions: List<ClearedTransaction>)
    }

    private var pendingJob: Job? = null
    private var clearedJob: Job? = null

    private var latestPending: List<PendingTransaction> = listOf()
    private var latestCleared: List<ClearedTransaction> = listOf()

    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("TransactionPresenter")
        twig("TransactionPresenter starting!")

        pendingJob?.cancel()
        pendingJob = view.launchPendingBinder()

        clearedJob?.cancel()
        clearedJob = view.launchClearedBinder()
    }

    override fun stop() {
        twig("TransactionPresenter stopping!")
        Twig.clip("TransactionPresenter")
        pendingJob?.cancel()?.also { pendingJob = null }
        clearedJob?.cancel()?.also { clearedJob = null }
    }

    fun CoroutineScope.launchPendingBinder() = launch {
        val channel = synchronizer.pendingTransactions()
        twig("pending transaction binder starting")
        for (new in channel) {
            twig("pending transactions have been modified... binding to the view")
            latestPending = new
            bind()
        }
        twig("pending transaction binder exiting!")
    }

    fun CoroutineScope.launchClearedBinder() = launch {
        val channel = synchronizer.clearedTransactions()
        twig("cleared transaction binder starting")
        for (new in channel) {
            twig("cleared transactions have been modified... binding to the view")
            latestCleared = new
            bind()
        }
        twig("cleared transaction binder exiting!")
    }


    //
    // Events
    //

    private fun bind() {
        twig("binding ${latestPending.size} pending transactions and ${latestCleared.size} cleared transactions")
        // merge transactions
        val mergedTransactions = mutableListOf<ClearedTransaction>()
        latestPending.forEach { mergedTransactions.add(it.toClearedTransaction()) }
        mergedTransactions.addAll(latestCleared)
        mergedTransactions.sortByDescending {
            it.timeInSeconds
        }
        view.setTransactions(mergedTransactions)
//        twig("MERGED_TX---------vvvvvv")
//        mergedTransactions.forEach {
//            twig("MERGED_TX: ${it.toString()}")
//        }
//        twig("MERGED_TX---------^^^^^^")
    }


    sealed class PurchaseResult {
        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
        data class Failure(val reason: String = "") : PurchaseResult()
    }
}

private fun PendingTransaction.toClearedTransaction(): ClearedTransaction {
    var description = when {
        isFailedEncoding() -> "Failed to create! Aborted."
        isFailedSubmit() -> "Failed to send...Retrying!"
        isCreating() -> if (memo.toLowerCase().contains("poker chip")) "Redeeming..." else "Creating transaction..."
        isSubmitted() && !isMined() -> "Submitted, awaiting response."
        isSubmitted() && isMined() -> "Successfully mined!"
        else -> "Pending..."
    }
    if (!isSubmitted() && (submitAttempts > 2 || encodeAttempts > 2)) {
        description += " aborting in ${ttl() / 60L}m${ttl().rem(60)}s"
    }
    return ClearedTransaction(
        value = value,
        isSend = true,
        isMined = isMined(),
        height = minedHeight,
        timeInSeconds = createTime / 1000L,
        address = address,
        status = description,
        memo = memo
    )
}

@Module
abstract class TransactionPresenterModule {
    @Binds
    abstract fun providePresenter(transactionPresenter: TransactionPresenter): Presenter
}