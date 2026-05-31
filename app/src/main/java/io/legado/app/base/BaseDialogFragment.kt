package io.legado.app.base

import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.filletBackground
import io.legado.app.lib.theme.popupBackgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setBackgroundKeepPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


abstract class BaseDialogFragment(
    @LayoutRes layoutID: Int,
    private val adaptationSoftKeyboard: Boolean = false
) : DialogFragment(layoutID) {

    private var onDismissListener: OnDismissListener? = null

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this.onDismissListener = onDismissListener
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (adaptationSoftKeyboard) {
            dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
        } else if (AppConfig.isEInkMode) {
            dialog?.window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val attr = it.attributes
                attr.dimAmount = 0.0f
                attr.windowAnimations = 0
                it.attributes = attr
                it.decorView.setBackgroundKeepPadding(R.color.transparent)
            }
            // 修改gravity的时机一般在子类的onStart方法中, 因此需要在onStart之后执行.
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    when (dialog?.window?.attributes?.gravity) {
                        Gravity.TOP -> view?.setBackgroundResource(R.drawable.bg_eink_border_bottom)
                        Gravity.BOTTOM -> view?.setBackgroundResource(R.drawable.bg_eink_border_top)
                        else -> {
                            val padding = 2.dpToPx();
                            view?.setPadding(padding, padding, padding, padding)
                            view?.setBackgroundResource(R.drawable.bg_eink_border_dialog)
                        }
                    }
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            //不加这个android 5.0对话框顶部会有空白
            setStyle(STYLE_NO_TITLE, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (adaptationSoftKeyboard) {
            view.findViewById<View>(R.id.vw_bg)?.setOnClickListener(null)
            view.setOnClickListener { dismiss() }
        }
        if (!AppConfig.isEInkMode) {
            dialog?.window?.setBackgroundDrawable(requireContext().filletBackground)
            // 对话框文字颜色适配自定义浮窗背景色（跳过自定义主题控件，保留其自身着色逻辑）
            val dialogTextColor = if (ColorUtils.isColorLight(requireContext().popupBackgroundColor)) {
                0xDE000000.toInt()
            } else {
                0xFFFFFFFF.toInt()
            }
            view.post {
                applyDialogTextColor(view, dialogTextColor)
            }
        }
        view.findViewById<View>(R.id.tool_bar)?.setBackgroundColor(requireContext().primaryColor)
        onFragmentCreated(view, savedInstanceState)
        observeLiveBus()
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    companion object {
        /**
         * 递归遍历 View 树，对标准 TextView/ImageView（非按钮/输入框/自定义主题控件）设置文字/图标颜色。
         * 跳过 PrimaryTextView、SecondaryTextView 等自着色控件。
         */
        private fun applyDialogTextColor(view: View, textColor: Int) {
            if (view is TextView
                && view !is Button
                && view !is EditText
                && view.javaClass.name.let {
                    it == "android.widget.TextView"
                            || it == "androidx.appcompat.widget.AppCompatTextView"
                }
            ) {
                view.setTextColor(textColor)
            }
            if (view is ImageView
                && view.javaClass.name.let {
                    it == "android.widget.ImageView"
                            || it == "androidx.appcompat.widget.AppCompatImageView"
                }
            ) {
                view.imageTintList = ColorStateList.valueOf(textColor)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyDialogTextColor(view.getChildAt(i), textColor)
                }
            }
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            //在每个add事务前增加一个remove事务，防止连续的add
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context) { block() }

    open fun observeLiveBus() {
    }
}