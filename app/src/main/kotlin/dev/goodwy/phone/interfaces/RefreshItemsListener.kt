package dev.goodwy.phone.interfaces

interface RefreshItemsListener {
    fun refreshItems(invalidate: Boolean = false, needUpdate: Boolean = false, callback: (() -> Unit)? = null)
}
