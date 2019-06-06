package cash.z.android.wallet.ui.util

import android.animation.Animator
import com.airbnb.lottie.LottieAnimationView

inline fun LottieAnimationView.doOnComplete(crossinline block: () -> Unit) {
    addAnimatorListener(object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator?) {
        }

        override fun onAnimationEnd(animation: Animator?) {
            removeAllAnimatorListeners()
            block()
        }

        override fun onAnimationCancel(animation: Animator?) {
        }

        override fun onAnimationStart(animation: Animator?) {
        }
    })
}


fun LottieAnimationView.playToFrame(i: Int) {
    repeatCount = 0
    setMaxFrame(i)
    playAnimation()
}