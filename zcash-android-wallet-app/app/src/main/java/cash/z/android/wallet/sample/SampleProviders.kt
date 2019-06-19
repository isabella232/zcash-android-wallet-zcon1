package cash.z.android.wallet.sample

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import cash.z.android.wallet.ZcashWalletApplication
import okio.ByteString
import java.nio.charset.Charset
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Deprecated(message = InsecureWarning.message)
class SampleImportedSeedProvider(private val seedHex: String) : ReadOnlyProperty<Any?, ByteArray> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): ByteArray {
        val bytes = ByteString.decodeHex(seedHex).toByteArray()
        val stringBytes = String(bytes, Charset.forName("UTF-8"))
        Log.e("TWIG-x", "byteString: $stringBytes")
        return decodeHex(seedHex).also { Log.e("TWIG-x", "$it") }
    }

    fun decodeHex(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val d1 = decodeHexDigit(hex[i * 2]) shl 4
            val d2 = decodeHexDigit(hex[i * 2 + 1])
            result[i] = (d1 + d2).toByte()
        }
        return result
    }

    private fun decodeHexDigit(c: Char): Int {
        if (c in '0'..'9') return c - '0'
        if (c in 'a'..'f') return c - 'a' + 10
        if (c in 'A'..'F') return c - 'A' + 10
        throw IllegalArgumentException("Unexpected hex digit: $c")
    }
}

@Deprecated(message = InsecureWarning.message)
class SampleSpendingKeySharedPref(private val fileName: String) : ReadWriteProperty<Any?, String> {

    private fun getPrefs() = ZcashWalletApplication.instance
        .getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        val preferences = getPrefs()
        return preferences.getString("spending", null)
                ?: throw IllegalStateException(
                    "Spending key was not there when we needed it! Make sure it was saved " +
                            "during the first run of the app, when accounts were created!"
                )
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        Log.e("TWIG", "Spending key is being stored")
        val preferences = getPrefs()
        val editor = preferences.edit()
        editor.putString("spending", value)
        editor.apply()
    }
}

/**
 * This is intentionally insecure. Wallet makers have told us storing keys is their specialty so we don't put a lot of
 * energy here. A true implementation would create a key using user interaction, perhaps with a password they know that
 * is never stored, along with requiring user authentication for key use (i.e. fingerprint/PIN/pattern/etc). From there,
 * one of these approaches might be helpful to store the key securely:
 *
 * https://developer.android.com/training/articles/keystore.html
 * https://github.com/scottyab/AESCrypt-Android/blob/master/aescrypt/src/main/java/com/scottyab/aescrypt/AESCrypt.java
 * https://github.com/iamMehedi/Secured-Preference-Store
 */
@SuppressLint("HardwareIds")
@Deprecated(message = InsecureWarning.message)
class SeedGenerator {
    companion object {
        @Deprecated(message = InsecureWarning.message)
        fun getDeviceId(): String {
            val id = Build.FINGERPRINT + "ZCon1" + Settings.Secure.getString(ZcashWalletApplication.instance.contentResolver, Settings.Secure.ANDROID_ID)
            return id.replace("\\W".toRegex(), "_")
        }
    }
}


internal object InsecureWarning {
    const val message = "Do not use this because it is insecure and only intended for test code and samples. " +
            "Instead, use the Android Keystore system or a 3rd party library that leverages it."
}