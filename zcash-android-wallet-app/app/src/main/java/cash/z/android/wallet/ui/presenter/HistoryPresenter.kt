package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.adapter.TransactionUiModel
import cash.z.android.wallet.ui.fragment.HistoryFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.twig
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

class HistoryPresenter @Inject constructor(
    private val view: HistoryFragment,
    private var synchronizer: Synchronizer
) : Presenter {

    private var job: Job? = null

    interface HistoryView : PresenterView {
        fun setTransactions(transactions: List<TransactionUiModel>)
    }

    override suspend fun start() {
        job?.cancel()
        job = Job()
        twig("historyPresenter starting!")
//        view.launchTransactionBinder(synchronizer.allTransactions())
    }

    override fun stop() {
        twig("historyPresenter stopping!")
        job?.cancel()?.also { job = null }
    }

    private fun CoroutineScope.launchTransactionBinder(channel: ReceiveChannel<List<TransactionUiModel>>) = launch {
        twig("transaction binder starting!")
        for (clearedTransactionList in channel) {
            twig("received ${clearedTransactionList.size} transactions for presenting")
            bind(clearedTransactionList)
        }
        twig("transaction binder exiting!")
    }


    //
    // View Callbacks on Main Thread
    //

    private fun bind(transactions: List<TransactionUiModel>) {
        twig("binding ${transactions.size} clearedTransactions")
        view.setTransactions(transactions)
    }

}


@Module
abstract class HistoryPresenterModule {
    @Binds
    @Singleton
    abstract fun providePresenter(historyPresenter: HistoryPresenter): Presenter
}