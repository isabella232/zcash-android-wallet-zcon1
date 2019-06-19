package cash.z.android.wallet


object Zcon1Store {
    const val address = "ztestsapling16rynkcwc5t7rxx3r9wen9l77s40l6sjq49ql6ffsqjqfqxwrrv4aezhnz73kuynw4e36zccl97f"

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
            "Swag T-Shirt",
            1999900000,
            memo = "T-Shirt swag for $buyerName",
            descriptionResId = R.string.swag_tee_description,
            imageResId = R.drawable.swag_tee
        ) { // 30 TAZ - miner's fee
            companion object {
                const val ID = 0
            }
        }

        data class SwagPad(val buyerName: String) : CartItem(
            ID,
            "Swag Notebook",
            3499900000,
            memo = "Notebook swag for $buyerName",
            descriptionResId = R.string.swag_pad_description,
            imageResId = R.drawable.swag_pad
        ) { // 30 TAZ - miner's fee
            companion object {
                const val ID = 1
            }
        }
    }
}