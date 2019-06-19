package cash.z.android.wallet.extention

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import cash.z.android.wallet.ZcashWalletApplication

internal inline fun tryIgnore(block: () -> Unit) {
    try { block() } catch(ignored: Throwable) {}
}

internal inline fun <T> tryNull(block: () -> T): T? {
    return try { block() } catch(ignored: Throwable) { null }
}

internal inline fun <T> onErrorReturn(noinline errorHandler: (Throwable) -> T, block: () -> T): T {
    return try {
        block()
    } catch (error: Throwable) {
        errorHandler(error)
    }
}

internal inline fun String.truncate(): String {
    return "${substring(0..4)}...${substring(length-5, length)}"
}

internal inline fun String.masked(): String {
    return "${substring(0..4)}.**masked**.${substring(length-5, length)}"
}

internal inline fun String.toDbPath(): String {
    return ZcashWalletApplication.instance.getDatabasePath(this).absolutePath
}

fun Context.copyToClipboard(text: CharSequence) {
    val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip = ClipData.newPlainText("Zcon1", text)
}