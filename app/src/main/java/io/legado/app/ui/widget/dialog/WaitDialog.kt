package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import io.legado.app.databinding.DialogWaitBinding
import io.legado.app.lib.theme.filletBackground


@Suppress("unused")
class WaitDialog(context: Context) : Dialog(context) {

    val binding = DialogWaitBinding.inflate(layoutInflater)

    init {
        setCanceledOnTouchOutside(false)
        setContentView(binding.root)
        window?.setBackgroundDrawable(context.filletBackground)
    }

    fun setText(text: String): WaitDialog {
        binding.tvMsg.text = text
        return this
    }

    fun setText(res: Int): WaitDialog {
        binding.tvMsg.setText(res)
        return this
    }

}