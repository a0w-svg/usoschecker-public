package pl.vistula.checker.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Locale;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.PaymentInfo;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the Payments, Subaccount and Installments screen.
 */
public final class PaymentsScreenController {

    private PaymentsScreenController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              JSONObject state, boolean connected, Runnable onRefresh) throws JSONException {
        UiHelper.title(activity, content,
            activity.getString(R.string.payments_title),
            activity.getString(R.string.payments_subtitle));

        UiHelper.note(activity, content,
            activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(d.optLong("successAt"))),
            UiHelper.MUTED);

        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            UiHelper.empty(activity, content,
                activity.getString(R.string.payments_no_data_title),
                activity.getString(R.string.payments_no_data_desc));
            content.addView(UiHelper.button(activity,
                connected ? activity.getString(R.string.btn_refresh) : activity.getString(R.string.btn_connect),
                () -> { if (connected) onRefresh.run(); else activity.navigate(4); }, true));
            return;
        }

        Snapshot snapshot = Snapshot.from(raw);
        PaymentInfo payments = snapshot.payments;

        String authorizedScopes = state.optString("scopes", "");
        boolean hasPaymentsScope = Arrays.asList(authorizedScopes.split("\\|")).contains("payments");
        boolean scopeMissing = (payments != null && payments.scopeMissing)
            || (connected && !authorizedScopes.isEmpty() && !hasPaymentsScope);

        if (scopeMissing) {
            LinearLayout warnBox = UiHelper.card(activity, content, Color.rgb(255, 248, 230));
            UiHelper.add(activity, warnBox, activity.getString(R.string.payments_missing_scope_badge), 11, Color.rgb(180, 110, 0), true);
            UiHelper.add(activity, warnBox, activity.getString(R.string.payments_missing_scope_title), 17, UiHelper.INK, true);
            UiHelper.note(activity, warnBox,
                activity.getString(R.string.payments_missing_scope_desc, authorizedScopes.isEmpty() ? "brak" : authorizedScopes),
                UiHelper.INK);
            UiHelper.note(activity, warnBox, activity.getString(R.string.payments_missing_scope_action_note), UiHelper.MUTED);
            warnBox.addView(UiHelper.button(activity, activity.getString(R.string.payments_missing_scope_btn), () -> activity.navigate(4), true));
        }

        if (payments == null || (payments.accounts.isEmpty() && payments.installments.isEmpty())) {
            if (!scopeMissing) {
                UiHelper.empty(activity, content,
                    activity.getString(R.string.payments_empty_title),
                    activity.getString(R.string.payments_empty_desc));
                content.addView(UiHelper.button(activity, activity.getString(R.string.payments_refresh_btn), onRefresh, false));
            }
            return;
        }

        if (!payments.accounts.isEmpty()) {
            UiHelper.heading(activity, content, activity.getString(R.string.payments_accounts_heading));
            for (PaymentInfo.Account acc : payments.accounts) {
                LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
                UiHelper.add(activity, box, acc.name.isEmpty() ? activity.getString(R.string.payments_account_default_name) : acc.name, 18, UiHelper.INK, true);
                if (!acc.faculty.isEmpty()) {
                    UiHelper.add(activity, box, activity.getString(R.string.payments_faculty, acc.faculty), 13, UiHelper.MUTED, false);
                }
                if (!acc.accountOwner.isEmpty()) {
                    UiHelper.add(activity, box, activity.getString(R.string.payments_owner, acc.accountOwner), 13, UiHelper.MUTED, false);
                }
                UiHelper.add(activity, box, formatIban(acc.bankAccountNumber), 16, UiHelper.GREEN, true);
                if (!acc.swift.isEmpty()) {
                    UiHelper.add(activity, box, activity.getString(R.string.payments_swift, acc.swift), 12, UiHelper.MUTED, false);
                }
                if (acc.balance != 0) {
                    UiHelper.add(activity, box, activity.getString(R.string.payments_balance, acc.balance, acc.currency), 13, UiHelper.MUTED, false);
                }
                box.addView(UiHelper.button(activity, activity.getString(R.string.payments_copy_iban), () -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(MainActivity.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(UiHelper.privateClip("Subkonto USOS", acc.bankAccountNumber.replaceAll("\\s+", "")));
                        Toast.makeText(activity, activity.getString(R.string.payments_iban_copied), Toast.LENGTH_SHORT).show();
                    }
                }, false));
            }
        }

        if (!payments.installments.isEmpty()) {
            UiHelper.heading(activity, content, activity.getString(R.string.payments_schedule_heading));
            double totalUnpaid = payments.totalUnpaidAmount();
            LinearLayout summaryBox = UiHelper.card(activity, content, payments.hasOverdue() ? Color.rgb(255, 240, 240) : Color.rgb(238, 245, 242));
            UiHelper.add(activity, summaryBox, activity.getString(R.string.payments_status_settlement), 11, payments.hasOverdue() ? Color.rgb(160, 48, 40) : UiHelper.GREEN, true);
            UiHelper.add(activity, summaryBox, activity.getString(R.string.payments_total_due, totalUnpaid), 22, UiHelper.INK, true);
            UiHelper.note(activity, summaryBox,
                payments.hasOverdue() ? activity.getString(R.string.payments_status_overdue_warn) : (totalUnpaid == 0 ? activity.getString(R.string.payments_status_all_paid) : activity.getString(R.string.payments_status_pending)),
                payments.hasOverdue() ? Color.rgb(160, 48, 40) : UiHelper.MUTED);

            LayoutInflater inflater = LayoutInflater.from(activity);
            for (PaymentInfo.Installment inst : payments.installments) {
                View instCard = inflater.inflate(R.layout.item_payment_card, content, false);
                TextView title = instCard.findViewById(R.id.payment_item_title);
                TextView status = instCard.findViewById(R.id.payment_item_status);
                TextView amount = instCard.findViewById(R.id.payment_item_amount);
                TextView deadline = instCard.findViewById(R.id.payment_item_deadline);
                TextView paidDate = instCard.findViewById(R.id.payment_item_paid_date);
                TextView account = instCard.findViewById(R.id.payment_item_account);
                MaterialButton btnCopy = instCard.findViewById(R.id.payment_item_btn_copy);

                title.setText(inst.name);
                boolean paid = inst.isPaid();
                boolean overdue = inst.isOverdue();
                String statusLabel = paid ? activity.getString(R.string.payment_status_paid) : (overdue ? activity.getString(R.string.payment_status_overdue) : (inst.status.isEmpty() ? activity.getString(R.string.payment_status_to_pay) : inst.status));
                int statusColor = paid ? UiHelper.GREEN : (overdue ? Color.rgb(160, 48, 40) : Color.rgb(180, 110, 0));

                status.setText(statusLabel);
                status.setTextColor(statusColor);
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(paid ? Color.rgb(232, 243, 236) : (overdue ? Color.rgb(255, 235, 235) : Color.rgb(255, 248, 230)));
                badgeBg.setCornerRadius(UiHelper.dp(activity, 8));
                status.setBackground(badgeBg);

                amount.setText(activity.getString(R.string.payments_amount, inst.amount, inst.currency));

                if (!inst.deadline.isEmpty()) {
                    deadline.setVisibility(View.VISIBLE);
                    deadline.setText(activity.getString(R.string.payments_deadline, HistoryScreenController.formatFullDate(inst.deadline)));
                    if (overdue) deadline.setTextColor(Color.rgb(160, 48, 40));
                }

                if (!inst.paymentDate.isEmpty()) {
                    paidDate.setVisibility(View.VISIBLE);
                    paidDate.setText(activity.getString(R.string.payments_date_paid, HistoryScreenController.formatFullDate(inst.paymentDate)));
                }

                if (!inst.bankAccountNumber.isEmpty()) {
                    account.setVisibility(View.VISIBLE);
                    account.setText(activity.getString(R.string.payments_account_for_installment, formatIban(inst.bankAccountNumber)));
                    btnCopy.setVisibility(View.VISIBLE);
                    Runnable copyAction = () -> {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(MainActivity.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.payment_clipboard_label), inst.bankAccountNumber.replaceAll("\\s+", "")));
                            Toast.makeText(activity, activity.getString(R.string.payments_account_copied), Toast.LENGTH_SHORT).show();
                        }
                    };
                    btnCopy.setOnClickListener(v -> copyAction.run());
                    instCard.setClickable(true);
                    instCard.setFocusable(true);
                    instCard.setOnClickListener(v -> copyAction.run());
                }

                content.addView(instCard);
            }
        }
        content.addView(UiHelper.button(activity, activity.getString(R.string.payments_refresh_btn), onRefresh, false));
    }

    public static String formatIban(String raw) {
        if (raw == null) return "";
        String clean = raw.replaceAll("\\s+", "");
        if (clean.length() < 10) return raw;
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        if (clean.length() > 2 && Character.isLetter(clean.charAt(0)) && Character.isLetter(clean.charAt(1))) {
            sb.append(clean, 0, 2);
            offset = 2;
            if (clean.length() >= 4) {
                sb.append(" ").append(clean, 2, 4);
                offset = 4;
            }
        } else if (clean.length() == 26) {
            sb.append(clean, 0, 2);
            offset = 2;
        }
        for (int i = offset; i < clean.length(); i += 4) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(clean.substring(i, Math.min(i + 4, clean.length())));
        }
        return sb.toString();
    }
}
