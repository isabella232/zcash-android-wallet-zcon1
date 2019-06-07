package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.fragment.Zcon1CartFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.*
import cash.z.wallet.sdk.secure.Wallet
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.contracts.ExperimentalContracts

class CartPresenter @Inject constructor(
    private val view: Zcon1CartFragment,
    private val synchronizer: Synchronizer
) : Presenter {

    interface Zcon1CartView : PresenterView {
        fun updateAvailableBalance(available: Long)
        fun orderFailed(isSuccess: PurchaseResult.Failure)
        fun orderUpdated(processing: PurchaseResult.Processing)
    }

    /**
     * We require the user to send more than this amount. Right now, we just use the miner's fee as a minimum but other
     * lower bounds may also be useful for validation.
     */
    private val minersFee = 10_000L
    private var balanceJob: Job? = null
    private var purchaseJob: Job? = null


    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("CartPresenter")
        twig("cartPresenter starting!")
        balanceJob?.cancel()
        balanceJob = Job()
        balanceJob = view.launchBalanceBinder(synchronizer.balances())

        purchaseJob?.cancel()
        purchaseJob = Job()
        purchaseJob = view.launchPurchaseBinder(synchronizer.activeTransactions())
    }

    override fun stop() {
        twig("cartPresenter stopping!")
        Twig.clip("CartPresenter")
        balanceJob?.cancel()?.also { balanceJob = null }
    }

    fun CoroutineScope.launchBalanceBinder(channel: ReceiveChannel<Wallet.WalletBalance>) = launch {
        twig("cart balance binder starting!")
        for (new in channel) {
            twig("cart polled a balance item")
            bind(new)
        }
        twig("cart balance binder exiting!")
    }

    fun CoroutineScope.launchPurchaseBinder(channel: ReceiveChannel<Map<ActiveTransaction, TransactionState>>) = launch {
        twig("cart purchase binder starting!")
        for (new in channel) {
            twig("cart polled a purchase info")
            bind(new)
        }
        twig("cart purchse binder exiting!")
    }

    //
    // Public API
    //

    fun buyItem(item: CartItem) {
        //TODO: prehaps grab the activity scope or let the sycnchronizer have scope and make that function not suspend
        // also, we need to handle cancellations. So yeah, definitely do this differently
        GlobalScope.launch {
            synchronizer.sendToAddress(item.zatoshiValue, item.toAddress, item.memo)
        }
    }


    //
    // Events
    //

    fun bind(balanceInfo: Wallet.WalletBalance) {
        val available = balanceInfo.available
        if (available >= 0) {
            twig("binding balance of $available")
            view.updateAvailableBalance(available)
        }
    }

    private fun bind(activeTransactions: Map<ActiveTransaction, TransactionState>) {
        val newestState = activeTransactions.entries.last().value
        if (newestState is TransactionState.Failure) {
            view.orderFailed(PurchaseResult.Failure(newestState.reason))
        } else {
            view.orderUpdated(PurchaseResult.Processing(newestState))
        }
    }


    //
    // Validation
    //

//    /**
//     * Called after any user interaction. This is a potential time that errors should be shown, but only if data has
//     * already been entered. The view should call this method on focus change.
//     */
//    fun invalidate() {
//        requiresValidation = true
//    }
//
//    /**
//     * Validates the given address
//     *
//     * @param toAddress the address to consider for validation
//     * @param ignoreLength whether to ignore the length while validating, this is helpful when the user is still
//     * actively typing the address
//     */
//    private fun validateAddress(toAddress: String, ignoreLength: Boolean = false): Boolean {
//        // TODO: later expose a method in the synchronizer for validating addresses.
//        //  Right now it's not available so we do it ourselves
//        return if (!ignoreLength && sendUiModel.hasBeenUpdated && toAddress.length < 20) {// arbitrary length for now
//            view.setAddressError(R.string.send_error_address_too_short.toAppString())
//            requiresValidation = true
//            false
//        } else if (!toAddress.startsWith("zt") && !toAddress.startsWith("zs")) {
//            view.setAddressError(R.string.send_error_address_invalid_contents.toAppString())
//            requiresValidation = true
//            false
//        } else if (toAddress.any { !it.isLetterOrDigit() }) {
//            view.setAddressError(R.string.send_error_address_invalid_char.toAppString())
//            requiresValidation = true
//            false
//        } else {
//            view.setAddressError(null)
//            true
//        }
//    }
//
//    /**
//     * Validates the given amount, calling the related `showError` methods on the view, when appropriate
//     *
//     * @param amount the amount to consider for validation, for now this can be either USD or ZEC. In the future we will
//     * will separate those into types.
//     *
//     * @return true when the given amount is valid and all errors have been cleared on the view
//     */
//    private fun validateAmount(amountString: String): Boolean {
//        if (!sendUiModel.hasBeenUpdated) return true // don't mark zero as bad until the model has been updated
//
//        var amount = amountString.safelyConvertToBigDecimal()
//        // no need to convert when we know it's null
//        return if (amount == null ) {
//            validateZatoshiAmount(null)
//        } else {
//            val zecAmount =
//                if (sendUiModel.isUsdSelected) amount.convertUsdToZec(SampleProperties.USD_PER_ZEC) else amount
//            validateZatoshiAmount(zecAmount.convertZecToZatoshi())
//        }
//    }
//
//    private fun validateZatoshiAmount(zatoshiValue: Long?): Boolean {
//        return if (zatoshiValue == null || zatoshiValue <= minersFee) {
//            view.setAmountError("Please specify a larger amount")
//            requiresValidation = true
//            false
//        } else if (sendUiModel.availableBalance != null
//            && zatoshiValue >= sendUiModel.availableBalance!!) {
//            view.setAmountError("Exceeds available balance of " +
//                    "${sendUiModel.availableBalance.convertZatoshiToZecString(3)}")
//            requiresValidation = true
//            false
//        } else {
//            view.setAmountError(null)
//            true
//        }
//    }
//
//    fun validateAll(): Boolean {
//        with(sendUiModel) {
//            val isValid = validateZatoshiAmount(zatoshiValue)
//                    && validateAddress(toAddress)
//                    && validateMemo(memo)
//            requiresValidation = !isValid
//            view.setSendEnabled(isValid)
//            return isValid
//        }
//    }

    // TODO: get these values elsewhere and simplify construction
    sealed class CartItem(val itemName: String = "", val zatoshiValue: Long = -1L, val toAddress: String = "", val memo: String = "") {
        data class SwagShirt(val to: String, val from: String) : CartItem("Swag Shirt", 2000010000, to, from)
        data class SwagPad(val to: String, val from: String) :  CartItem("Swag Pad", 3000010000, to, from)
    }

    sealed class PurchaseResult {
        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
        data class Failure(val reason: String = "") : PurchaseResult()
    }

}


@Module
abstract class CartPresenterModule {
    @Binds
    abstract fun providePresenter(cartPresenter: CartPresenter): Presenter
}