package cash.z.android.wallet.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.viewpager2.widget.ViewPager2
import cash.z.android.wallet.R
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.databinding.FragmentZcon1FirstrunBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import dagger.Module
import dagger.android.ContributesAndroidInjector
import javax.inject.Inject

class FirstrunFragment : ProgressFragment(R.id.progress_firstrun), Transition.TransitionListener {

    @Inject
    lateinit var prefs: SharedPreferences

    private lateinit var binding: FragmentZcon1FirstrunBinding

    //
    // Lifecycle
    //

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupSharedElementTransitions()
        return DataBindingUtil.inflate<FragmentZcon1FirstrunBinding>(
            inflater, R.layout.fragment_zcon1_firstrun, container, false
        ).let {
            binding = it
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
//        binding.buttonNext.setOnClickListener {
//            mainActivity?.navController?.navigate(
//                R.id.action_firstrun_fragment_to_sync_fragment,
//                null,
//                null,
//                null
//            )
//        }
        binding.viewPagerFirstrun.adapter = FirstRunAdapter()
        binding.viewPagerFirstrun.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val x = ((binding.viewPagerFirstrun.width * position + positionOffsetPixels) * computeFactor())
                binding.scrollBgParallax.scrollTo(x.toInt()
                        + binding.scrollBgParallax.getChildAt(0).width / 3, 0)
            }

            override fun onPageSelected(position: Int) {
            }

            private fun computeFactor(): Float {
                return (binding.scrollBgParallax.getChildAt(0)
                    .width / 2 - binding.viewPagerFirstrun.width) / (binding.viewPagerFirstrun.width * 3).toFloat()
            }
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (view?.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity?.setToolbarShown(false)
    }

    private fun setupSharedElementTransitions() {
        TransitionInflater.from(mainActivity).inflateTransition(R.transition.transition_zec_sent).apply {
            duration = 250L
            addListener(this@FirstrunFragment)
            this@FirstrunFragment.sharedElementEnterTransition = this
            this@FirstrunFragment.sharedElementReturnTransition = this
        }
    }

    override fun showProgress(progress: Int) {
        super.showProgress(progress)
        binding.textProgressFirstrun.text = getProgressText(progress)

    }

    override fun onProgressComplete() {
        super.onProgressComplete()
        binding.textProgressFirstrun.visibility = View.GONE
    }

    override fun onTransitionStart(transition: Transition) {
//        binding.buttonNext.alpha = 0f
    }

    override fun onTransitionEnd(transition: Transition) {
//        binding.buttonNext.animate().apply {
//            duration = 300L
//        }.alpha(1.0f)
        binding.textProgressFirstrun.animate().apply {
            duration = 300L
        }.alpha(1.0f)
    }

    fun onNext(input: String?) {
        if (input != null) {
            // Hide keyboard
            mainActivity?.getSystemService<InputMethodManager>()
                ?.hideSoftInputFromWindow(view?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            if (!onSaveName(input)) return
        }

        binding.viewPagerFirstrun.postDelayed({
            binding.viewPagerFirstrun.apply {
                if(currentItem == 2) navigateToHome() else currentItem += 1
            }
        }, 200L)

    }

    private fun onSaveName(input: String): Boolean {
        return if(input.isEmpty() || input.isBlank()) false
        else {
            prefs.edit()
                .putString(ZcashWalletApplication.PREFS_PSEUDONYM, input.replace("\\W".toRegex(), "_"))
                .apply()
            true
        }
    }

    private fun navigateToHome() {
        mainActivity?.navController?.navigate(R.id.action_firstrun_fragment_to_zcon1_home_fragment,
            null,
            NavOptions.Builder().setPopUpTo(R.id.mobile_navigation, true).build(),
            null
        )
    }

    override fun onTransitionResume(transition: Transition) {}
    override fun onTransitionPause(transition: Transition) {}
    override fun onTransitionCancel(transition: Transition) {}


    inner class FirstRunAdapter : RecyclerView.Adapter<FirstRunViewHolder>() {
        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> R.layout.item_first_run_page_0
                1 -> R.layout.item_first_run_page_1
                2 -> R.layout.item_first_run_page_2
                else -> R.layout.item_first_run_page_0
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstRunViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return FirstRunViewHolder(view)
        }

        override fun getItemCount(): Int {
            return 3
        }

        override fun onBindViewHolder(holder: FirstRunViewHolder, position: Int) {
            // nothing to do because this is just a viewPager
        }
    }

    inner class FirstRunViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val nextButton = view.findViewById<View>(R.id.button_next)
        private val inputText = view.findViewById<EditText?>(R.id.input_pseudonym)
        init {
            nextButton.setOnClickListener {
                onNext(inputText?.text?.toString())
            }
        }
    }
}


@Module
abstract class FirstrunFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeFirstrunFragment(): FirstrunFragment
}