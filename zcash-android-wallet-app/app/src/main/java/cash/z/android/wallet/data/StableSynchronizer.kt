package cash.z.android.wallet.data

import cash.z.wallet.sdk.data.ActiveTransaction
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A synchronizer that attempts to remain operational, despite any number of errors that can occur.
 */
@ExperimentalCoroutinesApi
class StableSynchronizer @Inject constructor(
    private val wallet: Wallet,
    private val encoder: RawTransactionEncoder,
    private val sender: TransactionSender
) : DataSyncronizer {

    private val balanceChannel = ConflatedBroadcastChannel<Wallet.WalletBalance>()
    private val progressChannel = ConflatedBroadcastChannel<Int>()
    private val pendingChannel = ConflatedBroadcastChannel<List<PendingTransaction>>()

    override fun start(scope: CoroutineScope) {
        twig("Staring sender!")
        sender.start(scope)
    }

    override fun stop() {
        sender.stop()
    }


    //
    // Channels
    //

    override fun balances(): ReceiveChannel<Wallet.WalletBalance> {
        return balanceChannel.openSubscription()
    }

    override fun progress(): ReceiveChannel<Int> {
        return progressChannel.openSubscription()
    }

    override fun pendingTransactions(): ReceiveChannel<List<PendingTransaction>> {
        return pendingChannel.openSubscription()
    }


    //
    // Send / Receive
    //

    override suspend fun getAddress(accountId: Int): String = withContext(IO) { wallet.getAddress() }

    override suspend fun sendToAddress(
        zatoshi: Long,
        toAddress: String,
        memo: String,
        fromAccountId: Int
    ) = withContext(IO) {
        sender.sendToAddress(encoder, zatoshi, toAddress, memo, fromAccountId)
    }


//    override fun activeTransactions(): Flow<Pair<ActiveTransaction, TransactionState>> {
//    }
//
//    override fun transactions(): Flow<WalletTransaction> {
//    }
//
//    override fun progress(): Flow<Int> {
//    }
//
//    override fun status(): Flow<FlowSynchronizer.SyncStatus> {
//    }

}

interface DataSyncronizer {
    fun start(scope: CoroutineScope)
    fun stop()

    suspend fun getAddress(accountId: Int = 0): String
    suspend fun sendToAddress(zatoshi: Long, toAddress: String, memo: String = "", fromAccountId: Int = 0)

    fun balances(): ReceiveChannel<Wallet.WalletBalance>
    fun progress(): ReceiveChannel<Int>
    fun pendingTransactions(): ReceiveChannel<List<PendingTransaction>>
}