package cash.z.android.wallet.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication.Companion.PREFS_PSEUDONYM
import cash.z.android.wallet.Zcon1Store
import cash.z.android.wallet.databinding.FragmentZcon1CartBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.dialog.Zcon1SwagDialog
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1CartFragment : BaseFragment(), BalancePresenter.BalanceView {

    @Inject
    lateinit var prefs: SharedPreferences
    private lateinit var binding: FragmentZcon1CartBinding

    private val buyerName: String
        get() = prefs.getString(PREFS_PSEUDONYM, null)!!

    //
    // LifeCycle
    //

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
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
            showSwagDialog(Zcon1Store.CartItem.SwagTee(buyerName))
        }
        binding.buttonSwagPad.setOnClickListener {
            showSwagDialog(Zcon1Store.CartItem.SwagPad(buyerName))
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.balancePresenter?.addBalanceView(this)
    }

    override fun onPause() {
        super.onPause()
        mainActivity?.balancePresenter?.removeBalanceView(this)
    }


    //
    // Private API
    //

    private fun showSwagDialog(item: Zcon1Store.CartItem) {
        Zcon1SwagDialog(item.id, buyerName).show(activity!!.supportFragmentManager, item.name)
    }


    //
    // BalanceView Implementation
    //

    override fun updateBalance(balanceInfo: Wallet.WalletBalance) {
        val balance = balanceInfo.available.convertZatoshiToZecString(6).split(".")
        binding.textIntegerDigits.text = balance[0]
        binding.textFractionalDigits.text = ""
        if (balance.size > 1) {
            binding.textFractionalDigits.text = ".${balance[1]}"
        }
    }
}


@Module
abstract class Zcon1CartFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeZcon1CartFragment(): Zcon1CartFragment
}

