package cash.z.android.wallet.ui.fragment

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import cash.z.android.wallet.ui.activity.MainActivity
import dagger.android.support.DaggerFragment
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

abstract class BaseFragment : DaggerFragment(), ScopedFragment {

    val mainActivity: MainActivity? get() = activity as MainActivity?
}

interface ScopedFragment : CoroutineScope, LifecycleOwner {
    override val coroutineContext: CoroutineContext get() = lifecycle.coroutineScope.coroutineContext
}