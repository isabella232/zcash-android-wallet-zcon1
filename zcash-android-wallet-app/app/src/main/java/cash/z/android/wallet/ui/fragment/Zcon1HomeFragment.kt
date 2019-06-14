package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cash.z.android.wallet.ChipBucket
import cash.z.android.wallet.PokerChip
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.FragmentZcon1HomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.adapter.TransactionAdapter
import cash.z.android.wallet.ui.dialog.StatusDialog
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenter
import cash.z.android.wallet.ui.presenter.TransactionPresenterModule
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1HomeFragment : BaseFragment(), BalancePresenter.BalanceView, TransactionPresenter.TransactionView,
    ChipBucket.OnBucketChangedListener {

    //, SwipeRefreshLayout.OnRefreshListener
    private lateinit var binding: FragmentZcon1HomeBinding

    @Inject
    lateinit var transactionPresenter: TransactionPresenter

    @Inject
    lateinit var chipBucket: ChipBucket

    lateinit var balanceInfo: Wallet.WalletBalance
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
        StatusDialog(balanceInfo.available).show(activity!!.supportFragmentManager, "dialog_status")
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
        chipBucket.setOnBucketChangedListener(this)
    }

    override fun onStart() {
        super.onStart()
        mainActivity?.balancePresenter?.addBalanceView(this)
        chipBucket.setOnBucketChangedListener(this)
        launch {
            transactionPresenter.start()
        }
    }

    override fun onStop() {
        super.onStop()
        mainActivity?.balancePresenter?.removeBalanceView(this)
        chipBucket.removeOnBucketChangedListener(this)
        transactionPresenter.stop()
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

    //
    // Balance listeners
    //

    override fun updateBalance(balanceInfo: Wallet.WalletBalance) {
        this.balanceInfo = balanceInfo
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
            }, 100L)
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

