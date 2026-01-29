package dev.goodwy.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class MainAdapter(
    private val sections: List<Section>
) : RecyclerView.Adapter<MainAdapter.ViewHolder>() {

    data class Section(
        val layoutId: Int,
        val viewType: Int,
        var view: View? = null,
        val onBind: ((View) -> Unit)? = null,
        var isVisible: Boolean = true
    )

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    // Cache visible positions
    private val visiblePositions = mutableListOf<Int>()

    init {
        updateVisiblePositions()
    }

    private fun updateVisiblePositions() {
        visiblePositions.clear()
        sections.forEachIndexed { index, section ->
            if (section.isVisible) {
                visiblePositions.add(index)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val actualPosition = visiblePositions[position]
        return sections[actualPosition].viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val section = sections.first { it.viewType == viewType }
        val view = LayoutInflater.from(parent.context).inflate(section.layoutId, parent, false)
        section.view = view
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val actualPosition = visiblePositions[position]
        sections[actualPosition].onBind?.invoke(holder.view)
    }

    override fun getItemCount() = visiblePositions.size

    fun getViewByType(viewType: Int): View? {
        return sections.firstOrNull { it.viewType == viewType }?.view
    }

    // Method for updating section visibility
    fun updateSectionVisibility(viewType: Int, isVisible: Boolean) {
        val section = sections.firstOrNull { it.viewType == viewType }
        section?.isVisible = isVisible
        updateVisiblePositions()
        notifyDataSetChanged()
    }

    // Method for obtaining all sections
    fun getSections(): List<Section> = sections
}
