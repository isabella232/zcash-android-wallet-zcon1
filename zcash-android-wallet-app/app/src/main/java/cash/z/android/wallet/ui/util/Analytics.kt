package cash.z.android.wallet.ui.util

import android.annotation.SuppressLint
import cash.z.android.wallet.PokerChip
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.wallet.sdk.data.twig
import com.mixpanel.android.mpmetrics.MixpanelAPI

object Analytics {
    // TODO: Move to manifest
    private const val MIXPANEL_TOKEN = "17e08c2ca8e6d1fe4f88335a2d1635cf"

    @SuppressLint("StaticFieldLeak") // application context is fine to use here
    private val mixpanel: MixpanelAPI = MixpanelAPI.getInstance(ZcashWalletApplication.instance, MIXPANEL_TOKEN)

    fun clear() {
        mixpanel.flush()
    }

    fun trackFunnelStep(funnel: FunnelStep) {
        twig("@FunnelStep ${funnel.eventName()} : ${funnel.toProperties()}")
        mixpanel.trackMap(funnel.eventName(), funnel.toProperties())
    }

    fun trackAction(action: Action) {
        twig("@Action ${action.eventName()}")
        mixpanel.trackMap(action.eventName(), mapOf("deviceTimestamp" to action.deviceTimestamp()))
    }

    /**
     * An action is an event with no properties
     */
    interface Action {
        fun eventName(): String {
            return this.toString()
        }
        fun deviceTimestamp(): Long = System.currentTimeMillis()
    }

    /**
     * A funnel is an event with properties, typically part of a larger sequence of events.
     */
    interface FunnelStep : Action {
        fun toProperties(): MutableMap<String, Any>
    }

    /**
     * Every tap in the app
     */
    enum class Tap : Action {
        // Feedback Screen
        TAPPED_GIVE_FEEDBACK,
        TAPPED_CANCEL_FEEDBACK,
        TAPPED_SUBMIT_FEEDBACK,

        // Received Screen
        TAPPED_COPY_ADDRESS,

        // Scan
        TAPPED_SCAN_QR_HOME,
        TAPPED_SCAN_QR_SEND,
        TAPPED_SCAN_QR_ONBOARDING
    }

    sealed class PokerChipFunnel(val pokerChip: PokerChip, val success: Boolean = true) : FunnelStep {
        class Collected(chip: PokerChip) : PokerChipFunnel(chip)
        class Aborted(chip: PokerChip) : PokerChipFunnel(chip)
        class StartSweep(chip: PokerChip) : PokerChipFunnel(chip)
        class Swept(chip: PokerChip, val error: String?) : PokerChipFunnel(chip, error == null) {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply { this["errorMessage"] = "$error" }
            }
        }
        class Redeemed(chip: PokerChip, isSuccess: Boolean) : PokerChipFunnel(chip, isSuccess)

        override fun toProperties(): MutableMap<String, Any> {
            return pokerChip.toProperties().apply {
                this["funnelStep"] = eventName()
                this["isSuccess"] = "$success"
                this["deviceTimestamp"] = deviceTimestamp()
            }
        }

        override fun eventName(): String {
            return "${PokerChipFunnel::class.simpleName}.${javaClass.simpleName}"
        }
    }
}

private fun PokerChip.toProperties(): MutableMap<String, Any> {
    return mutableMapOf(
        "PokerChip.id" to maskedId,
        "PokerChip.created" to created,
        "PokerChip.redeemed" to redeemed
    )
}
