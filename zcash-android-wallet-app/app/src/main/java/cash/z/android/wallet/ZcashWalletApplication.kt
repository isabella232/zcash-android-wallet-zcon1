package cash.z.android.wallet

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDex
import cash.z.android.wallet.di.component.DaggerApplicationComponent
import cash.z.android.wallet.ui.util.Analytics
import cash.z.android.wallet.ui.util.Analytics.trackCrash
import cash.z.wallet.sdk.data.TroubleshootingTwig
import cash.z.wallet.sdk.data.Twig
import com.facebook.stetho.Stetho
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication


class ZcashWalletApplication : DaggerApplication() {

    override fun onCreate() {
        instance = this
        // Setup handler for uncaught exceptions.
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(ExceptionReporter(Thread.getDefaultUncaughtExceptionHandler()))
        Stetho.initializeWithDefaults(this)
        Twig.plant(TroubleshootingTwig())
    }

    /**
     * Implement the HasActivityInjector behavior so that dagger knows which [AndroidInjector] to use.
     */
    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerApplicationComponent.builder().create(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    companion object {
        lateinit var instance: ZcashWalletApplication

        // Pref keys
        const val PREFS_PSEUDONYM = "Swag.PREFS_PSEUDONYM"
    }

    class ExceptionReporter(val ogHandler: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread?, e: Throwable?) {
            trackCrash(e, "Top-level exception wasn't caught by anything else!")
            Analytics.clear()
            ogHandler.uncaughtException(t, e)
        }
    }
}