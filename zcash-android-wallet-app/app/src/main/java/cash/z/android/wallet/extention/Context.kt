package cash.z.android.wallet.extention

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal val NO_ACTION = {}

/**
 * Calls context.alert with the given string.
 */
internal fun Context.alert(
    @StringRes messageResId: Int,
    @StringRes titleResId: Int = 0,
    @StringRes positiveButtonResId: Int = android.R.string.ok,
    @StringRes negativeButtonResId: Int = android.R.string.cancel,
    negativeAction: () -> Unit = NO_ACTION,
    positiveAction: () -> Unit = NO_ACTION
) {
    alert(
        message = getString(messageResId),
        title = if (titleResId == 0) null else getString(titleResId),
        positiveButtonResId = positiveButtonResId,
        negativeButtonResId = negativeButtonResId,
        negativeAction = negativeAction,
        positiveAction = positiveAction
    )
}

/**
 * Show an alert with the given message, if the block exists, it will execute after the user clicks the positive button,
 * while clicking the negative button will abort the block. If no block exists, there will only be a positive button.
 */
internal fun Context.alert(
    message: String,
    title: String? = null,
    @StringRes positiveButtonResId: Int = android.R.string.ok,
    @StringRes negativeButtonResId: Int = android.R.string.cancel,
    negativeAction: (() -> Unit) = NO_ACTION,
    positiveAction: (() -> Unit) = NO_ACTION
) {
    val builder = MaterialAlertDialogBuilder(this)
        .setMessage(message).apply {
            if(title != null) setTitle(title)
        }
        .setCancelable(false)
        .setPositiveButton(positiveButtonResId) { dialog, _ ->
            dialog.dismiss()
            positiveAction()
        }
    if (positiveAction !== NO_ACTION || negativeAction !== NO_ACTION) {
        builder.setNegativeButton(negativeButtonResId) { dialog, _ ->
            dialog.dismiss()
            negativeAction()
        }
    }
    builder.show()
}