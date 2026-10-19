package pl.vistula.checker.core;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

public final class PaymentInfo {
    public static final class Account {
        public final String id;
        public final String name;
        public final String currency;
        public final String bankAccountNumber;
        public final double balance;
        public final String faculty;
        public final String accountOwner;
        public final String swift;

        public Account(String id, String name, String currency, String bankAccountNumber, double balance) {
            this(id, name, currency, bankAccountNumber, balance, "", "", "");
        }

        public Account(String id, String name, String currency, String bankAccountNumber, double balance,
                       String faculty, String accountOwner, String swift) {
            this.id = id != null ? id : "";
            this.name = name != null ? name : "";
            this.currency = (currency != null && !currency.isEmpty()) ? currency : "PLN";
            this.bankAccountNumber = bankAccountNumber != null ? bankAccountNumber : "";
            this.balance = balance;
            this.faculty = faculty != null ? faculty : "";
            this.accountOwner = accountOwner != null ? accountOwner : "";
            this.swift = swift != null ? swift : "";
        }

        public JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("currency", currency)
                .put("bankAccountNumber", bankAccountNumber).put("balance", balance)
                .put("faculty", faculty).put("accountOwner", accountOwner).put("swift", swift);
        }

        public static Account from(JSONObject o) {
            if (o == null) return new Account("", "", "PLN", "", 0.0);
            return new Account(o.optString("id", ""), o.optString("name", ""), o.optString("currency", "PLN"),
                o.optString("bankAccountNumber", ""), o.optDouble("balance", 0.0),
                o.optString("faculty", ""), o.optString("accountOwner", ""), o.optString("swift", ""));
        }
    }

    public static final class Installment {
        public final String id;
        public final String name;
        public final double amount;
        public final String currency;
        public final String deadline;
        public final String status;
        public final String paymentDate;
        public final String bankAccountNumber;
        public final String type;

        public Installment(String id, String name, double amount, String currency, String deadline, String status, String paymentDate) {
            this(id, name, amount, currency, deadline, status, paymentDate, "", "");
        }

        public Installment(String id, String name, double amount, String currency, String deadline, String status, String paymentDate,
                           String bankAccountNumber, String type) {
            this.id = id != null ? id : "";
            this.name = name != null ? name : "";
            this.amount = amount;
            this.currency = (currency != null && !currency.isEmpty()) ? currency : "PLN";
            this.deadline = deadline != null ? deadline : "";
            this.status = status != null ? status : "";
            this.paymentDate = paymentDate != null ? paymentDate : "";
            this.bankAccountNumber = bankAccountNumber != null ? bankAccountNumber : "";
            this.type = type != null ? type : "";
        }

        public JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("amount", amount)
                .put("currency", currency).put("deadline", deadline).put("status", status)
                .put("paymentDate", paymentDate).put("bankAccountNumber", bankAccountNumber)
                .put("type", type);
        }

        public static Installment from(JSONObject o) {
            if (o == null) return new Installment("", "", 0.0, "PLN", "", "", "");
            return new Installment(o.optString("id", ""), o.optString("name", ""), o.optDouble("amount", 0.0),
                o.optString("currency", "PLN"), o.optString("deadline", ""), o.optString("status", ""),
                o.optString("paymentDate", ""), o.optString("bankAccountNumber", ""), o.optString("type", ""));
        }

        public boolean isPaid() {
            return "paid".equalsIgnoreCase(status) || "opłacona".equalsIgnoreCase(status)
                || "oplacona".equalsIgnoreCase(status) || "settled".equalsIgnoreCase(status)
                || "rozliczona".equalsIgnoreCase(status) || "сплачено".equalsIgnoreCase(status);
        }

        public boolean isOverdue() {
            if ("overdue".equalsIgnoreCase(status) || "przeterminowana".equalsIgnoreCase(status)
                || "po terminie".equalsIgnoreCase(status) || "прострочено".equalsIgnoreCase(status)) return true;
            if (isPaid()) return false;
            if (deadline != null && !deadline.isEmpty()) {
                try {
                    String d = deadline.length() > 10 ? deadline.substring(0, 10) : deadline;
                    return LocalDate.parse(d).isBefore(LocalDate.now());
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    public final List<Account> accounts;
    public final List<Installment> installments;
    public final boolean scopeMissing;
    public final String statusNotice;

    public PaymentInfo(List<Account> accounts, List<Installment> installments) {
        this(accounts, installments, false, "");
    }

    public PaymentInfo(List<Account> accounts, List<Installment> installments, boolean scopeMissing, String statusNotice) {
        this.accounts = Collections.unmodifiableList(new ArrayList<>(accounts));
        this.installments = Collections.unmodifiableList(new ArrayList<>(installments));
        this.scopeMissing = scopeMissing;
        this.statusNotice = statusNotice != null ? statusNotice : "";
    }

    public double totalUnpaidAmount() {
        double sum = 0.0;
        for (Installment inst : installments) {
            if (!inst.isPaid() && inst.amount > 0) sum += inst.amount;
        }
        return sum;
    }

    public boolean hasOverdue() {
        for (Installment inst : installments) {
            if (inst.isOverdue()) return true;
        }
        return false;
    }

    public JSONObject json() throws JSONException {
        JSONArray accArr = new JSONArray();
        for (Account a : accounts) accArr.put(a.json());
        JSONArray instArr = new JSONArray();
        for (Installment i : installments) instArr.put(i.json());
        return new JSONObject().put("accounts", accArr).put("installments", instArr)
            .put("scopeMissing", scopeMissing).put("statusNotice", statusNotice);
    }

    public static PaymentInfo from(JSONObject o) {
        if (o == null) return new PaymentInfo(Collections.emptyList(), Collections.emptyList());
        List<Account> accList = new ArrayList<>();
        JSONArray accArr = o.optJSONArray("accounts");
        if (accArr != null) {
            for (int i = 0; i < accArr.length(); i++) {
                accList.add(Account.from(accArr.optJSONObject(i)));
            }
        }
        List<Installment> instList = new ArrayList<>();
        JSONArray instArr = o.optJSONArray("installments");
        if (instArr != null) {
            for (int i = 0; i < instArr.length(); i++) {
                instList.add(Installment.from(instArr.optJSONObject(i)));
            }
        }
        boolean scopeMissing = o.optBoolean("scopeMissing", false);
        String statusNotice = o.optString("statusNotice", "");
        return new PaymentInfo(accList, instList, scopeMissing, statusNotice);
    }
}
