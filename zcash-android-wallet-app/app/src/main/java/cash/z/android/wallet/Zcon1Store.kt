package cash.z.android.wallet


object Zcon1Store {
    const val address = "ztestsapling1yu2zy9aanf8pjf2qvm4qmn4k6q57y2d9fcs3vz0guthxx3m2aq57qm6hkx0580m9u9635xh6ttr"

    sealed class CartItem(
        val id: Int = -1,
        val name: String = "",
        val zatoshiValue: Long = -1L,
        val toAddress: String = address,
        val memo: String = "",
        val descriptionResId: Int,
        val imageResId: Int
    ) {
        data class SwagTee(val buyerName: String) : CartItem(
            ID,
            "T-Shirt",
            199990000,
            memo = buyerName,
            descriptionResId = R.string.swag_tee_description,
            imageResId = R.drawable.swag_tee
        ) { // 30 TAZ - miner's fee
            companion object {
                const val ID = 0
            }
        }

        data class SwagPad(val buyerName: String) : CartItem(
            ID,
            "Notebook",
            399990000,
            memo = buyerName,
            descriptionResId = R.string.swag_pad_description,
            imageResId = R.drawable.swag_pad
        ) { // 30 TAZ - miner's fee
            companion object {
                const val ID = 1
            }
        }
    }
}