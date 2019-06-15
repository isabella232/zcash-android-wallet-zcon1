package cash.z.android.wallet.data

import androidx.lifecycle.LifecycleOwner
import cash.z.android.wallet.ui.util.BaseLifecycleObserver

/**
 * A synchronizer that attempts to remain operational, despite any number of errors that can occur.
 *
 * @param lifecycleOwner the lifecycle to observe while synchronizing
 */
class StableSynchronizer(
    private val lifecycleOwner: LifecycleOwner,
    sender: TransactionSender
) : BaseLifecycleObserver(), TransactionSender by sender {
    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    //
    // Lifecycle
    //

    override fun onCreate(owner: LifecycleOwner) {

    }

    override fun onDestroy(owner: LifecycleOwner) {
    }


//    override fun activeTransactions(): Flow<Pair<ActiveTransaction, TransactionState>> {
//    }
//
//    override fun transactions(): Flow<WalletTransaction> {
//    }
//
//    override fun balance(): Flow<Wallet.WalletBalance> {
//    }
//
//    override fun progress(): Flow<Int> {
//    }
//
//    override fun status(): Flow<FlowSynchronizer.SyncStatus> {
//    }

}