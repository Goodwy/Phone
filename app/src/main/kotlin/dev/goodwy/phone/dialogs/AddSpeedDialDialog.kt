package dev.goodwy.phone.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.showKeyboard
import dev.goodwy.phone.R
import dev.goodwy.phone.databinding.DialogAddSpeedDialBinding
import dev.goodwy.phone.models.SpeedDial

class AddSpeedDialDialog(
    private val activity: Activity,
    private val speedDial: SpeedDial,
    private val callback: (name: String) -> Unit,
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogAddSpeedDialBinding.inflate(activity.layoutInflater).apply {
            addSpeedDialEditText.apply {
                setText(speedDial.number)
                hint = speedDial.number
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.speed_dial) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.addSpeedDialEditText)
                    alertDialog.getButton(BUTTON_POSITIVE).apply {
                        setOnClickListener {
                            val newTitle = binding.addSpeedDialEditText.text.toString()
                            callback(newTitle)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }
}
