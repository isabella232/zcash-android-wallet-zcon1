package cash.z.android.wallet.data

import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.ActiveTransaction
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Like a normal Synchronizer but robust.
 */
interface FlowSynchronizer {
    fun activeTransactions(): Flow<Pair<ActiveTransaction, TransactionState>>
    fun transactions(): Flow<WalletTransaction>
    fun balance(): Flow<Wallet.WalletBalance>
    fun progress(): Flow<Int>
    fun status(): Flow<SyncStatus>

    sealed class SyncStatus(val status: Boolean) {
        class Connected(status: Boolean) : SyncStatus(status)
        class Downloading(status: Boolean) : SyncStatus(status)
        class Scanning(status: Boolean) : SyncStatus(status)
        class Validating(status: Boolean) : SyncStatus(status)
        class Stale(status: Boolean) : SyncStatus(status)
    }

//--------------------------------------------------------
//
//    fun cancelSend(transaction: ActiveSendTransaction): Boolean {
//    }
//
//    fun getAddress(accountId: Int): String {
//    }
//
//    fun getAvailableBalance(accountId: Int): Long {
//    }
//
//    suspend fun isStale(): Boolean {
//    }

}