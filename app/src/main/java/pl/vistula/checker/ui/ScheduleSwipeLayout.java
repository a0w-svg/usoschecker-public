package pl.vistula.checker.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/** Intercepts deliberate horizontal gestures while preserving vertical scrolling and card taps. */
public final class ScheduleSwipeLayout extends LinearLayout {
    private final java.util.function.IntConsumer onSwipe;
    private final int slop;
    private float downX, downY;
    private boolean dragging;

    public ScheduleSwipeLayout(Context context, java.util.function.IntConsumer onSwipe) {
        super(context);
        this.onSwipe = onSwipe;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); dragging = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > slop && dx > dy * 2) {
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); dragging = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(event.getX() - downX) > slop && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY) * 2) {
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX, dy = Math.abs(event.getY() - downY);
            boolean change = dragging && Math.abs(dx) >= UiHelper.dp(getContext(), 64) && Math.abs(dx) > dy * 2;
            dragging = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (change) onSwipe.accept(dx < 0 ? 1 : -1);
            else performClick();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() { return super.performClick(); }
}
