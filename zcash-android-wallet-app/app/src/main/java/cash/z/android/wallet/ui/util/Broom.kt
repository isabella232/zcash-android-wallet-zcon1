package cash.z.android.wallet.ui.util

import cash.z.android.wallet.PokerChip
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.StaticTransactionRepository
import cash.z.android.wallet.extention.toDbPath
import cash.z.android.wallet.extention.tryIgnore
import cash.z.android.wallet.sample.SampleProperties
import cash.z.wallet.sdk.data.PollingTransactionRepository
import cash.z.wallet.sdk.data.TransactionRepository
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.jni.RustBackendWelding
import cash.z.wallet.sdk.rpc.Service
import cash.z.wallet.sdk.secure.Wallet
import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty

class Broom(
    private val service: LightWalletService,
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
            val wallet = initWallet(walletSeedProvider)
            // verify & scan
            //TODO: for now, assume validation is happening elsewhere and just scan here
            Twig.sprout("broom-scan")
            twig("scanning database at ${DATA_DB_NAME.toDbPath()}")
            val scanResult = rustBackend.scanBlocks(cacheDbName.toDbPath(), DATA_DB_NAME.toDbPath())
            Twig.clip("broom-scan")
            if (scanResult) {
                twig("successfully scanned blocks! Ready to sweep!!!")
                val memo = "swag shirt test"
                val address = "ztestsapling1yu2zy9aanf8pjf2qvm4qmn4k6q57y2d9fcs3vz0guthxx3m2aq57qm6hkx0580m9u9635xh6ttr"
//                val address = appWallet.getAddress()
                val transactionId = wallet.createRawSendTransaction(amount, address).also { checkTx(it) }
                val transactionRaw: ByteArray? = repository.findTransactionById(transactionId)?.raw.also { checkRawTx(it) }
                service.submitTransaction(transactionRaw!!).also { checkResponse(it) }
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

    private fun checkTx(transactionId: Long) {
        if (transactionId < 0) {
            throw SweepException.Creation
        } else {
            twig("successfully created transaction!")
        }
    }

    private fun checkRawTx(transactionRaw: ByteArray?) {
        if (transactionRaw == null) {
            throw SweepException.Disappeared
        } else {
            twig("found raw transaction in the dataDb")
        }
    }

    private fun checkResponse(response: Service.SendResponse) {
        if (response.errorCode < 0) {
            throw SweepException.IncompletePass(response)
        } else {
            twig("successfully submitted. error code: ${response.errorCode}")
        }
    }

    private fun initWallet(seedProvider: ReadOnlyProperty<Any?, ByteArray>): Wallet {
        // TODO: maybe let this one live and make a new one?
        DATA_DB_PATH.absoluteFile.delete()
        return Wallet(
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
    }

    companion object {
        private const val DATA_DB_NAME = "BroomData.db"
        private val DATA_DB_PATH: File = ZcashWalletApplication.instance.getDatabasePath(DATA_DB_NAME)
    }


    sealed class SweepException(val errorMessage: String) : RuntimeException(errorMessage) {
        object Creation : SweepException("failed to create raw transaction")
        object Disappeared : SweepException("unable to find a matching raw transaction. This means the rust backend said it created a TX but when we looked for it in the DB it was missing!")
        class IncompletePass(response: Service.SendResponse) : SweepException("submit failed with error code: ${response.errorCode} and message ${response.errorMessage}")
    }
}
