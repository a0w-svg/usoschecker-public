package pl.vistula.checker.ui

import android.content.Context
import android.annotation.SuppressLint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarMenuView
import java.util.function.IntConsumer
import kotlin.math.abs

/** Horizontal swipes belong to navigation; taps retain the standard accessible behavior. */
class SwipeNavigationView : BottomNavigationView {
    private var onSwipe: IntConsumer? = null
    private var tabScrolling = false
    private val slop: Int
    private var downX = 0f
    private var downY = 0f
    private var axis = 0
    private var childTouchCancelled = false

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

    fun setTabScrolling(scrolling: Boolean) { tabScrolling = scrolling }

    fun requiredItemWidth(): Int {
        var textWidth = 0
        fun measureLabel(view: View) {
            if (view is android.widget.TextView && view.text.isNotEmpty()) {
                textWidth = maxOf(textWidth, kotlin.math.ceil(view.paint.measureText(view.text.toString())).toInt() +
                    view.compoundPaddingLeft + view.compoundPaddingRight)
            }
            if (view is android.view.ViewGroup) repeat(view.childCount) { measureLabel(view.getChildAt(it)) }
        }
        measureLabel(this)
        return textWidth + UiHelper.dp(context, 24)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        (background?.mutate() as? android.graphics.drawable.GradientDrawable)?.cornerRadius = h / 2f
    }

    @SuppressLint("RestrictedApi")
    override fun createNavigationBarMenuView(context: Context): NavigationBarMenuView = PillMenuView(context)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // HorizontalScrollView offers unspecified width. Pass the accessibility
        // minimum through to Material's menu before it divides space among tabs.
        val widthSpec = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED && minimumWidth > 0) {
            MeasureSpec.makeMeasureSpec(minimumWidth, MeasureSpec.EXACTLY)
        } else widthMeasureSpec
        super.onMeasure(widthSpec, heightMeasureSpec)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // A scrollable menu owns horizontal movement; otherwise the swipe changes pages.
        parent.requestDisallowInterceptTouchEvent(!tabScrolling)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                axis = 0
                childTouchCancelled = false
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
        val swipe = if (tabScrolling) null else onSwipe
        if (swipe != null && axis != 0) {
            // Cancel the pressed Material item once, then own the entire gesture.
            // Changing screens on UP must not let the old item receive that same UP.
            if (!childTouchCancelled) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                childTouchCancelled = true
            }
            if (axis == 1 && event.actionMasked == MotionEvent.ACTION_UP) {
                val dx = event.x - downX
                if (abs(dx) >= UiHelper.dp(context, 48) && abs(dx) > abs(event.y - downY)) {
                    swipe.accept(if (dx < 0) 1 else -1)
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) axis = 0
            return true
        }
        // Ordinary taps retain Material's click and accessibility behavior.
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) axis = 0
        return handled
    }
}

/** Material's horizontal menu uses only 80% of the bar for five items. Text-only pills need
 * equal full-width slots so labels, hit areas and indicators have the same centre.
 * This small adapter is covered by label/indicator bounds tests against the pinned Material version.
 */
@SuppressLint("RestrictedApi")
private class PillMenuView(context: Context) : BottomNavigationMenuView(context) {
    private val textPosition = IntArray(2)
    private val pillPosition = IntArray(2)
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        repeat(childCount) { index ->
            val item = getChildAt(index) as? android.view.ViewGroup ?: return@repeat
            val indicator = item.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_active_indicator_view) ?: return@repeat
            val inner = item.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_inner_content_container) ?: return@repeat
            fun visibleLabel(view: View): android.widget.TextView? {
                if (view is android.widget.TextView && view.isShown && view.text.isNotEmpty()) return view
                if (view is android.view.ViewGroup) repeat(view.childCount) { visibleLabel(view.getChildAt(it))?.let { label -> return label } }
                return null
            }
            val label = visibleLabel(inner) ?: return@repeat
            // Active and inactive text use the same 12 sp style. Material's cached
            // scale factors can outlive a font-scale change and clip inactive text.
            label.scaleX = 1f
            label.scaleY = 1f
            val layout = label.layout ?: return@repeat
            label.getLocationInWindow(textPosition)
            indicator.getLocationInWindow(pillPosition)
            val textCentre = textPosition[0] + label.compoundPaddingLeft + layout.getLineLeft(0) + layout.getLineWidth(0) / 2
            // Inactive indicators are scaled by Material. Their window position includes
            // that transform, so use their displayed width to keep the centre stable.
            val delta = pillPosition[0] + indicator.width * indicator.scaleX / 2f - textCentre
            // Material's minimum indicator width adds space after a short iconless label.
            // Centre the actual text in that space without changing its size or hit target.
            if (abs(delta) > .5f) inner.translationX += delta
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (visible.isEmpty()) { setMeasuredDimension(width, 0); return }
        val heightSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) heightMeasureSpec
            else MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)
        var height = 0
        visible.forEachIndexed { index, child ->
            fun centreLabels(view: View) {
                if (view is android.widget.TextView) {
                    if (view.gravity != android.view.Gravity.CENTER) view.gravity = android.view.Gravity.CENTER
                    view.scaleX = 1f
                    view.scaleY = 1f
                    val size = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
                    if (abs(view.textSize - size) > .1f) view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size)
                }
                if (view is android.view.ViewGroup) repeat(view.childCount) { centreLabels(view.getChildAt(it)) }
            }
            centreLabels(child)
            val childWidth = width / visible.size + if (index < width % visible.size) 1 else 0
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), heightSpec)
            child.layoutParams.width = childWidth
            height = maxOf(height, child.measuredHeight)
        }
        setMeasuredDimension(width, height)
    }
}

