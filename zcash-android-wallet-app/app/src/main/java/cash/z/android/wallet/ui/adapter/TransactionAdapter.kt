package cash.z.android.wallet.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cash.z.android.wallet.R
import cash.z.android.wallet.extention.toAppColor
import cash.z.android.wallet.extention.toRelativeTimeString
import cash.z.wallet.sdk.entity.SentTransaction
import cash.z.wallet.sdk.ext.MINERS_FEE_ZATOSHI
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue


class TransactionAdapter(@LayoutRes val itemResId: Int = R.layout.item_transaction) : ListAdapter<TransactionUiModel, TransactionViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(itemResId, parent, false)
        return TransactionViewHolder(itemView)
    }
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) = holder.bind(getItem(position))
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionUiModel>() {
    override fun areItemsTheSame(oldItem: TransactionUiModel, newItem: TransactionUiModel) = oldItem.minedHeight == newItem.minedHeight
    override fun areContentsTheSame(oldItem: TransactionUiModel, newItem: TransactionUiModel): Boolean = oldItem == newItem
}

class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val status = itemView.findViewById<View>(R.id.view_transaction_status)
    private val icon = itemView.findViewById<ImageView>(R.id.image_transaction_type)
    private val timestamp = itemView.findViewById<TextView>(R.id.text_transaction_timestamp)
    private val amount = itemView.findViewById<TextView>(R.id.text_transaction_amount)
    private val address = itemView.findViewById<TextView>(R.id.text_transaction_address)
    private val memo = itemView.findViewById<TextView>(R.id.text_transaction_memo)
    private val formatter = SimpleDateFormat("M/d h:mma", Locale.getDefault())

    fun bind(tx: TransactionUiModel) {
        val useSend = (tx.isSend) && !tx.isPokerChip
        val isHistory = icon != null
        val sign = if (useSend) "- " else "+ "
        val amountColor = if (useSend) R.color.colorAccent else R.color.zcashPurple_accent
        val defaultColor = R.color.text_light_dimmed
        val actionColor = if (tx.action == null) amountColor else defaultColor
        val transactionColor = if (useSend) R.color.send_associated else R.color.receive_associated
        val transactionIcon = if (useSend || (tx.isPokerChip && (tx as? SentTransaction)?.toAddress != "Redeemed")) R.drawable.ic_sent_transaction else R.drawable.ic_received_transaction
        val zecAbsoluteValue = tx.zatoshiValue.absoluteValue + if(tx.isSend) MINERS_FEE_ZATOSHI else 0

        timestamp.text = if (tx.timestampMillis == 0L) "Pending"
                         else (if (isHistory) formatter.format(tx.timestampMillis) else (tx.timestampMillis).toRelativeTimeString())
        amount.text = tx.action ?: "$sign${zecAbsoluteValue.convertZatoshiToZecString(2)}"
        amount.setTextColor(actionColor.toAppColor())

        // maybes - and if this gets to be too much, then pass in a custom holder when constructing the adapter, instead
        status?.setBackgroundColor(transactionColor.toAppColor())
        address?.text = tx.status
        memo?.text = tx?.memo ?: ""
        icon?.setImageResource(transactionIcon)
    }
}

data class TransactionUiModel(
    val action: String? = null,
    val status: String? = null,
    val isSend: Boolean = false,
    val isPending: Boolean = false,
    val isPokerChip: Boolean = false,
    val isSwag: Boolean = false,
    val timestampMillis: Long = 0L,
    val zatoshiValue: Long =  0L,
    val minedHeight: Int = 0,
    val memo: String? = null
)

val TransactionUiModel.isMined get() = this.minedHeight > 0