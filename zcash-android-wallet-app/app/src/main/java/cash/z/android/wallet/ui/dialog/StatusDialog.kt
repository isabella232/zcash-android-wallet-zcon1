package cash.z.android.wallet.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.DialogStatusBinding
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class StatusDialog(
    availableBalance: Long,
    syncingBalance: Long,
    pendingChipBalance: Long,
    summary: String
) : DialogFragment() {

    init {
        arguments = Bundle().apply {
            putLong(ARG_BALANCE_AVAILABLE, availableBalance)
            putLong(ARG_BALANCE_SYNCING, syncingBalance)
            putLong(ARG_BALANCE_PENDING, pendingChipBalance)
            putString(ARG_SUMMARY, summary)
        }
    }

    lateinit var binding: DialogStatusBinding


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DataBindingUtil.inflate(activity!!.layoutInflater, R.layout.dialog_status, null, false)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val lp = dialog.window.attributes
        lp.gravity = Gravity.TOP
        lp.y = 0

        return dialog
    }
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        return super.onCreateView(inflater, container, savedInstanceState)
//        val window = dialog?.window
//
//        // set "origin" to top left corner, so to speak
//        window!!.setGravity(Gravity.TOP or Gravity.LEFT)
//
//        // after that, setting values for x and y works "naturally"
//        val params = window.attributes
//        params.x = 0
//        params.y = -200
//        window.attributes = params
//    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fun String.long() = requireArguments().getLong(this)
        fun Long.zec() = convertZatoshiToZecString(6)

        val available = ARG_BALANCE_AVAILABLE.long()
        val pending = ARG_BALANCE_PENDING.long()
        val syncing = ARG_BALANCE_SYNCING.long()

        binding.textAvailableValue.text = available.zec()
        binding.textPendingValue.text = pending.zec()
        binding.textSyncingValue.text = syncing.zec()
        binding.textTotalValue.text = (available + pending + syncing).zec()
        binding.textSummaryValue.text = requireArguments().getString(ARG_SUMMARY)

        binding.buttonOk.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val ARG_BALANCE_SYNCING = "arg_balance_syncing"
        const val ARG_BALANCE_AVAILABLE = "arg_balance_available"
        const val ARG_BALANCE_PENDING = "arg_balance_pending"
        const val ARG_SUMMARY = "arg_summary"
    }

}