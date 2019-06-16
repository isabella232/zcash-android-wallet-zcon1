package cash.z.android.wallet.ui.util

import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.StaticTransactionRepository
import cash.z.android.wallet.data.TransactionSender
import cash.z.android.wallet.data.WalletTransactionEncoder
import cash.z.android.wallet.extention.toDbPath
import cash.z.android.wallet.extention.tryIgnore
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.jni.RustBackendWelding
import cash.z.wallet.sdk.secure.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty

class Broom(
    private val sender: TransactionSender,
    private val rustBackend: RustBackendWelding,
    private val cacheDbName: String,
    private val appWallet: Wallet
) {

    private val repository =  StaticTransactionRepository(DATA_DB_NAME, rustBackend)

    /**
     * Gets the seed from the provider and sweeps the associated wallet
     */
    suspend fun sweep(walletSeedProvider: ReadOnlyProperty<Any?, ByteArray>, amount: Long = 100_000_000L - MINERS_FEE_ZATOSHI): String? = withContext(Dispatchers.IO){
        Twig.sprout("sweep")
        // copy cache db
//        cloneCachedBlocks() // optional?

        try {
            val encoder = initEncoder(walletSeedProvider)
            // verify & scan
            //TODO: for now, assume validation is happening elsewhere and just scan here
            Twig.sprout("broom-scan")
            twig("scanning database at ${DATA_DB_NAME.toDbPath()}")
            val scanResult = rustBackend.scanBlocks(cacheDbName.toDbPath(), DATA_DB_NAME.toDbPath())
            Twig.clip("broom-scan")
            if (scanResult) {
                twig("successfully scanned blocks! Ready to sweep!!!")
                sender.sendToAddress(encoder, amount, appWallet.getAddress())
            } else {
                twig("failed to scan!")
            }
            null
        } catch (t: Throwable) {
            val message = "Failed to sweep due to: ${t.message}"
            twig(message)
            message
        } finally {
            Twig.clip("sweep")
        }
    }

    private fun initEncoder(seedProvider: ReadOnlyProperty<Any?, ByteArray>): WalletTransactionEncoder {
        // TODO: maybe let this one live and make a new one?
        DATA_DB_PATH.absoluteFile.delete()
        val wallet = Wallet(
            ZcashWalletApplication.instance,
            rustBackend,
            DATA_DB_PATH.absolutePath,
            ZcashWalletApplication.instance.cacheDir.absolutePath,
            arrayOf(0),
            seedProvider,
            Delegates.notNull()
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
