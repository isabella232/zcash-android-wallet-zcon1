package cash.z.android.wallet.ui.activity

import android.animation.Animator
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.ActivityMainBinding
import cash.z.android.wallet.sample.WalletConfig
import cash.z.wallet.sdk.data.Synchronizer
import dagger.Module
import dagger.android.ContributesAndroidInjector
import javax.inject.Inject

class MainActivity : BaseActivity(), Animator.AnimatorListener {

    @Inject
    lateinit var synchronizer: Synchronizer

    @Inject
    lateinit var walletConfig: WalletConfig

    lateinit var binding: ActivityMainBinding
    lateinit var loadMessages: List<String>

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.activity = this
        initAppBar()
        loadMessages = generateFunLoadMessages().shuffled()
        synchronizer.start(this)
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

    override fun onDestroy() {
        super.onDestroy()
        synchronizer.stop()
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
            alpha = 0.85f
        }
    }

    fun onNavButtonSelected(index: Int) {
        if (navSelection == index) return
        val frameSize = 24
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
            3 -> R.id.nav_history_fragment
            else -> R.id.nav_zcon1_home_fragment
        }, null, navOptions)
    }
    override fun onAnimationRepeat(animation: Animator?) {
    }

    override fun onAnimationEnd(animation: Animator?) {
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

    var navSelection = -1
    var start = -1
    var end = -1





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
}

@Module
abstract class MainActivityModule {
    @ContributesAndroidInjector
    abstract fun contributeMainActivity(): MainActivity
}
