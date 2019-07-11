package cash.z.android.wallet.ui.activity

import android.animation.Animator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import cash.z.android.wallet.*
import cash.z.android.wallet.databinding.ActivityMainBinding
import cash.z.android.wallet.di.annotation.ActivityScope
import cash.z.android.wallet.extention.Toaster
import cash.z.android.wallet.extention.alert
import cash.z.android.wallet.extention.copyToClipboard
import cash.z.android.wallet.ui.dialog.StatusDialog
import cash.z.android.wallet.ui.fragment.ScanFragment
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.MainPresenter
import cash.z.android.wallet.ui.presenter.MainPresenterModule
import cash.z.android.wallet.ui.util.Analytics
import cash.z.android.wallet.ui.util.Analytics.FeedbackFunnel
import cash.z.android.wallet.ui.util.Analytics.PokerChipFunnel.FundsFound
import cash.z.android.wallet.ui.util.Analytics.PokerChipFunnel.StartSweep
import cash.z.android.wallet.ui.util.Analytics.PurchaseFunnel.*
import cash.z.android.wallet.ui.util.Analytics.Tap.*
import cash.z.android.wallet.ui.util.Analytics.trackAction
import cash.z.android.wallet.ui.util.Analytics.trackCrash
import cash.z.android.wallet.ui.util.Analytics.trackFunnelStep
import cash.z.android.wallet.ui.util.Broom
import cash.z.wallet.sdk.data.DataSyncronizer
import cash.z.wallet.sdk.data.StableSynchronizer
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.db.isMined
import cash.z.wallet.sdk.db.isSubmitted
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

class MainActivity : BaseActivity(), Animator.AnimatorListener, ScanFragment.BarcodeCallback, MainPresenter.MainView {


    @Inject
    lateinit var synchronizer: DataSyncronizer

    @Inject
    lateinit var mainPresenter: MainPresenter

    @Inject
    lateinit var broom: Broom

//    @Inject
//    lateinit var chipBucket: ChipBucket

    lateinit var binding: ActivityMainBinding
    lateinit var loadMessages: List<String>

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var navController: NavController
    
    private val mediaPlayer: MediaPlayer = MediaPlayer()

