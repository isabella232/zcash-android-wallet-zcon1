package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BalancePresenter {

    private val views: MutableSet<BalanceView> = mutableSetOf()
    var lastBalance: Wallet.WalletBalance = Wallet.WalletBalance(0, 0)

    /**
     * Contract for views showing balance updates.
     */
    interface BalanceView : PresenterView {
        fun updateBalance(balanceInfo: Wallet.WalletBalance)
    }

    private var balanceJob: Job? = null


    //
    // LifeCycle
    //

    fun start(scope: CoroutineScope, balanceChannel: ReceiveChannel<Wallet.WalletBalance>) {
        Twig.sprout("BalancePresenter")
        twig("balancePresenter starting!")
        balanceJob?.cancel()
        balanceJob = Job()
        balanceJob = scope.launchBalanceBinder(balanceChannel)
    }

    fun stop() {
        twig("balancePresenter stopping!")
        Twig.clip("BalancePresenter")
        balanceJob?.cancel()?.also { balanceJob = null }
    }

    private fun CoroutineScope.launchBalanceBinder(channel: ReceiveChannel<Wallet.WalletBalance>) = launch {
        twig("balance binder starting!")
        for (new in channel) {
            twig("polled a balance item")
            bind(new)
        }
        twig("balance binder exiting!")
    }


    //
    // Public API
    //

    fun addBalanceView(view: BalanceView) {
        if (views.add(view)) {
            view.updateBalance(lastBalance)
        }
    }

    fun removeBalanceView(view: BalanceView) {
        views.remove(view)
    }


    //
    // Events
    //

    private fun bind(balanceInfo: Wallet.WalletBalance) {
        lastBalance = balanceInfo
        twig("binding balance $balanceInfo")
        for (view in views) {
            if (view.isActive) view.updateBalance(balanceInfo)
        }
    }

}
