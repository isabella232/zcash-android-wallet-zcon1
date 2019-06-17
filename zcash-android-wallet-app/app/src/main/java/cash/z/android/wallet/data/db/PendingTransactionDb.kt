package cash.z.android.wallet.data.db

import androidx.room.*
import cash.z.android.wallet.data.RawTransaction

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
    fun insert(transaction: PendingTransactionEntity)

    @Delete
    fun delete(transaction: PendingTransactionEntity)

    /**
     * Query all blocks that are not mined and not expired.
     */
    @Query(
        """
        SELECT id,
               address,
               value,
               memo,
               minedheight,
               expiryheight,
               submitcount,
               encodecount,
               errormessage,
               createtime,
               raw
        FROM   pending_transactions
        WHERE  minedHeight = -1 and (expiryHeight >= :currentHeight) and (raw IS NOT NULL)
        ORDER  BY createtime
    """
    )
    fun getAllPending(currentHeight: Int): List<PendingTransactionEntity>
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
    val submitCount: Int = -1,
    /** the number of times there was an attempt to encode this transaction */
    val encodeCount: Int = -1,
    val errorMessage: String? = null,
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
        if (submitCount != other.submitCount) return false
        if (encodeCount != other.encodeCount) return false
        if (errorMessage != other.errorMessage) return false
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
        result = 31 * result + submitCount
        result = 31 * result + encodeCount
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + createTime.hashCode()
        result = 31 * result + (raw?.contentHashCode() ?: 0)
        return result
    }
}

fun PendingTransactionEntity.isFailure(): Boolean {
    return errorMessage != null
}
