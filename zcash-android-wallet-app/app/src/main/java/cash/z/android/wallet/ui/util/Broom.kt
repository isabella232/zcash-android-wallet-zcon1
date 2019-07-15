package cash.z.android.wallet.ui.util

import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.StaticTransactionRepository
import cash.z.android.wallet.extention.toDbPath
import cash.z.android.wallet.extention.tryIgnore
import cash.z.wallet.sdk.data.TransactionSender
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.WalletTransactionEncoder
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.entity.PendingTransaction
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.jni.RustBackendWelding
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty

class Broom(
    val sender: TransactionSender,
    private val rustBackend: RustBackendWelding,
    private val cacheDbName: String,
    private val appWallet: Wallet
) {

    private val repository =  StaticTransactionRepository(DATA_DB_NAME, rustBackend)

    /**
     * Gets the seed from the provider and sweeps the associated wallet
     */
    suspend fun sweep(walletSeedProvider: ReadOnlyProperty<Any?, ByteArray>, amount: Long = 100_000_000L - MINERS_FEE_ZATOSHI, memo: String = ""): String? = withContext(Dispatchers.IO){
        Twig.sprout("sweep")
        // copy cache db
//        cloneCachedBlocks() // optional?

        var tx = PendingTransaction()
        try {
            tx = sender.prepareTransaction(amount, appWallet.getAddress(), memo)!!
            val encoder = initEncoder(walletSeedProvider)
            // verify & scan
            //TODO: for now, assume validation is happening elsewhere and just scan here
            Twig.sprout("broom-scan")
            twig("scanning database at ${DATA_DB_NAME.toDbPath()}")
            val scanResult = rustBackend.scanBlocks(cacheDbName.toDbPath(), DATA_DB_NAME.toDbPath())
            Twig.clip("broom-scan")
            if (scanResult) {
                twig("successfully scanned blocks! Ready to sweep!!!")
                twig("FYI: this wallet has a starting balance of : ${rustBackend.getBalance(DATA_DB_NAME.toDbPath(), 0).convertZatoshiToZecString(6)}")
                tx = sender.sendPreparedTransaction(encoder, tx)
            } else {
                twig("failed to scan!")
            }
            tx.errorMessage
        } catch (t: Throwable) {
            val message = "Failed to sweep due to: ${t.message} caused by ${t.cause}"
            twig(message)
            tx = tx.copy(errorMessage = "$message and ${tx.errorMessage}")
            message
        } finally {
            Twig.clip("sweep")
            Twig.clip("broom-scan")
            // delete if raw=null because it's not sendable so it would just sit in the UI forever. Deleting and showing an alert is easier than retrying send later.
            if (tx.id >= 0) sender.cleanupPreparedTransaction(tx).also { twig("cleaning up prepared transaction") }
        }
    }

    private fun initEncoder(seedProvider: ReadOnlyProperty<Any?, ByteArray>): WalletTransactionEncoder {
        // TODO: maybe let this one live and make a new one?
        DATA_DB_PATH.absoluteFile.delete()
        val wallet = Wallet(
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
        return WalletTransactionEncoder(wallet, repository)
    }

    companion object {
        private const val DATA_DB_NAME = "BroomData.db"
        private val DATA_DB_PATH: File = ZcashWalletApplication.instance.getDatabasePath(DATA_DB_NAME)
    }

}
