package pl.vistula.checker.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Horizontal swipes belong to navigation; taps retain the standard accessible behavior. */
public final class SwipeNavigationView extends BottomNavigationView {
    private java.util.function.IntConsumer onSwipe;
    private final int slop;
    private float downX, downY;
    private int axis;

    public SwipeNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }
    public void setOnTabSwipe(java.util.function.IntConsumer action) { onSwipe = action; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); axis = 0; break;
            case MotionEvent.ACTION_POINTER_DOWN:
                axis = 2; break;
            case MotionEvent.ACTION_MOVE:
                if (axis == 0) {
                    float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
                    if (dx > slop && dx > dy * 1.3f) axis = 1;
                    else if (dy > slop) axis = 2;
                }
                break;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) axis = 0;
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return (onSwipe != null && axis != 0) || super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (onSwipe != null && axis != 0) {
            if (axis == 1 && event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX;
                if (Math.abs(dx) >= UiHelper.dp(getContext(), 48) && Math.abs(dx) > Math.abs(event.getY() - downY))
                    onSwipe.accept(dx < 0 ? 1 : -1);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { return super.performClick(); }
}
