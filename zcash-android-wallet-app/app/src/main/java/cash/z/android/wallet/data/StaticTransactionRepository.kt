package cash.z.android.wallet.data

import cash.z.android.wallet.ZcashWalletApplication
import cash.z.wallet.sdk.data.PollingTransactionRepository
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.db.DerivedDataDb
import cash.z.wallet.sdk.jni.RustBackendWelding
import kotlinx.coroutines.CoroutineScope

// TODO: reverse this hierarchy so that Polling depends on this class and has all the overhead specific to polling, leaving this class more streamlined and efficient
class StaticTransactionRepository(dataDbName: String, rustBackend: RustBackendWelding, dbCallback: (DerivedDataDb) -> Unit = {}) :
    PollingTransactionRepository(ZcashWalletApplication.instance, dataDbName, rustBackend,2000L, dbCallback) {

//    override fun start(parentScope: CoroutineScope) {
//        twig("starting repository ignored because this DB does not poll")
//    }
//
//    override fun stop() {
//        twig("stopping repository ignored because this DB does not poll")
//    }

}