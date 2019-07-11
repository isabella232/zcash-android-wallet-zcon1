package cash.z.android.wallet

import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.ZATOSHI_PER_ZEC
import cash.z.wallet.sdk.ext.convertZecToZatoshi
import cash.z.wallet.sdk.ext.safelyConvertToBigDecimal
import java.lang.IllegalStateException
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
        require(id.startsWith("r-") || id.startsWith("b-") || id.startsWith("c-"))
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


    sealed class ChipColor(val zatoshiValue: Long, val idPrefix: String, val colorName: String) {
        object RED : ChipColor(5L * ZATOSHI_PER_ZEC, "r-", "Red")
        object BLACK : ChipColor(25L * ZATOSHI_PER_ZEC, "b-", "Black")
        // c-{amount}-{name}-{uuid}
        class CUSTOM private constructor(customValue: Long, customName: String) : ChipColor(customValue, "c-", customName) {
            constructor(id: String) : this(toZatoshi(id), toName(id))
        }

        companion object {
            fun toZatoshi(id: String): Long {
                return id.split("-")[1].safelyConvertToBigDecimal().convertZecToZatoshi()
            }
            fun toName(id: String): String {
                return id.split("-")[2]
            }
            fun from(chip: PokerChip): ChipColor? {
                return when {
                    chip.id.startsWith(RED.idPrefix) -> RED
                    chip.id.startsWith(BLACK.idPrefix) -> BLACK
                    chip.id.startsWith("c-") -> CUSTOM(chip.id)
                    else -> throw IllegalStateException("unrecognized chip id: ${chip.id}")
                }
                return null
            }
        }
    }
}


fun PokerChip.toMemo(): String {
    return if (chipColor is PokerChip.ChipColor.CUSTOM) "${chipColor.colorName} sent us magic money! Isn't that nice and it's in the form of a Poker Chip." //must have poker chip in the name so make it off screen for now :D
    else "${chipColor.colorName} Poker Chip Scanned #${id.hashCode()}"
}

class PokerChipSeedProvider(val chipId: String) : ReadOnlyProperty<Any?, ByteArray> {
    constructor(chip: PokerChip) : this(chip.id)

    override fun getValue(thisRef: Any?, property: KProperty<*>): ByteArray {
        val salt = ZcashWalletApplication.instance.getString(R.string.initial)
        val seed = "$chipId$salt"
        return "$chipId$salt".toByteArray()
    }
}