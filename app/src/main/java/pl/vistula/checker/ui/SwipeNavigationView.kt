package pl.vistula.checker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.function.IntConsumer
import kotlin.math.abs

/** Horizontal swipes belong to navigation; taps retain the standard accessible behavior. */
class SwipeNavigationView : BottomNavigationView {
    private var onSwipe: IntConsumer? = null
    private val slop: Int
    private var downX = 0f
    private var downY = 0f
    private var axis = 0

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        slop = ViewConfiguration.get(context).scaledTouchSlop
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        slop = ViewConfiguration.get(context).scaledTouchSlop
    }

    fun setOnTabSwipe(action: IntConsumer?) {
        onSwipe = action
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                axis = 0
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                axis = 2
            }
            MotionEvent.ACTION_MOVE -> {
                if (axis == 0) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > slop && dx > dy * 1.3f) {
                        axis = 1
                    } else if (dy > slop) {
                        axis = 2
                    }
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            axis = 0
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return (onSwipe != null && axis != 0) || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipe = onSwipe
        if (swipe != null && axis != 0) {
            if (axis == 1 && event.actionMasked == MotionEvent.ACTION_UP) {
                val dx = event.x - downX
                if (abs(dx) >= UiHelper.dp(context, 48) && abs(dx) > abs(event.y - downY)) {
                    swipe.accept(if (dx < 0) 1 else -1)
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}

