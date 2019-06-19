package cash.z.android.wallet

import cash.z.wallet.sdk.ext.ZATOSHI
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface ChipBucket {
    fun count(): Int
    fun add(chip: PokerChip)
    fun remove(chip: PokerChip)
    fun redeem(chip: PokerChip)
    fun forEach(block: (PokerChip) -> Unit)
    suspend fun save()
    suspend fun restore(): ChipBucket
    fun findChip(id: String): PokerChip?

    fun valuePending(): Long
    fun valueRedeemed(): Long
    fun setOnBucketChangedListener(listener: OnBucketChangedListener)
    fun removeOnBucketChangedListener(listener: OnBucketChangedListener)

    interface OnBucketChangedListener {
        fun onBucketChanged(bucket: ChipBucket)
    }
}

// suppress b/c we're ok w/ copies we just don't want the long constructor to be suggested in the IDE, for convenience
@Suppress("DataClassPrivateConstructor")
data class PokerChip(val id: String = "", var redeemed: Long = -1L, val created: Long = -1L) {
    constructor(id: String) : this(id, created = System.currentTimeMillis())

    init {
        require(id.startsWith("r-") || id.startsWith("b-"))
    }

    val maskedId: String get() = id.split("-").let { "${it[0]}-${it[1]}-${it[2]}-**masked**" }
    val chipColor: ChipColor get() = ChipColor.from(this)!! // acceptable !! because of the require contract
    val zatoshiValue: Long get() = chipColor.zatoshiValue

    fun isRedeemed(): Boolean {
        return redeemed > 0
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is PokerChip && other.id == id
    }

    enum class ChipColor(val zatoshiValue: Long, val idPrefix: String, val colorName: String) {
        RED(5L * ZATOSHI, "r-", "Red"),
        BLACK(25L * ZATOSHI, "b-", "Black");

        companion object {
            fun from(chip: PokerChip): ChipColor? {
                for (color in values()) {
                    if(chip.id.startsWith(color.idPrefix)) return color
                }
                return null
            }
        }
    }
}


fun PokerChip.toMemo(): String {
    return "${chipColor.colorName} Poker Chip Scanned #${id.hashCode()}"
}

class PokerChipSeedProvider(val chipId: String) : ReadOnlyProperty<Any?, ByteArray> {
    constructor(chip: PokerChip) : this(chip.id)

    override fun getValue(thisRef: Any?, property: KProperty<*>): ByteArray {
        val salt = ZcashWalletApplication.instance.getString(R.string.initial)
        val seed = "$chipId$salt"
        return "$chipId$salt".toByteArray()
    }
}