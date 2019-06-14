package cash.z.android.wallet.ui.activity

import android.animation.Animator
import android.media.MediaPlayer
import android.os.Bundle
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
import cash.z.android.wallet.sample.WalletConfig
import cash.z.android.wallet.ui.fragment.ScanFragment
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.MainPresenter
import cash.z.android.wallet.ui.presenter.MainPresenterModule
import cash.z.android.wallet.ui.util.Analytics
import cash.z.android.wallet.ui.util.Analytics.PokerChipFunnel.StartSweep
import cash.z.android.wallet.ui.util.Analytics.PokerChipFunnel.Swept
import cash.z.android.wallet.ui.util.Analytics.Tap.*
import cash.z.android.wallet.ui.util.Analytics.trackAction
import cash.z.android.wallet.ui.util.Analytics.trackFunnelStep
import cash.z.android.wallet.ui.util.Broom
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

class MainActivity : BaseActivity(), Animator.AnimatorListener, ScanFragment.BarcodeCallback, MainPresenter.MainView {

    @Inject
    lateinit var synchronizer: Synchronizer

    @Inject
    lateinit var mainPresenter: MainPresenter

    @Inject
    lateinit var walletConfig: WalletConfig

    @Inject
    lateinit var broom: Broom

    @Inject
    lateinit var chipBucket: ChipBucket

    lateinit var binding: ActivityMainBinding
    lateinit var loadMessages: List<String>

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var navController: NavController
    
    private val mediaPlayer: MediaPlayer = MediaPlayer()

    lateinit var balancePresenter: BalancePresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chipBucket.restore()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.activity = this
        initAppBar()
        loadMessages = generateFunLoadMessages().shuffled()
        synchronizer.start(this)

        balancePresenter = BalancePresenter()
    }

    override fun onAttachFragment(childFragment: Fragment) {
        super.onAttachFragment(childFragment)
        (childFragment as? ScanFragment)?.barcodeCallback = this
    }

    override fun onResume() {
        super.onResume()
        chipBucket.restore()
        launch {
            balancePresenter.start(this, synchronizer)
            mainPresenter.start()
        }
    }

    override fun onPause() {
        super.onPause()
        balancePresenter.stop()
        mainPresenter.stop()
        chipBucket.save()
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronizer.stop()
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
            3 -> R.id.nav_zcon1_cart_fragment
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
        trackAction(TAPPED_SCAN_QR_HOME)
        supportFragmentManager.beginTransaction()
            .add(R.id.camera_placeholder, ScanFragment(), "camera_fragment")
            .addToBackStack("camera_fragment_scanning")
            .commit()
    }

    fun onSendFeedback(view: View) {
        trackAction(TAPPED_GIVE_FEEDBACK)
        navController.navigate(R.id.nav_feedback_fragment)
    }

    override fun onBarcodeScanned(value: String) {
        exitScanMode()

        // For now, empty happens when back is pressed
        if (value.isEmpty()) return
        chipBucket.findChip(value)?.let {existingChip ->
            if (existingChip.isRedeemed()) {
                alert("Previously Redeemed!", "We scanned this one already and the funds went to this wallet.")
            } else {
                alert(
                    title = "Still Processing",
                    message = "We scanned this one already and it is still processing. Would you rather wait until it finishes or abort and try again later?",
                    positiveButtonResId = R.string.wait,
                    negativeButtonResId = R.string.abort,
                    negativeAction = {
                        onAbortChip(existingChip)
                    }
                )
            }
            return
        }

        alert(
            title = "You found a token!",
            message = "Would you like to magically convert this poker chip into digital money?"
        ) {
            playSound(Random.nextBoolean())
            funQuote()
            launch {
                val chip = PokerChip(value)
                chipBucket.add(chip)
                val result = sweepChip(chip)
                twig("Sweep result? $result")
                trackFunnelStep(Swept(chip, result))
            }
        }

    }

    private fun onAbortChip(chip: PokerChip) {
        // TODO: don't remove until we're sure we can because this triggers a funnel event
        chipBucket.remove(chip)
    }

    private suspend fun sweepChip(chip: PokerChip): String? {
        trackFunnelStep(StartSweep(chip))
        val provider = PokerChipSeedProvider(chip)
        return broom.sweep(provider, chip.zatoshiValue)
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
        alert(
            message = "Are you sure you'd like to buy a ${product.name} for ${product.zatoshiValue.convertZatoshiToZecString(1)} TAZ?",
            positiveButtonResId = R.string.ok_allcaps,
            negativeButtonResId = R.string.cancel,
            positiveAction = { sendPurchaseOrder(product) }
        )
    }

    private fun sendPurchaseOrder(item: Zcon1Store.CartItem) {
        launch {
            synchronizer.sendToAddress(item.zatoshiValue, item.toAddress, item.memo)
        }
    }
    override fun orderFailed(error: MainPresenter.PurchaseResult.Failure) {
        alert(
            title = "Purchase Failed",
            message = "${error.reason}"
        )
    }

    override fun orderUpdated(processing: MainPresenter.PurchaseResult.Processing) {
        Toaster.short(processing.state.toString())
    }

    fun onFeedbackSubmit(view: View) {
        trackAction(TAPPED_SUBMIT_FEEDBACK)
        Toaster.short("Feedback Submitted! (j/k)")
        navController.navigateUp()
    }
    fun onFeedbackCancel(view: View) {
        Toaster.short("Feedback cancelled")
        trackAction(TAPPED_CANCEL_FEEDBACK)
        navController.navigateUp()
    }

    fun copyAddress(view: View) {
        trackAction(TAPPED_COPY_ADDRESS)
        Toaster.short("Address copied!")
        copyToClipboard(synchronizer.getAddress())
    }

}

@Module
abstract class MainActivityModule {
    @ActivityScope
    @ContributesAndroidInjector(modules = [MainPresenterModule::class])
    abstract fun contributeMainActivity(): MainActivity
}
