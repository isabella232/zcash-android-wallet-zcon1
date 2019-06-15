package cash.z.android.wallet.ui.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

open class BaseLifecycleObserver() : DefaultLifecycleObserver, CoroutineScope {

    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(owner: LifecycleOwner) {
        job = Job()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        job.cancel()
    }
}