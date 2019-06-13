package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.FragmentZcon1HomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.presenter.BalancePresenter
import cash.z.android.wallet.ui.presenter.HomePresenterModule
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import cash.z.wallet.sdk.secure.Wallet
import dagger.Module
import dagger.android.ContributesAndroidInjector


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1HomeFragment : BaseFragment(), BalancePresenter.BalanceView {//, SwipeRefreshLayout.OnRefreshListener, HomePresenter.HomeView {
    private lateinit var binding: FragmentZcon1HomeBinding

    //
    // LifeCycle
    //

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return DataBindingUtil.inflate<FragmentZcon1HomeBinding>(
            inflater, R.layout.fragment_zcon1_home, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
        mainActivity?.onNavButtonSelected(0)
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
abstract class Zcon1HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(modules = [HomePresenterModule::class])
    abstract fun contributeZcon1HomeFragment(): Zcon1HomeFragment
}

