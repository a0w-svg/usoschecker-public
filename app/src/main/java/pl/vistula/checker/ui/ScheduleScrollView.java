package pl.vistula.checker.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Resolves the gesture direction before either scrolling or pull-to-refresh can claim it. */
public final class ScheduleScrollView extends ScrollView {
    private java.util.function.IntConsumer onSwipe;
    private final int slop;
    private float downX, downY;
    private int axis; // 0 undecided, 1 horizontal, 2 vertical

    public ScheduleScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setScheduleNavigation(java.util.function.IntConsumer action) {
        onSwipe = action;
        // SwipeRefreshLayout honors interception requests from a nested-scrolling target.
        setNestedScrollingEnabled(action != null);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (onSwipe == null) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); axis = 0;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            axis = 2;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        } else if (action == MotionEvent.ACTION_MOVE && axis == 0) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > slop && dx > dy * 1.25f) axis = 1;
            else if (dy > slop && dy >= dx) {
                axis = 2;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            axis = 0;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return (onSwipe != null && axis == 1) || super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (onSwipe != null && axis == 1) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel);
                cancel.recycle();
            }
            if (action == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX, dy = Math.abs(event.getY() - downY);
                if (Math.abs(dx) >= UiHelper.dp(getContext(), 48) && Math.abs(dx) > dy) onSwipe.accept(dx < 0 ? 1 : -1);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() { return super.performClick(); }
}
