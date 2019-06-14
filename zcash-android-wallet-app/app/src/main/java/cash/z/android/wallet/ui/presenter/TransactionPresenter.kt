package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.fragment.Zcon1HomeFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.*
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject

class TransactionPresenter @Inject constructor(
    private val view: Zcon1HomeFragment,
    private val synchronizer: Synchronizer
) : Presenter {

    interface TransactionView : PresenterView {
        fun setTransactions(transactions: List<WalletTransaction>)
    }

    private var transactionJob: Job? = null


    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("TransactionPresenter")
        twig("TransactionPresenter starting!")

        transactionJob?.cancel()
        transactionJob = Job()
//        transactionJob = view.launchPurchaseBinder(synchronizer.activeTransactions())
        transactionJob = view.launchTransactionBinder(synchronizer.allTransactions())
    }

    override fun stop() {
        twig("TransactionPresenter stopping!")
        Twig.clip("TransactionPresenter")
        transactionJob?.cancel()?.also { transactionJob = null }
    }

    fun CoroutineScope.launchPurchaseBinder(channel: ReceiveChannel<Map<ActiveTransaction, TransactionState>>) = launch {
        twig("main purchase binder starting!")
        for (new in channel) {
            twig("main polled a purchase info")
            bind(new)
        }
        twig("main purchase binder exiting!")
    }

    fun CoroutineScope.launchTransactionBinder(allTransactions: ReceiveChannel<List<WalletTransaction>>) = launch {
        twig("transaction binder starting!")
        for (walletTransactionList in allTransactions) {
            twig("received ${walletTransactionList.size} transactions for presenting")
            bind(walletTransactionList)
        }
        twig("transaction binder exiting!")
    }

    //
    // Events
    //

    private fun bind(activeTransactions: Map<ActiveTransaction, TransactionState>) {
//        val newestState = activeTransactions.entries.last().value
//        if (newestState is TransactionState.Failure) {
//            view.orderFailed(PurchaseResult.Failure(newestState.reason))
//        } else {
//            view.orderUpdated(PurchaseResult.Processing(newestState))
//        }
    }

    private fun bind(transactions: List<WalletTransaction>) {
        twig("binding ${transactions.size} walletTransactions")
        view.setTransactions(transactions.sortedByDescending {
            if (!it.isMined && it.isSend) Long.MAX_VALUE else it.timeInSeconds
        })
    }

    sealed class PurchaseResult {
        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
        data class Failure(val reason: String = "") : PurchaseResult()
    }
}


@Module
abstract class TransactionPresenterModule {
    @Binds
    abstract fun providePresenter(transactionPresenter: TransactionPresenter): Presenter
}