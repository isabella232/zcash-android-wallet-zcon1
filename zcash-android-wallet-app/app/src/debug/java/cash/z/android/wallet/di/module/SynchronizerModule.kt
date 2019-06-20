package cash.z.android.wallet.di.module

import android.content.SharedPreferences
import android.preference.PreferenceManager
import cash.z.android.wallet.BuildConfig
import cash.z.android.wallet.ZcashWalletApplication
import cash.z.android.wallet.data.*
import cash.z.android.wallet.extention.toDbPath
import cash.z.android.wallet.sample.DeviceWallet
import cash.z.android.wallet.sample.SampleProperties.COMPACT_BLOCK_PORT
import cash.z.android.wallet.sample.SampleProperties.DEFAULT_BLOCK_POLL_FREQUENCY_MILLIS
import cash.z.android.wallet.sample.SampleProperties.DEFAULT_SERVER
import cash.z.android.wallet.sample.SampleProperties.DEFAULT_TRANSACTION_POLL_FREQUENCY_MILLIS
import cash.z.android.wallet.sample.SampleProperties.PREFS_SERVER_NAME
import cash.z.android.wallet.sample.Servers
import cash.z.android.wallet.sample.WalletConfig
import cash.z.android.wallet.ui.util.Broom
import cash.z.wallet.sdk.block.*
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.ActiveTransactionManager
import cash.z.wallet.sdk.data.PollingTransactionRepository
import cash.z.wallet.sdk.data.TransactionRepository
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.DEFAULT_BATCH_SIZE
import cash.z.wallet.sdk.ext.DEFAULT_RETRIES
import cash.z.wallet.sdk.jni.RustBackend
import cash.z.wallet.sdk.jni.RustBackendWelding
import cash.z.wallet.sdk.secure.Wallet
import cash.z.wallet.sdk.service.LightWalletGrpcService
import cash.z.wallet.sdk.service.LightWalletService
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import javax.inject.Named
import javax.inject.Singleton

/**
 * Module that contributes all the objects necessary for the synchronizer, which is basically everything that has
 * application scope.
 */
@Module
internal object SynchronizerModule {

    private val walletConfig = DeviceWallet

    private val rustBackend = RustBackend().also {
        if (BuildConfig.DEBUG) it.initLogs()
    }

    private val pollingTransactionRepository = PollingTransactionRepository(
        ZcashWalletApplication.instance,
        walletConfig.dataDbName,
        rustBackend,
        DEFAULT_TRANSACTION_POLL_FREQUENCY_MILLIS
    )


