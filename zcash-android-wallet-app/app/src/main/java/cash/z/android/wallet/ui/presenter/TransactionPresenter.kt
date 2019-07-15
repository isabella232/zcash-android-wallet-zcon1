package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.extention.truncate
import cash.z.android.wallet.ui.adapter.TransactionUiModel
import cash.z.android.wallet.ui.fragment.Zcon1HomeFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.entity.*
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class TransactionPresenter @Inject constructor(
    private val view: Zcon1HomeFragment,
    private val synchronizer: Synchronizer
) : Presenter {

    interface TransactionView : PresenterView {
        fun setTransactions(transactions: List<TransactionUiModel>)
    }

    private var pendingJob: Job? = null
    private var clearedJob: Job? = null

    private var latestPending: List<TransactionUiModel> = listOf()
    private var latestCleared: List<TransactionUiModel> = listOf()

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
            latestPending = new.map { it.toTransactionUiModel() }
            bind()
        }
        twig("pending transaction binder exiting!")
    }

    fun CoroutineScope.launchClearedBinder() = launch {
        val channel = synchronizer.clearedTransactions()
        twig("cleared transaction binder starting")
        for (new in channel) {
            twig("cleared transactions have been modified... binding to the view")
            latestCleared = new.map {
                when(it) {
                    is SentTransaction -> it.toTransactionUiModel()
                    is ReceivedTransaction -> it.toTransactionUiModel()
                    else -> throw UnsupportedTransactionException(it)
                }
            }
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
        val mergedTransactions = mutableListOf<TransactionUiModel>()

        mergedTransactions.addAll(latestCleared)
        mergedTransactions.addAll(latestPending)
        mergedTransactions.sortByDescending {
            if (it.timestampMillis > 0) it.timestampMillis else it.minedHeight.toLong()
        }
        view.setTransactions(mergedTransactions)
//        twig("MERGED_TX---------vvvvvv")
//        mergedTransactions.forEach {
//            twig("MERGED_TX: ${it.toString()}")
//        }
//        twig("MERGED_TX---------^^^^^^")
    }


//    sealed class PurchaseResult {
//        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
//        data class Failure(val reason: String = "") : PurchaseResult()
//    }
}
private fun SentTransaction.toTransactionUiModel(): TransactionUiModel {
    return TransactionUiModel(
        status = "to ${toAddress.truncate()}",
        isSend = true,
        minedHeight = minedHeight,
        zatoshiValue = value,
        memo = memo,
        isSwag = memo?.toLowerCase()?.contains("swag") ?: false,
        timestampMillis = blockTimeInSeconds * 1000L,
        isPokerChip = false
    )
}

private fun ReceivedTransaction.toTransactionUiModel(): TransactionUiModel {
    return TransactionUiModel(
        status = "from shielded address",
        minedHeight = minedHeight,
        zatoshiValue = value,
        timestampMillis = blockTimeInSeconds * 1000L,
        isPokerChip = false
    )
}

private fun PendingTransaction.toTransactionUiModel(): TransactionUiModel {
    val isPokerChip = memo?.toLowerCase()?.contains("poker chip") == true
    val isSwag = memo?.toLowerCase()?.contains("swag") ?: false
    var description = when {
        isSwag && isMined() -> "Purchase accepted."
        isFailedEncoding() -> "Failed to create! Aborted."
        isFailedSubmit() -> "Failed to send...Retrying!"
        isCreating() -> if (isPokerChip) "Redeeming..." else "Creating transaction..."
        isSwag && !isMined() -> "Purchase pending"
        isSubmitted() && !isMined() -> "Submitted, awaiting response."
        isSubmitted() && isMined() -> if (isPokerChip) "Successfully mined!" else "to ${toAddress.truncate()}"
        else -> "Pending..."
    }
    return TransactionUiModel(
        action = if (isPokerChip) "Scan" else null,
        status = description,
        isPending = true,
        isSend = true,
        isSwag = isSwag,
        minedHeight = minedHeight,
        zatoshiValue = value,
        memo = memo,
        timestampMillis = createTime,
        isPokerChip = isPokerChip
    )
}

class UnsupportedTransactionException(tx: ClearedTransaction) :
    RuntimeException("Unsupported transaction type. " +
            "Expected either SentTransaction or ReceivedTransaction but was ${tx.javaClass.canonicalName}")

//private fun PendingTransaction.toClearedTransaction(): ClearedTransaction {
//    var description = when {
//        isFailedEncoding() -> "Failed to create! Aborted."
//        isFailedSubmit() -> "Failed to send...Retrying!"
//        isCreating() -> if (memo?.toLowerCase()?.contains("poker chip") == true) "Redeeming..." else "Creating transaction..."
//        isSubmitted() && !isMined() -> "Submitted, awaiting response."
//        isSubmitted() && isMined() -> "Successfully mined!"
//        else -> "Pending..."
//    }
////    if (!isSubmitted() && (submitAttempts > 2 || encodeAttempts > 2)) {
////        description += " aborting in ${ttl() / 60L}m${ttl().rem(60)}s"
////    }
//    return SentTransaction(
//        value = value,
//        minedHeight = minedHeight,
//        blockTimeInSeconds = createTime / 1000L,
//        toAddress = toAddress,
//        status = description,
//        memo = memo
//    )
//}

@Module
abstract class TransactionPresenterModule {
    @Binds
    abstract fun providePresenter(transactionPresenter: TransactionPresenter): Presenter
}