    lateinit var balancePresenter: BalancePresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.lifecycle

//        chipBucket.restore()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.activity = this
        initAppBar()
        loadMessages = generateFunLoadMessages().shuffled()
        balancePresenter = BalancePresenter()
    }

    override fun onAttachFragment(childFragment: Fragment) {
        super.onAttachFragment(childFragment)
        (childFragment as? ScanFragment)?.barcodeCallback = this
    }

    override fun onResume() {
        super.onResume()
//        chipBucket.restore()
        launch {
            synchronizer.onCriticalErrorListener = ::onCriticalError
            synchronizer.start(this)
            balancePresenter.start(this, synchronizer.balances())
            mainPresenter.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Toaster.long("onActivityResult")
    }

    var deepLinkHandled = false
    fun handleDeepLink() {
        if (!deepLinkHandled && intent.data != null) {
            //lazy parsing!
            val name = intent.data.pathSegments[1]
            val amount = intent.data.pathSegments[2]
            val uuid = intent.data.pathSegments[3]
            onBarcodeScanned("c-$amount-$name-$uuid")
            deepLinkHandled = true
        }
    }

    private fun onCriticalError(error: Throwable): Boolean {
        Handler(Looper.getMainLooper()).post {
            //TODO proper error parsing, with strongly typed exceptions
            var title: String? = null
            var message: String? = null

            when {
                (error.message?.contains("UNAVAILABLE") == true) -> {
                    title = "Server Error!"
                    message = "Unable to reach the server. Either it is down or you are not connected to the internet."
                }
                else -> message = "WARNING: A critical error has occurred and " +
                        "this app will not function properly until that is corrected!"
            }
            alert(
                title = title,
                message = message,
                positiveButtonResId = R.string.ignore,
                negativeButtonResId = R.string.details,
                negativeAction = { alert("Synchronization error:\n\n$error") }
            )
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        balancePresenter.stop()
        mainPresenter.stop()
//        chipBucket.save()
    }

    override fun onDestroy() {
        super.onDestroy()
        Analytics.clear()
    }

    private fun initAppBar() {
        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        toolbar.navigationIcon = null
        setupNavigation()

        // show content behind the status bar
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    /**
     * Let the navController override the default behavior when the drawer icon or back arrow are clicked. This
     * automatically takes care of the drawer toggle behavior. Note that without overriding this method, the up/drawer
     * buttons will not function.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    fun setToolbarShown(isShown: Boolean) {
        binding.mainAppBar.visibility = if (isShown) View.VISIBLE else View.INVISIBLE
    }

    fun setNavigationShown(isShown: Boolean) {
        binding.groupNavigation.visibility = if (isShown) View.VISIBLE else View.INVISIBLE
        binding.groupNavigation.requestLayout()
    }

    fun setupNavigation() {
        // create and setup the navController and appbarConfiguration
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)

        navController.addOnDestinationChangedListener { _, _, _ ->
            // hide the keyboard anytime we change destinations
            getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, HIDE_NOT_ALWAYS)
        }

        binding.lottieNavigationZcon1.apply {
            speed = 2.0f
//            alpha = 0.85f
        }
    }

// TODO: move all this nav stuff to a separate component
    var navSelection = -1
    var start = -1
    var end = -1
    val frameSize = 24
    fun onNavButtonSelected2(index: Int) {
        if (navSelection == index) return
        val previousEnd =  (navSelection * frameSize + 6) + frameSize / 2
        navSelection = index.rem(4)
        start = navSelection * frameSize + 6
        end = start + frameSize / 2 - 6

        if(previousEnd > start) onAnimationEnd(null)
        else {
            binding.lottieNavigationZcon1.apply {
                addAnimatorListener(this@MainActivity)
                setMinAndMaxFrame(previousEnd, start)
                playAnimation()
            }
        }
        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.nav_default_enter_anim)
            .setExitAnim(R.anim.nav_default_exit_anim)
            .build()

        navController.navigate(when(navSelection) {
            0 -> R.id.nav_zcon1_home_fragment
            1 -> R.id.nav_send_fragment
            2 -> R.id.nav_receive_fragment
            3 -> R.id.nav_zcon1_cart_fragment
            else -> R.id.nav_zcon1_home_fragment
        }, null, navOptions)
    }

    val enterRanges = arrayOf<IntRange>(
        0..13,
        24..37,
        48..61,
        72..85
    )
    val exitRanges = arrayOf<IntRange>(
        14..23,
        38..47,
        62..71,
        86..95
    )
    fun onNavButtonSelected(index: Int) {
        if (navSelection == index || index < 0 || index > 3) return
        val previousSelection = navSelection
        navSelection = index

        if (previousSelection == -1) {
            onAnimationEnd(null)
        } else {
            // play nav section exit animation
            binding.lottieNavigationZcon1.apply {
                addAnimatorListener(this@MainActivity)
                setMinAndMaxFrame(exitRanges[previousSelection].first, exitRanges[previousSelection].last)
                playAnimation()
            }
        }

        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.nav_default_enter_anim)
            .setExitAnim(R.anim.nav_default_exit_anim)
            .build()
        navController.navigate(when(navSelection) {
            0 -> R.id.nav_zcon1_home_fragment
            1 -> R.id.nav_send_fragment
            2 -> R.id.nav_receive_fragment
            3 -> R.id.nav_zcon1_cart_fragment.also { launch { trackFunnelStep(ViewedCart) } }
            else -> R.id.nav_zcon1_home_fragment
        }, null, navOptions)
    }


    override fun onAnimationRepeat(animation: Animator?) {
    }

    override fun onAnimationEnd(animation: Animator?) {
            // play nav section enter animation
        binding.lottieNavigationZcon1.apply {
            removeAnimatorListener(this@MainActivity)
            setMinAndMaxFrame(enterRanges[navSelection].first, enterRanges[navSelection].last)
            playAnimation()
        }
    }

    fun onAnimationEnd2(animation: Animator?) {
        binding.lottieNavigationZcon1.apply {
            removeAnimatorListener(this@MainActivity)
            setMinAndMaxFrame(start, end)
            playAnimation()
        }
    }

    override fun onAnimationCancel(animation: Animator?) {
    }

    override fun onAnimationStart(animation: Animator?) {
    }




    fun nextLoadMessage(index: Int = -1): String {
        return if (index < 0) loadMessages.random() else loadMessages[index]
    }

    companion object {
        init {
            // Enable vector drawable magic
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        // TODO: move these lists, once approved
        fun generateSeriousLoadMessages(): List<String> {
            return listOf(
                "Initializing your shielded address",
                "Connecting to testnet",
                "Downloading historical blocks",
                "Synchronizing to current blockchain",
                "Searching for past transactions",
                "Validating your balance"
            )
        }

        fun generateFunLoadMessages(): List<String> {
            return listOf(
                "Reticulating splines",
                "Making the sausage",
                "Drinking the kool-aid",
                "Learning to spell Lamborghini",
                "Asking Zooko, \"when moon?!\"",
                "Pretending to look busy"
            )
        }
    }

    fun onScanQr(view: View) {
        try {
            trackAction(TAPPED_SCAN_QR_HOME)
            val isFirstRun = view.id == R.id.button_first_run_scan
            if (isFirstRun) {
                navController.navigate(R.id.nav_zcon1_home_fragment)
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.camera_placeholder, ScanFragment(), "camera_fragment")
                .addToBackStack("camera_fragment_scanning")
                .commit()
        } catch (t: Throwable) {
            Toaster.long("Error opening scanner on this device! This error has been reported. Thanks for testing the beta app!")
            trackCrash(t, "Crash while scanning qr code")
            launch {
                delay(1000L)
                Analytics.clear()
                exitScanMode()
            }
        }
    }

    fun onSendFeedback(view: View) {
        trackAction(TAPPED_GIVE_FEEDBACK)
        trackFunnelStep(FeedbackFunnel.Started)
        navController.navigate(R.id.nav_feedback_fragment)
    }

    fun onShowStatus() {
        launch {
            (synchronizer as StableSynchronizer).refreshBalance()
            val balanceInfo = balancePresenter.lastBalance
            StatusDialog(
                availableBalance = balanceInfo.available,
                syncingBalance = balanceInfo.total - balanceInfo.available,
                pendingChipBalance = calculatePendingChipBalance(),
                summary = determineStatusSummary(balanceInfo)
            ).show(supportFragmentManager, "dialog_status")
        }
    }

    private fun determineStatusSummary(balanceInfo: Wallet.WalletBalance): String {
        val available = balanceInfo.available
        val total = balanceInfo.total
        val hasIncomingFunds = available < total
        val hasPendingChips = (calculatePendingChipBalance() ?: 0L) > 0L
        val isUnfunded = total == 0L && !hasPendingChips
        val hasEnoughForSwag = available > Zcon1Store.CartItem.SwagTee("").zatoshiValue


        val statusResId = when {
            isUnfunded -> R.string.status_wallet_unfunded
            hasEnoughForSwag -> R.string.status_wallet_funds_available_for_swag
            hasIncomingFunds -> R.string.status_wallet_incoming_funds
            hasPendingChips -> R.string.status_wallet_chips_pending
            else -> R.string.status_wallet_generic
        }
        return getString(statusResId)// + "\n\nErrors: there was an error. J/k but if there was it would show up here."
    }

    override fun onBarcodeScanned(value: String) {
        exitScanMode()

        // For now, empty happens when back is pressed
        if (value.isEmpty()) return
        synchronizer.getPending()?.firstOrNull { it.memo.contains("#${value.hashCode()}") }?.let { existingTransaction ->
            if (existingTransaction.isMined()) {
                alert(title = "Successfully Redeemed!", message = "We scanned this one already and the funds went to this wallet!")
            } else {
                alert(
                    title = "Still Processing",
                    message = "We scanned this one already and it is still processing. Would you rather wait until it finishes or abort and try again later?",
                    positiveButtonResId = R.string.wait,
                    negativeButtonResId = R.string.abort,
                    negativeAction = {
                        launch {
                            if (existingTransaction.isSubmitted()) {
                                alert(
                                    title = "Oops! Too late.",
                                    message = "Cannot abort because the transaction has already been uploaded to the network."
                                )
                            } else {
                                broom.sender.cancel(existingTransaction)
                            }
                        }
                    }
                )
            }
            return
        }

        if (value.startsWith("c-")) {
            val chip = PokerChip(value)
            alert(
                title = "You received magic money from ${chip.chipColor.colorName}!",
                message = "How nice! ${chip.chipColor.colorName} send you ${chip.zatoshiValue.convertZatoshiToZecString(2)} TAZ. Would you like to add it to your wallet?"
            ) {
                playSound(Random.nextBoolean())
                funQuote()
                launch {
                    val result = sweepChip(chip)
                    twig("Sweep result? $result")
                    trackFunnelStep(FundsFound(chip, result))
                }
            }
        } else {
            alert(
                title = "You found a token!",
                message = "Would you like to magically convert this poker chip into digital money?"
            ) {
                playSound(Random.nextBoolean())
                funQuote()
                launch {
                    val chip = PokerChip(value)
                    val result = sweepChip(chip)
                    twig("Sweep result? $result")
                    trackFunnelStep(FundsFound(chip, result))
                }
            }

        }

    }

    private suspend fun sweepChip(chip: PokerChip): String? {
        trackFunnelStep(StartSweep(chip))
        val provider = PokerChipSeedProvider(chip)
        val memo = chip.toMemo()
        val result = broom.sweep(provider, chip.zatoshiValue, memo)
        if (result != null) {
            // TODO: fix this lazy parsing later, with strongly typed exceptions, instead of strings
            val message = if(result.toLowerCase().contains("insufficient")) "This poker chip was already redeemed, maybe by another wallet." else "Please try again later (tap 'details' for more info)."
            val title = if(result.toLowerCase().contains("insufficient")) "Oops. Already Redeemed!" else "Oops. Something went wrong!"
            alert(
                title = title,
                message = "We were unable to sweep that poker chip! $message",
                positiveButtonResId = R.string.ok_allcaps,
                negativeButtonResId = R.string.details,
                negativeAction = { alert("Error details:\n$result") }
            )
        }
        return result
    }

    val scanQuote = arrayOf(
        "You're the Scrooge McDuck of Zcon1!",
        "We're rich!",
        "Show me the money! Oh wait, you just did. Literally.",
        "Doing magic. Actual magic.",
        "This is TAZmania!"
    )
    private fun funQuote() {
        var message = scanQuote.random()
//        if(message == scanQuote[0]) message = scanQuote.random() // simple way to make 0 more rare
        Toaster.short(message)
    }

    override fun isTargetBarcode(value: String?): Boolean {
        if(value == null) return false
        return value.startsWith("r-") || value.startsWith("b-")
    }

    private fun playSound(isLarge: Boolean) {
        mediaPlayer.apply {
            if (isPlaying) stop()
            try {
                reset()
                val fileName = if (isLarge) "sound_receive_large.mp3" else "sound_receive_small.mp3"
                assets.openFd(fileName).let { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                prepare()
                start()
            } catch (t: Throwable) {
                twig("ERROR: unable to play sound due to $t")
            }
        }
    }

    private fun exitScanMode() {
        with(supportFragmentManager) {
            findFragmentByTag("camera_fragment")?.let { cameraFragment ->
                beginTransaction().remove(cameraFragment).commit()
            }
        }
    }

    fun buyProduct(product: Zcon1Store.CartItem) {
        trackFunnelStep(PurchasedItem(product))
        val balance = (synchronizer as StableSynchronizer).getBalance()
        if (balance.available < (product.zatoshiValue + MINERS_FEE_ZATOSHI)) {
            val message = if (balance.total >= (product.zatoshiValue + MINERS_FEE_ZATOSHI)) {
                "Sorry, some of your funds are still awaiting 20 network confirmations before they are available for spending! Try again after your \"amount syncing\" is zero."
            } else {
                "Sorry, you do not have enough funds available for this purchase.\n\nMaybe find poker chips or convince a friend to send you funds by scanning your QR code on the previous tab."
            }
            alert(
                title = "Oops. Insufficient funds!",
                message = message,
                positiveButtonResId = R.string.ok_allcaps
            )
        } else {
            alert(
                message = "Are you sure you'd like to buy a ${product.name} for ${product.zatoshiValue.convertZatoshiToZecString(1)} TAZ?",
                positiveButtonResId = R.string.ok_allcaps,
                negativeButtonResId = R.string.cancel,
                positiveAction = { sendPurchaseOrder(product) },
                negativeAction = { onPurchaseCancelled(product) }
            )
        }
    }

    private fun onPurchaseCancelled(product: Zcon1Store.CartItem) {
        trackFunnelStep(CancelledPurchase(product))
    }

    private fun sendPurchaseOrder(item: Zcon1Store.CartItem) {
        trackFunnelStep(ConfirmedPurchase(item))
        launch {
            synchronizer.sendToAddress(item.zatoshiValue, item.toAddress, item.memo)
        }
        navController.navigate(R.id.nav_zcon1_home_fragment)
    }
    override fun orderFailed(error: MainPresenter.PurchaseResult.Failure) {
        trackFunnelStep(Submitted(error.reason))
        alert(
            title = "Purchase Failed",
            message = "${error.reason}"
        )
    }

    override fun orderUpdated(processing: MainPresenter.PurchaseResult.Processing) {
        twig("order updated: ${processing.pendingTransaction}")
        Toaster.short("Order updated: ${processing.pendingTransaction.memo}")
    }

    fun onSynchronizerError(error: Throwable?): Boolean {
        alert(
            message = "WARNING: A critical error has occurred and " +
                    "this app will not function properly until that is corrected!",
            positiveButtonResId = R.string.ignore,
            negativeButtonResId = R.string.details,
            negativeAction = { alert("Synchronization error:\n\n$error") }
        )
        return false
    }


    //
    // Events from Layout Files
    //

    fun copyAddress(view: View) {
        trackAction(TAPPED_COPY_ADDRESS)
        Toaster.short("Address copied!")
        launch {
            copyToClipboard(synchronizer.getAddress())
        }
    }

    fun calculatePendingChipBalance(): Long {
        return synchronizer.getPending()?.filter {
            it.memo.toLowerCase().contains("poker chip") && !it.isMined()
            }?.fold(0L) { acc, item ->
                acc + item.value
            } ?: 0L
    }

}

@Module
abstract class MainActivityModule {
    @ActivityScope
    @ContributesAndroidInjector(modules = [MainPresenterModule::class])
    abstract fun contributeMainActivity(): MainActivity
}
