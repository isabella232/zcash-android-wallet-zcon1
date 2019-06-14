package cash.z.android.wallet

import cash.z.android.wallet.ui.util.Analytics
import cash.z.android.wallet.ui.util.Analytics.PokerChipFunnel.*
import cash.z.android.wallet.ui.util.Analytics.trackFunnelStep
import cash.z.wallet.sdk.data.twig
import java.util.concurrent.CopyOnWriteArraySet
import cash.z.wallet.sdk.ext.ZATOSHI
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface ChipBucket {
    fun add(chip: PokerChip)
    fun remove(chip: PokerChip)
    fun redeem(chip: PokerChip)
    fun forEach(block: (PokerChip) -> Unit)
    fun save()
    fun restore(): ChipBucket
    fun findChip(id: String): PokerChip?

    fun valuePending(): Long
    fun valueRedeemed(): Long
    fun setOnBucketChangedListener(listener: OnBucketChangedListener)
    fun removeOnBucketChangedListener(listener: OnBucketChangedListener)

    interface OnBucketChangedListener {
        fun onBucketChanged(bucket: ChipBucket)
    }
}

class InMemoryChipBucket : ChipBucket {

    private var listener: ChipBucket.OnBucketChangedListener? = null
    private val chips = CopyOnWriteArraySet<PokerChip>()

    override fun add(chip: PokerChip) {
        chips.add(chip)
        listener?.onBucketChanged(this)
        trackFunnelStep(Collected(chip))
    }

    override fun remove(chip: PokerChip) {
        chips.remove(chip)
        listener?.onBucketChanged(this)
        trackFunnelStep(Aborted(chip))
    }

    override fun redeem(chip: PokerChip) {
        if (!chip.isRedeemed()) {
            chips.remove(chip)
            chips.add(chip.copy(redeemed = System.currentTimeMillis()))
            listener?.onBucketChanged(this)
            trackFunnelStep(Redeemed(chip, true))
        }
    }

    override fun forEach(block: (PokerChip) -> Unit) {
        if (chips.isNotEmpty()) {
            for (chip in chips) {
                block(chip)
            }
        }
    }

    override fun save() {}

    override fun restore(): InMemoryChipBucket = this

    override fun findChip(id: String) = chips.find { it.id == id }

    override fun valuePending(): Long {
        if(chips.isEmpty()) return -1L
        return chips.fold(0L) { acc, pokerChip ->
            if(pokerChip.isRedeemed()) acc else acc + pokerChip.zatoshiValue
        }
    }

    override fun valueRedeemed(): Long {
        if(chips.isEmpty()) return -1L
        return chips.fold(0L) { acc, pokerChip ->
            if (pokerChip.isRedeemed()) acc + pokerChip.zatoshiValue else acc
        }
    }

    override fun setOnBucketChangedListener(listener: ChipBucket.OnBucketChangedListener) {
        if (listener !== this.listener) {
            this.listener = listener
            listener?.onBucketChanged(this)
            twig("bucket listener set to $listener")
        }
    }

    override fun removeOnBucketChangedListener(listener: ChipBucket.OnBucketChangedListener) {
        if (listener === this.listener) {
            twig("removing bucket listener $listener")
            this.listener = null
        }
    }
}

// suppress b/c we're ok w/ copies we just don't want the long constructor to be suggested in the IDE, for convenience
@Suppress("DataClassPrivateConstructor")
data class PokerChip private constructor(val id: String = "", val redeemed: Long = -1L, val created: Long = -1L) {
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

    enum class ChipColor(val zatoshiValue: Long, val idPrefix: String) {
        RED(5L * ZATOSHI, "r-"),
        BLACK(25L * ZATOSHI, "b-");

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

class PokerChipSeedProvider(val chipId: String) : ReadOnlyProperty<Any?, ByteArray> {
    constructor(chip: PokerChip) : this(chip.id)

    override fun getValue(thisRef: Any?, property: KProperty<*>): ByteArray {
        val salt = ZcashWalletApplication.instance.getString(R.string.initial)
        return "$chipId$salt".toByteArray()
    }
}