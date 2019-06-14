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
import cash.z.android.wallet.databinding.FragmentZcon1FeedbackBinding
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
class Zcon1FeedbackFragment : BaseFragment() {

    @Inject
    lateinit var prefs: SharedPreferences
    private lateinit var binding: FragmentZcon1FeedbackBinding


    //
    // LifeCycle
    //

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return DataBindingUtil.inflate<FragmentZcon1FeedbackBinding>(
                inflater, R.layout.fragment_zcon1_feedback, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
    }


    //
    // Private API
    //

}


@Module
abstract class Zcon1FeedbackFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeZcon1FeedbackFragment(): Zcon1FeedbackFragment
}

