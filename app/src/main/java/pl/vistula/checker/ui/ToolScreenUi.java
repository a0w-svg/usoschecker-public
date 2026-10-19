package pl.vistula.checker.ui;

import android.view.View;
import android.view.Menu;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;

/** Shared flat headers and overflow actions for tools in More. */
final class ToolScreenUi {
    private ToolScreenUi() {}
    static void header(MainActivity activity, LinearLayout content, int title, java.util.function.Consumer<Menu> actions) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = UiHelper.text(activity, activity.getString(title), 20, UiHelper.INK, true);
        label.setPadding(UiHelper.dp(activity, 8), 0, 0, 0);
        androidx.core.view.ViewCompat.setAccessibilityHeading(label, true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.setMinimumHeight(UiHelper.dp(activity, 64));
        if (actions != null) {
            Button more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more, () -> {});
            more.setId(R.id.tool_screen_more);
            more.setContentDescription(activity.getString(R.string.tool_options, activity.getString(title)));
            row.addView(more, new LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2));
            more.setOnClickListener(v -> { PopupMenu popup = new PopupMenu(activity, more); actions.accept(popup.getMenu()); popup.show(); });
        }
        content.addView(row);
    }
    static void action(Menu menu, int label, Runnable action) {
        menu.add(label).setOnMenuItemClickListener(item -> { action.run(); return true; });
    }
    static View overflow(MainActivity activity, LinearLayout parent, String description, java.util.function.Consumer<Menu> actions) {
        Button more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more, () -> {});
        more.setContentDescription(description);
        parent.addView(more, new LinearLayout.LayoutParams(UiHelper.dp(activity, 48), -2));
        more.setOnClickListener(v -> { PopupMenu popup = new PopupMenu(activity, more); actions.accept(popup.getMenu()); popup.show(); });
        return more;
    }
}
