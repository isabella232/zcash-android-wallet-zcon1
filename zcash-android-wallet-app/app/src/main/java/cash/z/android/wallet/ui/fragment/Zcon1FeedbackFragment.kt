package cash.z.android.wallet.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.FragmentZcon1FeedbackBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.extention.Toaster
import cash.z.android.wallet.ui.util.Analytics.FeedbackFunnel
import cash.z.android.wallet.ui.util.Analytics.Tap.TAPPED_CANCEL_FEEDBACK
import cash.z.android.wallet.ui.util.Analytics.Tap.TAPPED_SUBMIT_FEEDBACK
import cash.z.android.wallet.ui.util.Analytics.trackAction
import cash.z.android.wallet.ui.util.Analytics.trackFunnelStep
import dagger.Module
import dagger.android.ContributesAndroidInjector
import javax.inject.Inject


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class Zcon1FeedbackFragment : BaseFragment() {

    @Inject
    lateinit var prefs: SharedPreferences
    private lateinit var binding: FragmentZcon1FeedbackBinding

    private lateinit var ratings: Array<View>

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
        with(binding) {
            buttonCancel.setOnClickListener(::onFeedbackCancel)
            buttonSubmit.setOnClickListener(::onFeedbackSubmit)

            ratings = arrayOf(feedbackExp1, feedbackExp2, feedbackExp3, feedbackExp4, feedbackExp5)
            ratings.forEach {
                it.setOnClickListener(::onRatingCilcked)
            }
        }

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setNavigationShown(true)
    }


    //
    // Private API
    //

    private fun onFeedbackSubmit(view: View) {
        Toaster.long("Thanks for the feedback!")
        trackAction(TAPPED_SUBMIT_FEEDBACK)

        val q1 = binding.inputQuestion1.editText?.text.toString()
        val q2 = binding.inputQuestion2.editText?.text.toString()
        val q3 = binding.inputQuestion3.editText?.text.toString()
        val rating = ratings.indexOfFirst { it.isActivated } + 1
        trackFunnelStep(FeedbackFunnel.Submitted(rating, q1, q2, q3))

        mainActivity?.navController?.navigateUp()
    }
    private fun onFeedbackCancel(view: View) {
        Toaster.short("Feedback cancelled")
        trackAction(TAPPED_CANCEL_FEEDBACK)
        trackFunnelStep(FeedbackFunnel.Cancelled)

        mainActivity?.navController?.navigateUp()
    }

    private fun onRatingCilcked(view: View) {
        ratings.forEach { it.isActivated = false }
        view.isActivated = !view.isActivated
    }
}


@Module
abstract class Zcon1FeedbackFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeZcon1FeedbackFragment(): Zcon1FeedbackFragment
}

