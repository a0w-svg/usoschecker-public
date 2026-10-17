package pl.vistula.checker.ui;

import android.widget.LinearLayout;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;

/**
 * Controller for rendering the Student Tools Hub screen (Więcej).
 */
public final class MoreScreenController {

    private MoreScreenController() {}

    public static void render(MainActivity activity, LinearLayout content) {
        UiHelper.title(activity, content,
            activity.getString(R.string.tools_title),
            activity.getString(R.string.tools_subtitle));

        UiHelper.heading(activity, content, activity.getString(R.string.section_studies));
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_card, activity.getString(R.string.cards_title), activity.getString(R.string.cards_description), () -> activity.navigate(13));
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_documents, activity.getString(R.string.documents_title), activity.getString(R.string.documents_description), () -> activity.navigate(14));
        UiHelper.toolTile(activity, content, "🎓",
            activity.getString(R.string.tool_gpa_title),
            activity.getString(R.string.tool_gpa_desc),
            () -> activity.navigate(9));

        UiHelper.toolTile(activity, content, "📊",
            activity.getString(R.string.tool_attendance_title),
            activity.getString(R.string.tool_attendance_desc),
            () -> activity.navigate(12));

        UiHelper.toolTile(activity, content, "✅",
            activity.getString(R.string.tool_tasks_title),
            activity.getString(R.string.tool_tasks_desc),
            () -> activity.navigate(7));

        UiHelper.heading(activity, content, activity.getString(R.string.section_university));
        UiHelper.toolTile(activity, content, "💳",
            activity.getString(R.string.tool_payments_title),
            activity.getString(R.string.tool_payments_desc),
            () -> activity.navigate(8));

        UiHelper.toolTile(activity, content, "📢",
            activity.getString(R.string.tool_news_title),
            activity.getString(R.string.tool_news_desc),
            () -> activity.navigate(11));

        UiHelper.toolTile(activity, content, "🏛️",
            activity.getString(R.string.tool_campus_title),
            activity.getString(R.string.tool_campus_desc),
            () -> activity.navigate(10));

        UiHelper.heading(activity, content, activity.getString(R.string.section_app));
        UiHelper.toolTile(activity, content, "◐", activity.getString(R.string.appearance_title),
            activity.getString(R.string.appearance_description), () -> Appearance.choose(activity));
        UiHelper.toolTile(activity, content, "👤",
            activity.getString(R.string.tool_account_title),
            activity.getString(R.string.tool_account_desc),
            () -> activity.navigate(4));

        UiHelper.toolTile(activity, content, "⚡",
            activity.getString(R.string.tool_api_title),
            activity.getString(R.string.tool_api_desc),
            () -> activity.navigate(3));

        UiHelper.note(activity, content, activity.getString(R.string.tools_privacy_note), UiHelper.MUTED);
        AppInfo.render(activity, content);
    }
}
