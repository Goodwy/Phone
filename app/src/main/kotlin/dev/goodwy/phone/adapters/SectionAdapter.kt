package dev.goodwy.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class SectionAdapter(
    private val layoutId: Int,
    private val onBind: ((view: android.view.View) -> Unit)? = null
) : RecyclerView.Adapter<SectionAdapter.ViewHolder>() {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBind?.invoke(holder.view)
    }

    override fun getItemCount() = 1
}
