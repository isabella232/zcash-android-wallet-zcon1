package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.Zcon1Store
import cash.z.android.wallet.Zcon1Store.address
import cash.z.android.wallet.ui.activity.MainActivity
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.entity.PendingTransaction
import cash.z.wallet.sdk.entity.isFailure
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
        purchaseJob = view.launchPurchaseBinder(synchronizer.pendingTransactions())
    }

    override fun stop() {
        twig("mainPresenter stopping!")
        Twig.clip("MainPresenter")
        purchaseJob?.cancel()?.also { purchaseJob = null }
    }

    private fun CoroutineScope.launchPurchaseBinder(channel: ReceiveChannel<List<PendingTransaction>>) = launch {
        twig("main purchase binder starting!")
        for (new in channel) {
            val mostRecent = new.sortedByDescending { it.createTime }.firstOrNull()
            if (mostRecent?.isSwag() == true) {
                twig("main polled a swag purchase")
                bind(mostRecent)
            }
        }
        twig("main purchase binder exiting!")
    }


    //
    // Events
    //

    private fun bind(swagPurchase: PendingTransaction) {
        if (swagPurchase.isFailure()) {
            view.orderFailed(PurchaseResult.Failure(swagPurchase.errorMessage))
        } else {
            view.orderUpdated(PurchaseResult.Processing(swagPurchase))
        }
    }

    sealed class PurchaseResult {
        data class Processing(val pendingTransaction: PendingTransaction) : PurchaseResult()
        data class Failure(val reason: String? = "") : PurchaseResult()
    }
}

private fun PendingTransaction.isSwag(): Boolean {
    return address == Zcon1Store.address && (memo?.toLowerCase()?.contains("swag") == true)
}


@Module
abstract class MainPresenterModule {
    @Binds
    abstract fun providePresenter(mainPresenter: MainPresenter): Presenter
}