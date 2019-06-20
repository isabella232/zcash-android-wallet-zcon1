package cash.z.android.wallet

import cash.z.android.wallet.ui.util.Analytics
import cash.z.wallet.sdk.data.twig
import java.util.concurrent.CopyOnWriteArraySet

open class InMemoryChipBucket : ChipBucket {

    protected var listener: ChipBucket.OnBucketChangedListener? = null
    protected val chips = CopyOnWriteArraySet<PokerChip>()

    override fun count(): Int = chips.size

    override fun add(chip: PokerChip) {
        chips.add(chip)
        listener?.onBucketChanged(this)
        Analytics.trackFunnelStep(
            Analytics.PokerChipFunnel.Collected(
                chip
            )
        )
    }

    override fun remove(chip: PokerChip) {
        chips.remove(chip)
        listener?.onBucketChanged(this)
        Analytics.trackFunnelStep(
            Analytics.PokerChipFunnel.Aborted(
                chip
            )
        )
    }

    override fun redeem(chip: PokerChip) {
//        if (!chip.isRedeemed()) {
//            chips.remove(chip)
//            chips.add(chip.copy(redeemed = System.currentTimeMillis()))
//            listener?.onBucketChanged(this)
//            Analytics.trackFunnelStep(
//                Analytics.PokerChipFunnel.Redeemed(
//                    chip,
//                    true
//                )
//            )
//        }
    }

    override fun forEach(block: (PokerChip) -> Unit) {
        if (chips.isNotEmpty()) {
            for (chip in chips) {
                block(chip)
            }
        }
    }

    override suspend fun save() {}

    override suspend fun restore(): InMemoryChipBucket = this

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