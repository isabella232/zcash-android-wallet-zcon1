package cash.z.android.wallet.data.db

import androidx.room.*
import cash.z.android.wallet.data.RawTransaction
import cash.z.android.wallet.extention.masked

@Database(
    entities = [
        PendingTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PendingTransactionDb : RoomDatabase() {
    abstract fun pendingTransactionDao(): PendingTransactionDao
}

@Dao
interface PendingTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transaction: PendingTransactionEntity): Long

    @Delete
    fun delete(transaction: PendingTransactionEntity)
//
//    /**
//     * Query all blocks that are not mined and not expired.
//     */
//    @Query(
//        """
//        SELECT id,
//               address,
//               value,
//               memo,
//               minedheight,
//               expiryheight,
//               submitcount,
//               encodecount,
//               errormessage,
//               createtime,
//               raw
//        FROM   pending_transactions
//        WHERE  minedHeight = -1 and (expiryHeight >= :currentHeight or expiryHeight = -1) and (raw IS NOT NULL)
//        ORDER  BY createtime
//    """
//    )
//    fun getAllPending(currentHeight: Int): List<PendingTransactionEntity>

    @Query("SELECT * from pending_transactions ORDER BY createTime")
    fun getAll(): List<PendingTransactionEntity>
}

@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val address: String = "",
    val value: Long = -1,
    val memo: String = "",
    val minedHeight: Int = -1,
    val expiryHeight: Int = -1,
    val submitAttempts: Int = -1,
    /** the number of times there was an attempt to encode this transaction */
    val encodeAttempts: Int = -1,
    val errorMessage: String? = null,
    val errorCode: Int? = null,
    val createTime: Long = System.currentTimeMillis(),
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    override val raw: ByteArray? = null
) : RawTransaction {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingTransactionEntity) return false

        if (id != other.id) return false
        if (address != other.address) return false
        if (value != other.value) return false
        if (memo != other.memo) return false
        if (minedHeight != other.minedHeight) return false
        if (expiryHeight != other.expiryHeight) return false
        if (submitAttempts != other.submitAttempts) return false
        if (encodeAttempts != other.encodeAttempts) return false
        if (errorMessage != other.errorMessage) return false
        if (errorCode != other.errorCode) return false
        if (createTime != other.createTime) return false
        if (raw != null) {
            if (other.raw == null) return false
            if (!raw.contentEquals(other.raw)) return false
        } else if (other.raw != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + memo.hashCode()
        result = 31 * result + minedHeight
        result = 31 * result + expiryHeight
        result = 31 * result + submitAttempts
        result = 31 * result + encodeAttempts
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (errorCode ?: 0)
        result = 31 * result + createTime.hashCode()
        result = 31 * result + (raw?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return if ((raw != null && raw.size > 1) || !address.contains("**mask")) {
            copy(
                raw = byteArrayOf(1),
                address = address.masked()
            ).toString()
        } else {
            super.toString()
        }
    }

}

fun PendingTransactionEntity.isCreating(): Boolean {
    return raw == null && submitAttempts <= 0 && !isFailedSubmit() && !isFailedEncoding()
}

fun PendingTransactionEntity.isFailedEncoding(): Boolean {
    return raw == null && encodeAttempts > 0
}

fun PendingTransactionEntity.isFailedSubmit(): Boolean {
    return errorMessage != null || errorCode != null
}

fun PendingTransactionEntity.isFailure(): Boolean {
    return isFailedEncoding() || isFailedSubmit()
}

fun PendingTransactionEntity.isSubmitted(): Boolean {
    return submitAttempts > 0
}

fun PendingTransactionEntity.isMined(): Boolean {
    return minedHeight > 0
}

fun PendingTransactionEntity.isPending(currentHeight: Int = -1): Boolean {
    // not mined and not expired and successfully created
    return !isSubmitSuccess() && minedHeight == -1 && (expiryHeight == -1 || expiryHeight > currentHeight) && raw != null
}

fun PendingTransactionEntity.isSubmitSuccess(): Boolean {
    return submitAttempts > 0 && (errorCode != null && errorCode >= 0) && errorMessage == null
}

/**
 * The amount of time remaining until this transaction is stale
 */
fun PendingTransactionEntity.ttl(): Long {
    return (60L * 2L) - (System.currentTimeMillis()/1000 - createTime)
}
