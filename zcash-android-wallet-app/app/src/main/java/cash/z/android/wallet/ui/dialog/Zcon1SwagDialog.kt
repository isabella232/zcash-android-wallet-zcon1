package cash.z.android.wallet.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import cash.z.android.wallet.R
import cash.z.android.wallet.Zcon1Store
import cash.z.android.wallet.Zcon1Store.CartItem.SwagPad
import cash.z.android.wallet.Zcon1Store.CartItem.SwagTee
import cash.z.android.wallet.databinding.DialogSwagBinding
import cash.z.android.wallet.ui.activity.MainActivity
import cash.z.wallet.sdk.ext.convertZatoshiToZecString
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class Zcon1SwagDialog(argProductId: Int, argBuyerName: String) : DialogFragment() {

    init {
        arguments = Bundle().apply {
            putInt(ARG_PRODUCT_ID, argProductId)
            putString(ARG_BUYER_NAME, argBuyerName)
        }
    }

    lateinit var binding: DialogSwagBinding
    lateinit var product: Zcon1Store.CartItem

    private val productId: Int
        get() = arguments!!.getInt(ARG_PRODUCT_ID)

    private val buyerName: String
        get() = arguments?.getString(ARG_BUYER_NAME)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DataBindingUtil.inflate(activity!!.layoutInflater, R.layout.dialog_swag, null, false)
        return MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create().also {
//                it.window.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        product = when (productId) {
            SwagPad.ID -> SwagPad(buyerName)
            SwagTee.ID -> SwagTee(buyerName)
            else -> throw IllegalArgumentException("Error unrecognized product ID: $productId")
        }

        binding.swagDescription.setText(product.descriptionResId)
        binding.swagProduct.text = product.name
        binding.swagPrice.text = product.zatoshiValue.convertZatoshiToZecString(1) + " TAZ"
        binding.swagImage.setImageResource(product.imageResId)

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        binding.buttonPurchase.setOnClickListener {
            dismiss()
            (activity as? MainActivity)!!.buyProduct(product)
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "arg_product_id"
        const val ARG_BUYER_NAME = "arg_buyer_name"
    }

}