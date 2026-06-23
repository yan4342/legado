package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * ViewPager that can have swiping temporarily disabled.
 * Used to prevent tab switching when NavDisplay sub-pages are showing.
 */
class LockableViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    var isSwipeEnabled = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return isSwipeEnabled && super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return isSwipeEnabled && super.onTouchEvent(ev)
    }
}
