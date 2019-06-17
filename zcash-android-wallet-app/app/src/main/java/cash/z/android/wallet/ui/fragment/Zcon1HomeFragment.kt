package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cash.z.android.wallet.*
import cash.z.android.wallet.databinding.FragmentZcon1HomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.adapter.TransactionAdapter
import cash.z.android.wallet.ui.dialog.StatusDialog
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenterModule
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.*
import javax.inject.Inject


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1HomeFragment : BaseFragment(), BalancePresenter.BalanceView, TransactionPresenter.TransactionView,
    ChipBucket.OnBucketChangedListener {

    private lateinit var binding: FragmentZcon1HomeBinding
    private var statusJob: Job? = null

    @Inject
    lateinit var transactionPresenter: TransactionPresenter

    @Inject
    lateinit var chipBucket: ChipBucket

    private val balanceInfo: Wallet.WalletBalance get() = mainActivity?.balancePresenter?.lastBalance!!

    private var transactions: List<WalletTransaction> = emptyList()


    //
    // LifeCycle
    //

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return DataBindingUtil.inflate<FragmentZcon1HomeBinding>(
            inflater, R.layout.fragment_zcon1_home, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backgroundBalanceImage.setOnClickListener {
            showStatus()
        }
    }

    private fun showStatus() {
        StatusDialog(
            availableBalance = balanceInfo.available,
            syncingBalance = balanceInfo.total - balanceInfo.available,
            pendingChipBalance = chipBucket.valuePending(),
            summary = determineStatusSummary()
        ).show(activity!!.supportFragmentManager, "dialog_status")
    }

    private fun updateStatusIndicator() {
        @ColorRes var statusColor: Int
        var statusMessage: String
        when {
            mainActivity?.synchronizer?.isConnected == false -> {
                statusColor = R.color.zcashRed
                statusMessage = "disconnected"
            }
            mainActivity?.synchronizer?.isScanning == true -> {
                statusColor = R.color.zcashYellow
                statusMessage = "scanning"
            }
            mainActivity?.synchronizer?.isSyncing == true -> {
                statusColor = R.color.zcashYellow
                statusMessage = "syncing"
            }
            else -> {
                statusColor = R.color.zcashGreen
                statusMessage = "synced"
            }
        }

        binding.indicatorStatus.backgroundTintList = ContextCompat
            .getColorStateList(ZcashWalletApplication.instance, statusColor)
        binding.textStatus.text = statusMessage
    }

    private fun determineStatusSummary(): String {
        val available = balanceInfo.available
        val total = balanceInfo.total
        val hasIncomingFunds = available < total
        val isUnfunded = total == 0L && chipBucket.count() == 0
        val hasPendingChips = chipBucket.valuePending() > 0
        val hasEnoughForSwag = available > Zcon1Store.CartItem.SwagTee("").zatoshiValue


        val statusResId = when {
            isUnfunded -> R.string.status_wallet_unfunded
            hasEnoughForSwag -> R.string.status_wallet_funds_available_for_swag
            hasIncomingFunds -> R.string.status_wallet_incoming_funds
            hasPendingChips -> R.string.status_wallet_chips_pending
            else -> R.string.status_wallet_generic
        }
        return getString(statusResId) + "\n\nErrors: there was an error. J/k but if there was it would show up here."
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
        mainActivity?.onNavButtonSelected(0)

        binding.recyclerTransactionHistory.apply {
            layoutManager = LinearLayoutManager(mainActivity, RecyclerView.VERTICAL, false)
            adapter = TransactionAdapter(R.layout.item_zcon1_transaction_history)
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.balancePresenter?.addBalanceView(this)
        chipBucket.setOnBucketChangedListener(this)
        chipBucket.setOnBucketChangedListener(this)
        launch {
            transactionPresenter.start()
            statusJob = startStatusMonitor()
        }
    }

    override fun onPause() {
        super.onPause()
        mainActivity?.balancePresenter?.removeBalanceView(this)
        chipBucket.removeOnBucketChangedListener(this)
        transactionPresenter.stop()
        statusJob?.cancel().also { statusJob = null }
    }

    fun refreshBalance() {
        val valuePending = chipBucket.valuePending()
        val balance = (balanceInfo.total + valuePending).convertZatoshiToZecString(6).split(".")
        binding.textIntegerDigits.text = balance[0]
        binding.textFractionalDigits.text = ""
        if (balance.size > 1) {
            binding.textFractionalDigits.text = ".${balance[1]}"
        }
    }

    private fun CoroutineScope.startStatusMonitor() = launch {
        twig("StatusMonitor starting!")
        while (isActive && isAdded) {
            delay(2_000L)
            updateStatusIndicator()
        }
        twig("StatusMonitor stopping!")
    }

    //
    // Balance listeners
    //

    override fun updateBalance(balanceInfo: Wallet.WalletBalance) {
        refreshBalance()
    }

    override fun onBucketChanged(bucket: ChipBucket) {
        refreshBalance()
        refreshTransactions()
    }


    //
    // TransactionView Implementation
    //
    
    override fun setTransactions(transactions: List<WalletTransaction>) {
        this.transactions = transactions
        refreshTransactions()
    }

    fun refreshTransactions() {
        with (binding.recyclerTransactionHistory) {
            (adapter as TransactionAdapter).submitList(addPokerChips(transactions))
            postDelayed({
                smoothScrollToPosition(0)
            }, 150L)
        }
    }

    private fun addPokerChips(transactions: List<WalletTransaction>): MutableList<WalletTransaction> {
        val mergedTransactions = mutableListOf<WalletTransaction>()
        chipBucket.forEach { mergedTransactions.add(it.toWalletTransaction()) }
        mergedTransactions.addAll(transactions)
        mergedTransactions.sortByDescending {
            if (!it.isMined && it.isSend) Long.MAX_VALUE else it.timeInSeconds
        }
        return mergedTransactions
    }
}

private fun PokerChip.toWalletTransaction(): WalletTransaction {
    return WalletTransaction(
        value = zatoshiValue - MINERS_FEE_ZATOSHI,
        isSend = true,
        timeInSeconds = created/1000L,
        address = if (isRedeemed()) "Redeemed" else "Pending...",
        memo = "Poker Chip Scanned"
    )
}


@Module
abstract class Zcon1HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(modules = [TransactionPresenterModule::class])
    abstract fun contributeZcon1HomeFragment(): Zcon1HomeFragment
}

