package pl.vistula.checker;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import pl.vistula.checker.core.LessonTime;

/** Scrollable day grid with separate lanes for overlapping classes. */
final class DayTimelineView extends FrameLayout {
    private final LocalDate date;
    private final List<Item> items = new ArrayList<>();
    private final int firstHour, lastHour;
    private static final int HOUR_HEIGHT = 72, GUTTER = 48;
    private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Item {
        LessonTime lesson; int start, end, column, columns = 1; TextView view;
    }
    public DayTimelineView(Context context) { this(context, LocalDate.now(LessonTime.ZONE), Collections.emptyList(), lesson -> {}); }
    DayTimelineView(Context context, LocalDate date, List<LessonTime> lessons, Consumer<LessonTime> click) {
        super(context); this.date = date;
        int first = 8, last = 20;
        for (LessonTime lesson : lessons) {
            Item item = new Item(); item.lesson = lesson;
            item.start = lesson.start.toLocalDate().isBefore(date) ? 0 : lesson.start.getHour() * 60 + lesson.start.getMinute();
            item.end = lesson.end.toLocalDate().isAfter(date) ? 1440 : lesson.end.getHour() * 60 + lesson.end.getMinute();
            first = Math.min(first, item.start / 60); last = Math.max(last, (item.end + 59) / 60);
            items.add(item);
        }
        firstHour = first; lastHour = Math.min(24, Math.max(first + 1, last));
        setMinimumHeight(dp((lastHour - firstHour) * HOUR_HEIGHT + 24));
        setLayoutParams(new LinearLayout.LayoutParams(-1, dp((lastHour - firstHour) * HOUR_HEIGHT + 24)));
        addView(new Lines(context, false), new LayoutParams(-1, -1));
        items.sort(Comparator.comparingInt(i -> i.start));
        List<Item> group = new ArrayList<>(); int groupEnd = -1;
        for (Item item : items) {
            if (item.start >= groupEnd && !group.isEmpty()) { finishGroup(group); group.clear(); }
            Set<Integer> occupied = new HashSet<>();
            for (Item previous : group) if (previous.end > item.start) occupied.add(previous.column);
            while (occupied.contains(item.column)) item.column++;
            group.add(item); groupEnd = Math.max(groupEnd, item.end);
            TextView block = new TextView(context); item.view = block;
            boolean current = item.lesson.current(Instant.now()) && !item.lesson.tentative();
            block.setTextColor(Color.rgb(20, 64, 49)); block.setTextSize(12); block.setPadding(dp(8), dp(4), dp(6), dp(4));
            block.setGravity(Gravity.TOP); block.setEllipsize(android.text.TextUtils.TruncateAt.END);
            String hours = item.lesson.start.format(CLOCK) + " – " + item.lesson.end.format(CLOCK);
            String room = "";
            for (String line : item.lesson.record.value.split("\n")) if (line.startsWith("Sala ")) room = line;
            String label = (item.lesson.tentative() ? "Orientacyjne · " : "") + item.lesson.record.title;
            int height = dp((item.end - item.start) * HOUR_HEIGHT / 60f);
            if (height >= dp(42)) label += "\n" + hours;
            if (height >= dp(72) && !room.isEmpty()) label += "\n" + room;
            block.setText(label); block.setMaxLines(height >= dp(72) ? 4 : height >= dp(42) ? 2 : 1);
            block.setContentDescription((current ? "Teraz. " : "") + item.lesson.record.title + ". " + item.lesson.record.value);
            GradientDrawable background = new GradientDrawable(); background.setColor(current ? Color.rgb(190, 232, 211) : item.lesson.tentative() ? Color.rgb(251, 240, 217) : Color.rgb(232, 243, 236));
            background.setCornerRadius(dp(10)); background.setStroke(dp(current ? 2 : 1), current ? Color.rgb(23, 107, 87) : item.lesson.tentative() ? Color.rgb(183, 141, 59) : Color.rgb(186, 210, 195));
            block.setBackground(background); block.setClickable(true); block.setFocusable(true);
            block.setOnClickListener(v -> click.accept(item.lesson)); addView(block);
        }
        finishGroup(group);
        addView(new Lines(context, true), new LayoutParams(-1, -1));
    }
    private void finishGroup(List<Item> group) {
        int columns = 1; for (Item item : group) columns = Math.max(columns, item.column + 1);
        for (Item item : group) item.columns = columns;
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        for (Item item : items) {
            int width = Math.max(dp(24), (w - dp(GUTTER + 8)) / item.columns);
            LayoutParams p = new LayoutParams(Math.max(dp(20), width - dp(4)), Math.max(dp(24), dp((item.end - item.start) * HOUR_HEIGHT / 60f) - dp(2)));
            p.leftMargin = dp(GUTTER) + item.column * width; p.topMargin = dp((item.start - firstHour * 60) * HOUR_HEIGHT / 60f + 8);
            item.view.setLayoutParams(p);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
    int nowOffset() { return dp((LocalTime.now(LessonTime.ZONE).getHour() * 60 + LocalTime.now(LessonTime.ZONE).getMinute() - firstHour * 60) * HOUR_HEIGHT / 60f + 8); }
    private final class Lines extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean nowOnly;
        private final String[] hourLabels = new String[lastHour - firstHour + 1];
        Lines(Context context, boolean nowOnly) { super(context); this.nowOnly = nowOnly; for (int i = 0; i < hourLabels.length; i++) hourLabels[i] = String.format(java.util.Locale.ROOT, "%02d:00", firstHour + i); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            if (!nowOnly) {
                paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
                for (int hour = firstHour; hour <= lastHour; hour++) {
                    float y = dp((hour - firstHour) * HOUR_HEIGHT + 8);
                    paint.setColor(Color.rgb(104, 119, 110)); canvas.drawText(hourLabels[hour - firstHour], dp(1), y + dp(4), paint);
                    paint.setColor(Color.rgb(225, 232, 226)); paint.setStrokeWidth(dp(1)); canvas.drawLine(dp(GUTTER), y, getWidth(), y, paint);
                }
            } else if (date.equals(LocalDate.now(LessonTime.ZONE))) {
                float y = nowOffset();
                if (y >= 0 && y <= getHeight()) {
                    paint.setColor(Color.rgb(192, 49, 49)); paint.setStrokeWidth(dp(2));
                    canvas.drawCircle(dp(GUTTER), y, dp(4), paint); canvas.drawLine(dp(GUTTER), y, getWidth(), y, paint);
                }
            }
        }
    }
}
