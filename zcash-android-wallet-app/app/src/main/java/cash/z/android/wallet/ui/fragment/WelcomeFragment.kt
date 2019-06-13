package cash.z.android.wallet.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavOptions
import androidx.navigation.fragment.FragmentNavigatorExtras
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication.Companion.PREFS_PSEUDONYM
import cash.z.android.wallet.databinding.FragmentZcon1WelcomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.ui.util.doOnComplete
import cash.z.android.wallet.ui.util.playToFrame
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject


class WelcomeFragment : ProgressFragment(R.id.progress_welcome) {

    @Inject
    lateinit var prefs: SharedPreferences

    private lateinit var binding: FragmentZcon1WelcomeBinding

    //
    // Lifecycle
    //

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return DataBindingUtil.inflate<FragmentZcon1WelcomeBinding>(
            inflater, R.layout.fragment_zcon1_welcome, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lottieEccLogo.doOnComplete {
            launch { onNext() }
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.setToolbarShown(false)
        binding.lottieEccLogo.playToFrame(240/100)
        binding.lottieEccLogo.speed = 1.4f
    }

    private suspend fun onNext() = coroutineScope {
        if (mainActivity != null) {
            val hasName = prefs.getString(PREFS_PSEUDONYM, null) == null
            val destination =
                if (hasName) R.id.action_welcome_fragment_to_firstrun_fragment
                else R.id.action_welcome_fragment_to_home_fragment

            val extras = FragmentNavigatorExtras(
                binding.progressWelcome to binding.progressWelcome.transitionName
            )

            mainActivity?.navController?.navigate(
                destination,
                null,
                NavOptions.Builder().setPopUpTo(R.id.mobile_navigation, true).build(),
                extras
            )
        }
    }
}

@Module
abstract class WelcomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeWelcomeFragment(): WelcomeFragment

}