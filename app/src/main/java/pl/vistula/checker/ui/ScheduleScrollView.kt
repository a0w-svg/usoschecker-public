package pl.vistula.checker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView
import java.util.function.IntConsumer
import kotlin.math.abs

/** Resolves the gesture direction before either scrolling or pull-to-refresh can claim it. */
class ScheduleScrollView : ScrollView {
    private var onSwipe: IntConsumer? = null
    private val slop: Int
    private var downX = 0f
    private var downY = 0f
    private var axis = 0 // 0 undecided, 1 horizontal, 2 vertical

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        slop = ViewConfiguration.get(context).scaledTouchSlop
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        slop = ViewConfiguration.get(context).scaledTouchSlop
    }

    fun setScheduleNavigation(action: IntConsumer?) {
        onSwipe = action
        // SwipeRefreshLayout honors interception requests from a nested-scrolling target.
        isNestedScrollingEnabled = action != null
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (onSwipe == null) return super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
            axis = 0
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            axis = 2
            parent?.requestDisallowInterceptTouchEvent(false)
        } else if (action == MotionEvent.ACTION_MOVE && axis == 0) {
            val dx = abs(event.x - downX)
            val dy = abs(event.y - downY)
            if (dx > slop && dx > dy * 1.25f) {
                axis = 1
            } else if (dy > slop && dy >= dx) {
                axis = 2
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            axis = 0
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return (onSwipe != null && axis == 1) || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipe = onSwipe
        if (swipe != null && axis == 1) {
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.onTouchEvent(cancel)
                cancel.recycle()
            }
            if (action == MotionEvent.ACTION_UP) {
                val dx = event.x - downX
                val dy = abs(event.y - downY)
                if (abs(dx) >= UiHelper.dp(context, 48) && abs(dx) > dy) {
                    swipe.accept(if (dx < 0) 1 else -1)
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}

