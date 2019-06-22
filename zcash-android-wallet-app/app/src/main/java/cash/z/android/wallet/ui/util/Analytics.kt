package cash.z.android.wallet.ui.util

import android.annotation.SuppressLint
import android.preference.PreferenceManager
import cash.z.android.wallet.PokerChip
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.Zcon1Store
import cash.z.android.wallet.data.db.PendingTransactionEntity
import cash.z.android.wallet.extention.toAppString
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import com.mixpanel.android.mpmetrics.MixpanelAPI

/**
 * Since this is, effectively, an internal QA build and only runs on testnet with test funds, we use Analytics to get
 * information about how to improve the user experience in the app and we also use it as backup to help with swag
 * distribution, in case there are any hiccups in the testnet network at the event. We don't collect any PII, not even
 * a real first name.
 */
object Analytics {

    @SuppressLint("StaticFieldLeak") // application context is fine to use here
    private val mixpanel: MixpanelAPI =
        MixpanelAPI.getInstance(ZcashWalletApplication.instance, R.string.mixpanel_project_token_dev.toAppString())

    private var pseudonym: String? = null

    fun clear() {
        mixpanel.flush()
    }

    fun trackFunnelStep(funnel: FunnelStep) {
        track(funnel, "@FunnelStep")
    }

    fun trackAction(action: Action) {
        track(action, "@Action")
    }

    fun trackError(error: Error) {
        track(error, "@Error")
    }

    private fun track(action: Action, prefix: String) {
        twig("$prefix ${action.eventName()} : ${action.toProperties()}")
        mixpanel.trackMap(action.eventName(), action.toProperties())
    }

    private fun getPseudonm(): String? {
        return pseudonym ?: PreferenceManager
            .getDefaultSharedPreferences(ZcashWalletApplication.instance)
            .getString(ZcashWalletApplication.PREFS_PSEUDONYM, null).also { pseudonym = it }
    }

    /**
     * An action is an event with no properties other than timestamp
     */
    interface Action {
        fun eventName(): String {
            return this.javaClass.canonicalName
        }

        fun deviceTimestamp(): Long = System.currentTimeMillis()
        fun toProperties(): MutableMap<String, Any> {
            return mutableMapOf(
                "deviceTimestamp" to deviceTimestamp(),
                "pseudonym" to (pseudonym ?: getPseudonm() ?: "Unknown")
            )
        }
    }

    /**
     * A funnel is an event with properties, typically part of a larger sequence of events.
     */
    interface FunnelStep : Action

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
        TAPPED_SCAN_QR_ONBOARDING;

        override fun eventName(): String {
            return toString()
        }
    }

    sealed class Error(val error: Throwable) : Action {
        class ErrorSubmitting(t: Throwable) : Error(t)
        class ProcessorMaxFailureReached(t: Throwable) : Error(t)
        class ProcessorRepeatedFailure(t: Throwable, private val count: Int) : Error(t) {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply { this["failureCount"] = count }
            }
        }

        override fun toProperties(): MutableMap<String, Any> {
            return super.toProperties().apply {
                this["errorMessage"] = "$error"
                if (error.cause != null) this["causedBy"] = "${error.cause}"
            }
        }
    }

    sealed class PurchaseFunnel : FunnelStep {
        object ViewedCart : PurchaseFunnel()
        open class SelectedItem(val item: Zcon1Store.CartItem) : PurchaseFunnel() {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply {
                    this["itemName"] = item.name
                    this["itemValue"] = item.zatoshiValue
                }
            }
        }

        open class PurchasedItem(cartItem: Zcon1Store.CartItem) : SelectedItem(cartItem)
        open class ConfirmedPurchase(cartItem: Zcon1Store.CartItem) : PurchasedItem(cartItem)
        open class CancelledPurchase(cartItem: Zcon1Store.CartItem) : PurchasedItem(cartItem)
        open class TransactionCreated(val errorMessage: String?) : PurchaseFunnel() {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply {
                    this["isSuccess"] = errorMessage == null
                    this["errorMessage"] = "$errorMessage"
                }
            }
        }

        class Submitted(val errorMessage: String? = null) : PurchaseFunnel() {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply {
                    this["isSuccess"] = errorMessage == null
                    this["errorMessage"] = "$errorMessage"
                    this["isPurchase"] = true
                }
            }
        }
    }

    sealed class FeedbackFunnel : FunnelStep {
        object Started : FeedbackFunnel()
        object Cancelled : FeedbackFunnel()
        data class Submitted(val rating: Int, val question1: String, val question2: String, val question3: String) :
            FeedbackFunnel() {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply {
                    this["rating"] = rating
                    this["question1"] = question1
                    this["question2"] = question2
                    this["question3"] = question3
                }
            }
        }
    }

    sealed class PokerChipFunnel(val pokerChip: PokerChip, val success: Boolean = true) : FunnelStep {
        class Collected(chip: PokerChip) : PokerChipFunnel(chip)
        class Aborted(chip: PokerChip) : PokerChipFunnel(chip)
        class StartSweep(chip: PokerChip) : PokerChipFunnel(chip)
        class FundsFound(chip: PokerChip, val error: String?) : PokerChipFunnel(chip, error == null) {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply { this["errorMessage"] = "$error" }
            }
        }

        class Redeemed(private val tx: PendingTransactionEntity, isSuccess: Boolean) : PokerChipFunnel(tx.toChip(), isSuccess) {
            override fun toProperties(): MutableMap<String, Any> {
                return super.toProperties().apply {
                    this["encodeAttempts"] = tx.encodeAttempts
                    this["submitAttempts"] = tx.submitAttempts
                    this["expiryHeight"] = tx.expiryHeight
                }
            }
        }

        override fun toProperties(): MutableMap<String, Any> {
            return pokerChip.toProperties().apply {
                this["funnelStep"] = eventName()
                this["isSuccess"] = "$success"
            }
        }

        override fun eventName(): String {
            return "${PokerChipFunnel::class.simpleName}.${javaClass.simpleName}"
        }
    }
}

private fun PendingTransactionEntity.toChip(): PokerChip {
    return PokerChip(
        "r-mined($minedHeight)value(${value.convertZatoshiToZecString(2)})-memo($memo)",
        System.currentTimeMillis(),
        createTime
    )
}

private fun PokerChip.toProperties(): MutableMap<String, Any> {
    return mutableMapOf(
        "PokerChip.id" to maskedId,
        "PokerChip.created" to created,
        "PokerChip.redeemed" to redeemed
    )
}
