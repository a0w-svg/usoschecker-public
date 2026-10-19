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

        ToolScreenUi.header(activity, content, R.string.nav_more, null);
        UiHelper.note(activity, content, activity.getString(R.string.section_studies), UiHelper.MUTED);
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_card, activity.getString(R.string.cards_title), activity.getString(R.string.cards_description), () -> activity.navigate(13));
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_documents, activity.getString(R.string.documents_title), activity.getString(R.string.documents_description), () -> activity.navigate(14));
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_grades,
            activity.getString(R.string.tool_gpa_title),
            activity.getString(R.string.tool_gpa_desc),
            () -> activity.navigate(9));

        UiHelper.toolTile(activity, content, R.drawable.ic_nav_attendance,
            activity.getString(R.string.tool_attendance_title),
            activity.getString(R.string.tool_attendance_desc),
            () -> activity.navigate(12));

        UiHelper.toolTile(activity, content, R.drawable.ic_nav_tasks,
            activity.getString(R.string.tool_tasks_title),
            activity.getString(R.string.tool_tasks_desc),
            () -> activity.navigate(7));

        UiHelper.note(activity, content, activity.getString(R.string.section_university), UiHelper.MUTED);
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_card,
            activity.getString(R.string.tool_payments_title),
            activity.getString(R.string.tool_payments_desc),
            () -> activity.navigate(8));

        UiHelper.toolTile(activity, content, R.drawable.ic_nav_news,
            activity.getString(R.string.tool_news_title),
            activity.getString(R.string.tool_news_desc),
            () -> activity.navigate(11));

        UiHelper.toolTile(activity, content, R.drawable.ic_nav_home,
            activity.getString(R.string.tool_campus_title),
            activity.getString(R.string.tool_campus_desc),
            () -> activity.navigate(10));

        UiHelper.note(activity, content, activity.getString(R.string.section_app), UiHelper.MUTED);
        UiHelper.toolTile(activity, content, R.drawable.ic_nav_account,
            activity.getString(R.string.tool_account_title),
            activity.getString(R.string.tool_account_desc),
            () -> activity.navigate(4));

        UiHelper.toolTile(activity, content, R.drawable.ic_nav_api,
            activity.getString(R.string.tool_api_title),
            activity.getString(R.string.tool_api_desc),
            () -> activity.navigate(3));

        UiHelper.note(activity, content, activity.getString(R.string.tools_privacy_note), UiHelper.MUTED);
    }
}