    @JvmStatic
    @Provides
    @Singleton
    fun providePrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(ZcashWalletApplication.instance)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideRustBackend(): RustBackendWelding {
        return rustBackend
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideWalletConfig(prefs: SharedPreferences): WalletConfig {
        return walletConfig
//        val walletName = prefs.getString(PREFS_WALLET_DISPLAY_NAME, BobWallet.displayName)
//        twig("FOUND WALLET DISPLAY NAME : $walletName")
//        return when(walletName) {
//            AliceWallet.displayName -> AliceWallet
//            BobWallet.displayName -> BobWallet // Default wallet
//            CarolWallet.displayName -> CarolWallet
//            DaveWallet.displayName -> DaveWallet
//            else -> WalletConfig.create(walletName)
//        }
    }

    @JvmStatic
    @Provides
    @Singleton
    @Named(PREFS_SERVER_NAME)
    fun provideServer(prefs: SharedPreferences): String {
        val serverName = prefs.getString(PREFS_SERVER_NAME, DEFAULT_SERVER.displayName)
        val server = Servers.values().firstOrNull { it.displayName == serverName }?.host ?: DEFAULT_SERVER.host
        twig("FOUND SERVER DISPLAY NAME : $serverName ($server)")
        return server
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideLightwalletService(@Named(PREFS_SERVER_NAME) server: String): LightWalletService {
        return LightWalletGrpcService(ZcashWalletApplication.instance, server, COMPACT_BLOCK_PORT)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideCompactBlockStore(walletConfig: WalletConfig): CompactBlockStore {
        return CompactBlockDbStore(ZcashWalletApplication.instance, walletConfig.cacheDbName)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideDownloader(service: LightWalletService, compatBlockStore: CompactBlockStore): CompactBlockDownloader {
        return CompactBlockDownloader(service, compatBlockStore)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideProcessorConfig(walletConfig: WalletConfig): ProcessorConfig {
        return ProcessorConfig(
            ZcashWalletApplication.instance.getDatabasePath(walletConfig.cacheDbName).absolutePath,
            ZcashWalletApplication.instance.getDatabasePath(walletConfig.dataDbName).absolutePath,
            downloadBatchSize = DEFAULT_BATCH_SIZE,
            blockPollFrequencyMillis = DEFAULT_BLOCK_POLL_FREQUENCY_MILLIS,
            retries = DEFAULT_RETRIES,
            maxBackoffInterval = 15_000L // testing
        )
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideProcessor(
        config: ProcessorConfig,
        downloader: CompactBlockDownloader,
        repository: TransactionRepository,
        rustBackend: RustBackendWelding
    ): CompactBlockProcessor {
        return CompactBlockProcessor(config, downloader, repository, rustBackend)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideRepository(): PollingTransactionRepository {
        return pollingTransactionRepository
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideBaseRepository(): TransactionRepository {
        return pollingTransactionRepository
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideWallet(
        application: ZcashWalletApplication,
        rustBackend: RustBackendWelding,
        walletConfig: WalletConfig
    ): Wallet {
        return Wallet(
            context = application,
            rustBackend = rustBackend,
            dataDbName = walletConfig.dataDbName,
            seedProvider = walletConfig.seedProvider,
            spendingKeyProvider = walletConfig.spendingKeyProvider
        )
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideManager(wallet: Wallet, repository: TransactionRepository, service: LightWalletService): ActiveTransactionManager {
        return ActiveTransactionManager(repository, service, wallet)
    }
//
//    @JvmStatic
//    @Provides
//    @Singleton
//    fun provideSynchronizer(
//        processor: CompactBlockProcessor,
//        repository: TransactionRepository,
//        manager: ActiveTransactionManager,
//        wallet: Wallet
//    ): Synchronizer {
//        return SdkSynchronizer(processor, repository, manager, wallet, DEFAULT_STALE_TOLERANCE)
//    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideBroom(
        sender: TransactionSender,
        wallet: Wallet,
        rustBackend: RustBackendWelding,
        walletConfig: WalletConfig
    ): Broom {
        return Broom(
            sender,
            rustBackend,
            walletConfig.cacheDbName,
            wallet
        )
    }

//    @JvmStatic
//    @Provides
//    @Singleton
//    fun provideChipBucket(): ChipBucket {
//        return InMemoryChipBucket()
//    }


    @JvmStatic
    @Provides
    @Singleton
    fun provideTransactionManager(): TransactionManager {
        return PersistentTransactionManager()
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideTransactionSender(
        manager: TransactionManager,
        service: LightWalletService,
        clearedTxProvider: ChannelListValueProvider<WalletTransaction>
    ): TransactionSender {
        return PersistentTransactionSender(manager, service, object : ClearedTransactionProvider {
            override fun getCleared(): List<WalletTransaction> {
                return clearedTxProvider.getLatestValue()
            }
        })
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideClearedTransactionProvider(): ChannelListValueProvider<WalletTransaction> {
        return ChannelListValueProvider(ConflatedBroadcastChannel(listOf()))
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideTransactionEncoder(wallet: Wallet, repository: TransactionRepository): RawTransactionEncoder {
        return WalletTransactionEncoder(wallet, repository)
    }

    @JvmStatic
    @Provides
    @Singleton
    fun provideDataSynchronizer(
        wallet: Wallet,
        ledger: PollingTransactionRepository,
        sender: TransactionSender,
        processor: CompactBlockProcessor,
        encoder: RawTransactionEncoder,
        clearedTxProvider: ChannelListValueProvider<WalletTransaction>
    ): DataSyncronizer {
        return StableSynchronizer(wallet, ledger, sender, processor, encoder, clearedTxProvider)
    }
}
