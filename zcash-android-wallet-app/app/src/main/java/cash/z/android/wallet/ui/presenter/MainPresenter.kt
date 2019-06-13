package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.activity.MainActivity
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.*
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainPresenter @Inject constructor(
    private val view: MainActivity,
    private val synchronizer: Synchronizer
) : Presenter {

    interface MainView : PresenterView {
        fun orderFailed(error: PurchaseResult.Failure)
        fun orderUpdated(processing: PurchaseResult.Processing)
    }

    private var purchaseJob: Job? = null


    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("MainPresenter")
        twig("mainPresenter starting!")

        purchaseJob?.cancel()
        purchaseJob = Job()
        purchaseJob = view.launchPurchaseBinder(synchronizer.activeTransactions())
    }

    override fun stop() {
        twig("mainPresenter stopping!")
        Twig.clip("MainPresenter")
        purchaseJob?.cancel()?.also { purchaseJob = null }
    }

    fun CoroutineScope.launchPurchaseBinder(channel: ReceiveChannel<Map<ActiveTransaction, TransactionState>>) = launch {
        twig("main purchase binder starting!")
        for (new in channel) {
            twig("main polled a purchase info")
            bind(new)
        }
        twig("main purchase binder exiting!")
    }


    //
    // Events
    //

    private fun bind(activeTransactions: Map<ActiveTransaction, TransactionState>) {
        val newestState = activeTransactions.entries.last().value
        if (newestState is TransactionState.Failure) {
            view.orderFailed(PurchaseResult.Failure(newestState.reason))
        } else {
            view.orderUpdated(PurchaseResult.Processing(newestState))
        }
    }

    sealed class PurchaseResult {
        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
        data class Failure(val reason: String = "") : PurchaseResult()
    }
}


@Module
abstract class MainPresenterModule {
    @Binds
    abstract fun providePresenter(mainPresenter: MainPresenter): Presenter
}