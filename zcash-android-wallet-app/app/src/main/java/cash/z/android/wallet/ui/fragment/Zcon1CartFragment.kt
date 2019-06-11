package cash.z.android.wallet.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.databinding.FragmentZcon1CartBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.extention.Toaster
import cash.z.android.wallet.extention.alert
import cash.z.android.wallet.ui.presenter.CartPresenter
import cash.z.android.wallet.ui.presenter.CartPresenterModule
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1CartFragment : BaseFragment(), CartPresenter.Zcon1CartView {//, SwipeRefreshLayout.OnRefreshListener, HomePresenter.HomeView {

    @Inject
    lateinit var cartPresenter: CartPresenter
    @Inject
    lateinit var prefs: SharedPreferences
    private lateinit var binding: FragmentZcon1CartBinding

    //
    // LifeCycle
    //

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
//        viewsInitialized = false
//        setupSharedElementTransitions()
        return DataBindingUtil.inflate<FragmentZcon1CartBinding>(
                inflater, R.layout.fragment_zcon1_cart, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSwagShirt.setOnClickListener {
            showSendDialog(CartPresenter.CartItem.SwagShirt(prefs.getString(ZcashWalletApplication.PREFS_PSEUDONYM, null)!!))
        }
        binding.buttonSwagPad.setOnClickListener {
            showSendDialog(CartPresenter.CartItem.SwagPad(prefs.getString(ZcashWalletApplication.PREFS_PSEUDONYM, null)!!))
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
    }

    override fun onResume() {
        super.onResume()
        launch {
            cartPresenter.start()
        }
    }

    override fun onPause() {
        super.onPause()
        cartPresenter.stop()
    }

    fun showSendDialog(item: CartPresenter.CartItem) {
//        setSendEnabled(false) // partially because we need to lower the button elevation
        mainActivity?.alert(
            message = "Are you sure you'd like to buy a ${item.itemName}",
            positiveButtonResId = R.string.ok_allcaps,
            negativeButtonResId = R.string.cancel,
            positiveAction = { cartPresenter.buyItem(item) }
        )
    }


    //
    // Zcon1CartView Implementation
    //

    override fun updateAvailableBalance(available: Long) {
        val balance = available.convertZatoshiToZecString(6).split(".")
        binding.textIntegerDigits.text = balance[0]
        binding.textFractionalDigits.text = ""
        if (balance.size > 1) {
            binding.textFractionalDigits.text = ".${balance[1]}"
        }
    }

    override fun orderFailed(error: CartPresenter.PurchaseResult.Failure) {
        mainActivity?.alert(
            message = "Purchased failed due to:\n\n${error.reason}"
        )
    }

    override fun orderUpdated(processing: CartPresenter.PurchaseResult.Processing) {
        Toaster.short(processing.state.toString())
    }
}


@Module
abstract class Zcon1CartFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(modules = [CartPresenterModule::class])
    abstract fun contributeZcon1CartFragment(): Zcon1CartFragment
}

