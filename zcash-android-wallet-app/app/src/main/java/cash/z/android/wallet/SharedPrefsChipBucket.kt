package cash.z.android.wallet

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class SharedPrefsChipBucket : InMemoryChipBucket() {

    @Inject
    lateinit var prefs: SharedPreferences

    override suspend fun save() = withContext(IO) {
        if (chips.isNotEmpty()) {
            val serialized = chips.map { "${it.id}|${it.redeemed}|${it.created}" }.toHashSet()
            prefs.edit()
                .putStringSet(PREFS_KEY, serialized)
                .commit()
        }
    }

    override suspend fun restore(): SharedPrefsChipBucket = withContext(IO) {
        prefs.getStringSet(PREFS_KEY, null)?.forEach {
            val parts = it.split("|")
            add(PokerChip(
                id = parts[0],
                redeemed = parts[1].toLong(),
                created = parts[2].toLong()
            ))
        }

        this@SharedPrefsChipBucket
    }

    companion object {
        const val PREFS_KEY = "chip_bucket_zcon1"
    }
}