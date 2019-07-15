package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.databinding.FragmentZcon1HomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.adapter.TransactionAdapter
import cash.z.android.wallet.ui.adapter.TransactionUiModel
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenterModule
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.entity.ClearedTransaction
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
class Zcon1HomeFragment : BaseFragment(), BalancePresenter.BalanceView, TransactionPresenter.TransactionView {

    private lateinit var binding: FragmentZcon1HomeBinding
    private var statusJob: Job? = null

    @Inject
    lateinit var transactionPresenter: TransactionPresenter

//    @Inject
//    lateinit var chipBucket: ChipBucket

    private val balanceInfo: Wallet.WalletBalance get() = mainActivity?.balancePresenter?.lastBalance!!

    private var transactions: List<TransactionUiModel> = emptyList()



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
//        Toaster.short("Found args: $navArguments")
//        Toaster.long("arguments: ${arguments?.getString("sender")}")
        binding.backgroundBalanceImage.setOnClickListener {
            mainActivity?.onShowStatus()
        }
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
                mainActivity?.handleDeepLink()
            }
        }

        binding.indicatorStatus.backgroundTintList = ZcashWalletApplication.instance.resources.getColorStateList(statusColor)

//        binding.indicatorStatus.backgroundTintList = ContextCompat
//            .getColorStateList(ZcashWalletApplication.instance, statusColor)
        binding.textStatus.text = statusMessage
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
//        chipBucket.setOnBucketChangedListener(this)
//        chipBucket.setOnBucketChangedListener(this)
        launch {
            transactionPresenter.start()
            statusJob = startStatusMonitor()
        }
    }

    override fun onPause() {
        super.onPause()
        mainActivity?.balancePresenter?.removeBalanceView(this)
//        chipBucket.removeOnBucketChangedListener(this)
        transactionPresenter.stop()
        statusJob?.cancel().also { statusJob = null }
    }

    fun refreshBalance() {
        val valuePending = mainActivity?.calculatePendingChipBalance() ?: 0L
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


    //
    // TransactionView Implementation
    //
    
    override fun setTransactions(transactions: List<TransactionUiModel>) {
        this.transactions = transactions
        refreshTransactions()
    }

    fun refreshTransactions() {
        with (binding.recyclerTransactionHistory) {
            (adapter as TransactionAdapter).submitList(transactions)
            postDelayed({
                smoothScrollToPosition(0)
            }, 150L)
        }
    }

//    private fun addPokerChips(transactions: List<ClearedTransaction>): MutableList<ClearedTransaction> {
//        val mergedTransactions = mutableListOf<ClearedTransaction>()
//        mergedTransactions.addAll(transactions)
//        chipBucket.forEach { chip ->
//            // once the transaction is sent, we no longer need the bucket to provide chip information because the synchronizer.sender is now in charge of tracking the chip
//            val memo = chip.toMemo()
//            if (transactions.none { it.memo == memo }) mergedTransactions.add(chip.toClearedTransaction())
//        }
//        mergedTransactions.sortByDescending {
//            if (!it.isMined && it.isSend) Long.MAX_VALUE else it.timeInSeconds
//        }
//        return mergedTransactions
//    }
}

//private fun PokerChip.toClearedTransaction(): ClearedTransaction {
//    return ClearedTransaction(
//        value = zatoshiValue - MINERS_FEE_ZATOSHI,
//        isSend = true,
//        timeInSeconds = created/1000L,
//        status = "Verifying funds...",
//        address = "PokerChip",
//        memo = toMemo()
//    )
//}


@Module
abstract class Zcon1HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(modules = [TransactionPresenterModule::class])
    abstract fun contributeZcon1HomeFragment(): Zcon1HomeFragment
}

