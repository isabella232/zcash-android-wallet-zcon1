package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.fragment.HomeFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.dao.ClearedTransaction
import cash.z.wallet.sdk.data.*
import cash.z.wallet.sdk.secure.Wallet
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

class HomePresenter @Inject constructor(
    private val view: HomeFragment,
    private val synchronizer: DataSynchronizer
) : Presenter {

    private var job: Job? = null

    interface HomeView : PresenterView {
        fun setTransactions(transactions: List<ClearedTransaction>)
        fun updateBalance(old: Long, new: Long)
        fun setActiveTransactions(activeTransactionMap: Map<ActiveTransaction, TransactionState>)
        fun onCancelledTooLate()
        fun onSynchronizerError(error: Throwable?): Boolean
    }

    override suspend fun start() {
//        job?.cancel()
//        job = Job()
//        twig("homePresenter starting! from ${this.hashCode()}")
//        with(view) {
//            launchBalanceBinder(synchronizer.balances())
//            launchTransactionBinder(synchronizer.allTransactions())
//            launchActiveTransactionMonitor(synchronizer.activeTransactions())
//        }
//        synchronizer.onSynchronizerErrorListener = view::onSynchronizerError
    }

    override fun stop() {
        twig("homePresenter stopping!")
        job?.cancel()?.also { job = null }
    }

    private fun CoroutineScope.launchBalanceBinder(channel: ReceiveChannel<Wallet.WalletBalance>) = launch {
        var old: Long? = null
        twig("balance binder starting!")
        for (new in channel) {
            twig("polled a balance item")
            bind(old, new.total).also { old = new.total }
        }
        twig("balance binder exiting!")
    }

    private fun CoroutineScope.launchTransactionBinder(channel: ReceiveChannel<List<ClearedTransaction>>) = launch {
        twig("transaction binder starting!")
        for (clearedTransactionList in channel) {
            twig("received ${clearedTransactionList.size} transactions for presenting")
            bind(clearedTransactionList)
        }
        twig("transaction binder exiting!")
    }

    private fun CoroutineScope.launchActiveTransactionMonitor(channel: ReceiveChannel<Map<ActiveTransaction, TransactionState>>) = launch {
        twig("active transaction monitor starting!")
        for (i in channel) {
            bind(i)
        }
        twig("active transaction monitor exiting!")
    }


    //
    // View Callbacks on Main Thread
    //

    private fun bind(old: Long?, new: Long) {
        twig("binding balance of $new")
        view.updateBalance(old ?: 0L, new)
    }


    private fun bind(transactions: List<ClearedTransaction>) {
        twig("binding ${transactions.size} clearedTransactions")
        view.setTransactions(transactions.sortedByDescending {
            if (!it.isMined && it.isSend) Long.MAX_VALUE else it.timeInSeconds
        })
    }

    private fun bind(activeTransactionMap: Map<ActiveTransaction, TransactionState>) {
        twig("binding a.t. map of size ${activeTransactionMap.size}")
        if (activeTransactionMap.isNotEmpty()) view.setActiveTransactions(activeTransactionMap)
    }

    fun onCancelActiveTransaction(transaction: ActiveSendTransaction) {
//        twig("requesting to cancel send for transaction ${transaction.internalId}")
//        val isTooLate = !synchronizer.cancelSend(transaction)
//        if (isTooLate) {
//            view.onCancelledTooLate()
//        }
    }

}

@Module
abstract class HomePresenterModule {
    @Binds
    @Singleton
    abstract fun providePresenter(homePresenter: HomePresenter): Presenter
}