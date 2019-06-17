package cash.z.android.wallet.data

import androidx.room.Room
import androidx.room.RoomDatabase
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.db.PendingTransactionDao
import cash.z.android.wallet.data.db.PendingTransactionDb
import cash.z.android.wallet.data.db.PendingTransactionEntity
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.service.LightWalletService
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class PersistentTransactionManager : TransactionManager {
    private var db: PendingTransactionDb? = null
    private var dao: PendingTransactionDao? = null

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

    override suspend fun manageCreation(encoder: RawTransactionEncoder, value: Long, toAddress: String, memo: String) = withContext(IO){
        if (db == null) start()
        twig("managing the creation of a transaction")
        var tx = PendingTransactionEntity(value = value, address = toAddress, memo = memo)
        try {
            twig("beginning to encode transaction with : $encoder")
            val raw = encoder.create(value, toAddress, memo)
            twig("successfully encoded transaction : $raw")
            tx = tx.copy(raw = raw)
            twig("inserting tx into DB (dao null? ${dao == null}): $tx")
            dao?.insert(tx) // We only insert successful transactions into the DB because right now, we don't currently retry encoding (but we could, if we changed manageSubmission)
            twig("successfully inserted TX into DB")
        } catch (t: Throwable) {
            twig("failed to encode transaction due to : ${t.message}")
        }
    }

    override suspend fun manageSubmission(service: LightWalletService, pendingTransaction: RawTransaction) {
        if (db == null) start()
        try {
            // TODO: stuff before you send
            twig("managing the preparation to submit transaction")
            val response = service.submitTransaction(pendingTransaction.raw!!)
            twig("management of submit transaction completed with response: ${response.errorCode}: ${response.errorMessage}")
            // TODO: stuff after sending
            if (response.errorCode < 0) {
            } else {
            }
        } catch (t: Throwable) {
            twig("error while managing submitting transaction: ${t.message} caused by: ${t.cause}")
        }
    }

    override suspend fun getAllPending(currentHeight: Int): List<PendingTransactionEntity> {
        return dao?.getAllPending(currentHeight) ?: listOf()
    }

}