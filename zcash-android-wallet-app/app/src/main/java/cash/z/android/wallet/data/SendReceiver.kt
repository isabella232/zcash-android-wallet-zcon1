package cash.z.android.wallet.data

import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_CHOSEN_COMPONENT
import cash.z.android.wallet.PokerChipSeedProvider
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.extention.Toaster
import cash.z.android.wallet.extention.tryIgnore
import cash.z.wallet.sdk.data.DataSynchronizer
import cash.z.wallet.sdk.data.StableSynchronizer
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZecToZatoshi
import cash.z.wallet.sdk.ext.safelyConvertToBigDecimal
import cash.z.wallet.sdk.jni.RustBackendWelding
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.android.DaggerBroadcastReceiver
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty


class SendReceiver : DaggerBroadcastReceiver() {

    @Inject
    lateinit var synchronizer: DataSynchronizer

    @Inject
    lateinit var rustBackend: RustBackendWelding

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val name = intent.getStringExtra("name")
        val amount = intent.getStringExtra("amount")
        val uuid = intent.getStringExtra("uuid")

        val pokerId = "c-$amount-$name-$uuid"
        val wallet = initWallet(PokerChipSeedProvider(pokerId))

        val selectedAppPackage = intent.extras.get(EXTRA_CHOSEN_COMPONENT)?.toString()
        Toaster.center("Sending $amount TAZ in the background!")
        (synchronizer as StableSynchronizer).coroutineScope.launch {
            synchronizer.sendToAddress(amount.safelyConvertToBigDecimal().convertZecToZatoshi() + (MINERS_FEE_ZATOSHI * 2), wallet.getAddress(), "Shared funds from $name")
        }
    }


//
//    private val repository =  StaticTransactionRepository(DATA_DB_NAME, rustBackend)
//
//    /**
//     * Gets the seed from the provider and sweeps the associated wallet
//     */
//    suspend fun sweep(walletSeedProvider: ReadOnlyProperty<Any?, ByteArray>, amount: Long = 100_000_000L - MINERS_FEE_ZATOSHI, memo: String = ""): String? = withContext(
//        Dispatchers.IO){
//        Twig.sprout("sweep")
//        // copy cache db
////        cloneCachedBlocks() // optional?
//
//        var tx = PendingTransaction()
//        try {
//            // verify & scan
//            //TODO: for now, assume validation is happening elsewhere and just scan here
//            Twig.sprout("broom-scan")
//            twig("scanning database at ${DATA_DB_NAME.toDbPath()}")
//            val scanResult = rustBackend.scanBlocks(cacheDbName.toDbPath(), DATA_DB_NAME.toDbPath())
//            Twig.clip("broom-scan")
//            if (scanResult) {
//                twig("successfully scanned blocks! Ready to sweep!!!")
//                twig("FYI: this wallet has a starting balance of : ${rustBackend.getBalance(DATA_DB_NAME.toDbPath(), 0).convertZatoshiToZecString(6)}")
//                tx = sender.sendPreparedTransaction(encoder, tx)
//            } else {
//                twig("failed to scan!")
//            }
//            tx.errorMessage
//        } catch (t: Throwable) {
//            val message = "Failed to sweep due to: ${t.message} caused by ${t.cause}"
//            twig(message)
//            tx = tx.copy(errorMessage = "$message and ${tx.errorMessage}")
//            message
//        } finally {
//            Twig.clip("sweep")
//            Twig.clip("broom-scan")
//            // delete if raw=null because it's not sendable so it would just sit in the UI forever. Deleting and showing an alert is easier than retrying send later.
//            if (tx.id >= 0) sender.cleanupPreparedTransaction(tx).also { twig("cleaning up prepared transaction") }
//        }
//    }

    private fun initWallet(seedProvider: ReadOnlyProperty<Any?, ByteArray>): Wallet {
        // TODO: maybe let this one live and make a new one?
        DATA_DB_PATH.absoluteFile.delete()
        return Wallet(
            context = ZcashWalletApplication.instance,
            birthday = Wallet.loadBirthdayFromAssets(ZcashWalletApplication.instance, 523240),
            rustBackend = rustBackend,
            dataDbName = DATA_DB_NAME,
            seedProvider = seedProvider,
            spendingKeyProvider = Delegates.notNull()
        ).also {
            tryIgnore {
                it.initialize()
            }
        }
    }

    companion object {
        private const val DATA_DB_NAME = "SharedFunds.db"
        private val DATA_DB_PATH: File = ZcashWalletApplication.instance.getDatabasePath(DATA_DB_NAME)
    }
}


@Module
abstract class SendReceiverModule {
    @ContributesAndroidInjector
    abstract fun contributeSendReceiver(): SendReceiver
}
