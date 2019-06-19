package cash.z.android.wallet.data

import androidx.room.Room
import androidx.room.RoomDatabase
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.db.PendingTransactionDao
import cash.z.android.wallet.data.db.PendingTransactionDb
import cash.z.android.wallet.data.db.PendingTransactionEntity
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.EXPIRY_OFFSET
import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * Facilitates persistent attempts to ensure a transaction occurs.
 */
// TODO: consider having the manager register the fail listeners rather than having that responsibility spread elsewhere (synchronizer and the broom)
class PersistentTransactionManager : TransactionManager {
    private var db: PendingTransactionDb? = null
    protected var dao: PendingTransactionDao? = null

    override fun start() {
        twig("TransactionManager starting")
        db = Room.databaseBuilder(
            ZcashWalletApplication.instance,
            PendingTransactionDb::class.java,
            "PendingTransactions.db"
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        dao = db!!.pendingTransactionDao()
    }

    override fun stop() {
        twig("TransactionManager stopping")
        db?.close().also { db = null; dao = null }
    }

    suspend fun initPlaceholder(
        zatoshiValue: Long,
        toAddress: String,
        memo: String
    ): PendingTransactionEntity? = withContext(IO) {
        requireDb()
        twig("constructing a placeholder transaction")
        var tx = initTransaction(zatoshiValue, toAddress, memo)
        twig("done constructing a placeholder transaction")
        try {
            twig("inserting tx into DB (dao null? ${dao == null}): $tx")
            val insertId = dao?.insert(tx)
            twig("insert returned id of $insertId")
            tx.copy(id = insertId!!)
        } catch (t: Throwable) {
            val message = "failed initialize a placeholder transaction due to : ${t.message} caused by: ${t.cause}"
            twig(message)
            null
        } finally {
            twig("done constructing a placeholder transaction")
        }
    }

    override suspend fun manageCreation(
        encoder: RawTransactionEncoder,
        zatoshiValue: Long,
        toAddress: String,
        memo: String,
        currentHeight: Int
    ): PendingTransactionEntity = manageCreation(encoder, initTransaction(zatoshiValue, toAddress, memo), currentHeight)


    suspend fun manageCreation(
        encoder: RawTransactionEncoder,
        transaction: PendingTransactionEntity,
        currentHeight: Int
    ): PendingTransactionEntity = withContext(IO){
        requireDb()
        twig("managing the creation of a transaction")
        var tx = transaction.copy(expiryHeight = if (currentHeight == -1) -1 else currentHeight + EXPIRY_OFFSET)
        try {
            twig("beginning to encode transaction with : $encoder")
            val encodedTx = encoder.create(tx.value, tx.address, tx.memo)
            twig("successfully encoded transaction for ${tx.memo}!!")
            tx = tx.copy(raw = encodedTx.raw, txId = encodedTx.txId)
            tx
        } catch (t: Throwable) {
            val message = "failed to encode transaction due to : ${t.message} caused by: ${t.cause}"
            twig(message)
            message
            tx = tx.copy(errorMessage = message)
            tx
        } finally {
            tx = tx.copy(encodeAttempts = Math.max(1, tx.encodeAttempts + 1))
            twig("inserting tx into DB (dao null? ${dao == null}): $tx")
            dao?.insert(tx)
            twig("successfully inserted TX into DB")
            tx
        }
    }

    override suspend fun manageSubmission(service: LightWalletService, pendingTransaction: RawTransaction) {
        requireDb()
        var tx = pendingTransaction as PendingTransactionEntity
        try {
            twig("managing the preparation to submit transaction memo: ${tx.memo} amount: ${tx.value}")
            val response = service.submitTransaction(pendingTransaction.raw!!)
            twig("management of submit transaction completed with response: ${response.errorCode}: ${response.errorMessage}")
            if (response.errorCode < 0) {
                tx = tx.copy(errorMessage = response.errorMessage, errorCode = response.errorCode)
            } else {
                tx = tx.copy(errorMessage = null, errorCode = response.errorCode)
            }
        } catch (t: Throwable) {
            twig("error while managing submitting transaction: ${t.message} caused by: ${t.cause}")
        } finally {
            tx = tx.copy(submitAttempts = Math.max(1, tx.submitAttempts + 1))
            dao?.insert(tx)
        }
    }

    override suspend fun getAll(): List<PendingTransactionEntity> = withContext(IO) {
        requireDb()
        dao?.getAll() ?: listOf()
    }

    private fun initTransaction(
        value: Long,
        toAddress: String,
        memo: String,
        currentHeight: Int = -1
    ): PendingTransactionEntity {
        return PendingTransactionEntity(
            value = value,
            address = toAddress,
            memo = memo,
            expiryHeight = if (currentHeight == -1) -1 else currentHeight + EXPIRY_OFFSET
        )
    }

    suspend fun manageMined(pendingTx: PendingTransactionEntity, matchingMinedTx: PendingTransactionEntity) = withContext(IO) {
        require(matchingMinedTx.minedHeight > 0) TODO: find why this breaks

        requireDb()
        twig("a pending transaction has been mined!")

        val tx = pendingTx.copy(minedHeight = matchingMinedTx.minedHeight)
        dao?.insert(tx)
    }

    /**
     * Remove a transaction and pretend it never existed. This is helpful for poker chips that we want to scan again.
     */
    suspend fun abortTransaction(existingTransaction: PendingTransactionEntity) = withContext(IO) {
        requireDb()
        dao?.delete(existingTransaction)
    }

    fun requireDb() {
        if (db == null) start()
    }

}