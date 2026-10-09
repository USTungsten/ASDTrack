package com.ellesjourney.tracker;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int IMPORT_BACKUP_REQUEST = 4102;
    private final DateTimeFormatter friendlyDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private TrackerStore store;
    private SecureSettings secureSettings;
    private FrameLayout content;
    private LinearLayout nav;
    private String activeTab = "Today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.CREAM);
        getWindow().setNavigationBarColor(Ui.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = new TrackerStore(this);
        secureSettings = new SecureSettings(this);
        if (store.hasProfile()) showApp("Today"); else showSetup();
    }

    private void showSetup() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.CREAM);
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 34), Ui.dp(this, 24), Ui.dp(this, 30));
        scroll.addView(page);

        TextView mark = Ui.text(this, "🌱", 34, Ui.PURPLE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.background(Ui.LAVENDER, 20, this));
        page.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        page.addView(Ui.space(this, 18));
        page.addView(Ui.title(this, "Elle’s Journey"));
        TextView intro = Ui.text(this,
                "A private, simple 12-week tracker for daily observations during a leucovorin trial.", 16, Ui.MUTED);
        Ui.setMargins(intro, 0, 6, 0, 24);
        page.addView(intro);

        EditText child = Ui.input(this, "Child’s name");
        child.setText("Elle");
        page.addView(Ui.heading(this, "Child’s name"));
        page.addView(child);

        EditText dose = Ui.input(this, "Example: 5 mg morning and evening");
        page.addView(Ui.heading(this, "Prescribed dose (optional)"));
        page.addView(dose);

        TextView startLabel = Ui.heading(this, "Trial start date");
        page.addView(startLabel);
        Button startDate = Ui.secondaryButton(this, friendlyDate.format(LocalDate.now()));
        final LocalDate[] selectedStart = {LocalDate.now()};
        startDate.setOnClickListener(view -> pickDate(selectedStart[0], date -> {
            selectedStart[0] = date;
            startDate.setText(friendlyDate.format(date));
        }));
        page.addView(startDate);

        LinearLayout notice = Ui.card(this);
        notice.setBackground(Ui.background(Ui.MINT, 18, this));
        notice.addView(Ui.heading(this, "🔒 Private by design"));
        notice.addView(Ui.text(this,
                "Entries stay on this phone. Nothing is uploaded. You choose when to share a PDF or backup.",
                14, Ui.GREEN));
        page.addView(notice);

        Button begin = Ui.primaryButton(this, "Begin 12-week tracker");
        begin.setOnClickListener(view -> {
            TrackerStore.Profile profile = new TrackerStore.Profile();
            profile.childName = valueOr(child, "Elle");
            profile.startDate = selectedStart[0].toString();
            profile.medication = "Leucovorin";
            profile.defaultDose = dose.getText().toString().trim();
            profile.doctorName = "";
            store.saveProfile(profile);
            showApp("Today");
        });
        page.addView(begin);
        TextView disclaimer = Ui.text(this,
                "This app records caregiver observations. It does not provide medical advice or dosing guidance.",
                12, Ui.MUTED);
        disclaimer.setGravity(Gravity.CENTER);
        page.addView(disclaimer);
        setContentView(scroll);
    }

    private void showApp(String tab) {
        activeTab = tab;
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.CREAM);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        nav = Ui.horizontal(this);
        nav.setPadding(Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4));
        nav.setBackgroundColor(Ui.WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 72)));
        setContentView(root);
        renderTab(tab);
    }

    private void renderTab(String tab) {
        activeTab = tab;
        content.removeAllViews();
        if ("History".equals(tab)) content.addView(historyScreen());
        else if ("Reports".equals(tab)) content.addView(reportsScreen());
        else if ("Settings".equals(tab)) content.addView(settingsScreen());
        else content.addView(todayScreen());
        renderNav();
    }

    private void renderNav() {
        nav.removeAllViews();
        addNavItem("⌂", "Today");
        addNavItem("▦", "History");
        addNavItem("▥", "Reports");
        addNavItem("⚙", "Settings");
    }

    private void addNavItem(String icon, String label) {
        TextView item = Ui.text(this, icon + "\n" + label, 13,
                label.equals(activeTab) ? Ui.PURPLE : Ui.MUTED);
        item.setGravity(Gravity.CENTER);
        item.setTypeface(Typeface.DEFAULT, label.equals(activeTab) ? Typeface.BOLD : Typeface.NORMAL);
        item.setOnClickListener(view -> renderTab(label));
        nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private ScrollView scrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.CREAM);
        return scroll;
    }

    private LinearLayout page(ScrollView scroll) {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(page);
        return page;
    }

    private void addHeader(LinearLayout page, String title, String subtitle, String icon) {
        LinearLayout row = Ui.horizontal(this);
        LinearLayout words = Ui.vertical(this);
        words.addView(Ui.title(this, title));
        words.addView(Ui.text(this, subtitle, 15, Ui.MUTED));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = Ui.text(this, icon, 25, Ui.PURPLE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.background(Ui.LAVENDER, 17, this));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));
        Ui.setMargins(row, 0, 0, 0, 20);
        page.addView(row);
    }

    private View todayScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        TrackerStore.Profile profile = store.getProfile();
        LocalDate today = LocalDate.now();
        addHeader(page, "Hi, " + profile.childName + "’s family", friendlyDate.format(today), "🌱");

        LinearLayout progressCard = Ui.card(this);
        progressCard.setBackground(Ui.background(Ui.PURPLE, 22, this));
        LinearLayout top = Ui.horizontal(this);
        TextView week = Ui.heading(this, "Week " + store.currentWeek() + " of 12");
        week.setTextColor(Ui.WHITE);
        TextView percent = Ui.text(this, Math.round(store.currentDay() / 84f * 100) + "%", 16, Ui.WHITE);
        percent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(week, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(percent);
        progressCard.addView(top);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(store.currentDay());
        bar.setProgressTintList(ColorStateList.valueOf(Ui.WHITE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
        Ui.setMargins(bar, 0, 12, 0, 6);
        progressCard.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        TextView count = Ui.text(this, store.getEntries().size() + " daily check-ins completed", 13, 0xE6FFFFFF);
        progressCard.addView(count);
        page.addView(progressCard);

        TrackerStore.DailyEntry todayEntry = store.getEntry(today.toString());
        LinearLayout checkin = Ui.card(this);
        checkin.addView(Ui.heading(this, todayEntry == null ? "Today’s check-in" : "Today is logged ✓"));
        checkin.addView(Ui.text(this,
                todayEntry == null ? "About one minute • You can edit it later" : "Tap below to review or make a change",
                14, Ui.MUTED));
        Button log = Ui.primaryButton(this, todayEntry == null ? "+  Log today" : "Edit today’s check-in");
        log.setOnClickListener(view -> showEntryEditor(today));
        checkin.addView(log);
        if (todayEntry != null) {
            LinearLayout quick = Ui.horizontal(this);
            quick.addView(statusPill(todayEntry.doseTaken ? "✓ Dose taken" : "Dose not marked", Ui.MINT, Ui.GREEN),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            quick.addView(statusPill("☾ Sleep " + trimFloat(todayEntry.sleepHours) + "h", Ui.PEACH, 0xFFA65E24),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            checkin.addView(quick);
        }
        page.addView(checkin);

        page.addView(Ui.section(this, "Coming up"));
        LinearLayout report = Ui.card(this);
        report.addView(Ui.heading(this, store.currentDay() < 42 ? "Week 6 doctor report" : "Week 6 report is ready"));
        int remaining = Math.max(0, 42 - store.currentDay());
        report.addView(Ui.text(this, remaining > 0
                ? "Ready in " + remaining + " days. Includes trends, notes, dose history, and side effects."
                : "Create a PDF from the Reports tab to share with the prescriber.", 14, Ui.MUTED));
        page.addView(report);

        TextView privacy = Ui.text(this,
                "🔒 Everything stays on this phone unless you choose to share a report or backup.", 13, Ui.GREEN);
        privacy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        privacy.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        privacy.setBackground(Ui.background(Ui.MINT, 16, this));
        page.addView(privacy);
        return scroll;
    }

    private TextView statusPill(String label, int background, int color) {
        TextView view = Ui.text(this, label, 13, color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(Ui.background(background, 15, this));
        Ui.setMargins(view, 4, 10, 4, 0);
        return view;
    }

    private void showEntryEditor(LocalDate date) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        int dayNumber = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(store.getProfile().startDate), date) + 1);
        addHeader(page, "Daily check-in", friendlyDate.format(date) + " • Day " + dayNumber, "✓");

        TrackerStore.DailyEntry existing = store.getEntry(date.toString());
        TrackerStore.DailyEntry entry = existing == null ? new TrackerStore.DailyEntry() : existing;
        entry.date = date.toString();

        LinearLayout medicine = Ui.card(this);
        CheckBox doseTaken = checkbox("Medication taken", entry.doseTaken);
        EditText dose = Ui.input(this, "Dose taken, e.g. 5 mg twice daily");
        dose.setText(entry.dose.isEmpty() ? store.getProfile().defaultDose : entry.dose);
        medicine.addView(Ui.heading(this, "Medication"));
        medicine.addView(doseTaken);
        medicine.addView(dose);
        page.addView(medicine);

        RatingPicker communication = ratingCard(page, "Communication", "Compared with usual baseline", entry.communication);
        RatingPicker engagement = ratingCard(page, "Social engagement", "Connection and interaction", entry.engagement);
        RatingPicker focus = ratingCard(page, "Focus", "Attention and participation", entry.focus);
        RatingPicker mood = ratingCard(page, "Overall mood", "How the day felt overall", entry.mood);
        RatingPicker appetite = ratingCard(page, "Appetite", "Compared with usual", entry.appetite);

        LinearLayout sleepCard = Ui.card(this);
        TextView sleepValue = Ui.heading(this, "Sleep: " + trimFloat(entry.sleepHours) + " hours");
        sleepCard.addView(sleepValue);
        SeekBar sleep = new SeekBar(this);
        sleep.setMax(48);
        sleep.setProgress(Math.round(entry.sleepHours * 4));
        sleep.setProgressTintList(ColorStateList.valueOf(Ui.PURPLE));
        sleep.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sleepValue.setText("Sleep: " + trimFloat(progress / 4f) + " hours");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sleepCard.addView(sleep);
        page.addView(sleepCard);

        LinearLayout effects = Ui.card(this);
        effects.addView(Ui.heading(this, "Anything noticed today?"));
        effects.addView(Ui.text(this, "Select any possible side effects or changes", 13, Ui.MUTED));
        CheckBox sleepChange = checkbox("Sleep change", entry.sleepChange);
        CheckBox tummyUpset = checkbox("Tummy upset", entry.tummyUpset);
        CheckBox headache = checkbox("Headache", entry.headache);
        CheckBox irritability = checkbox("Irritability", entry.irritability);
        CheckBox other = checkbox("Other", entry.otherSideEffect);
        effects.addView(sleepChange); effects.addView(tummyUpset); effects.addView(headache);
        effects.addView(irritability); effects.addView(other);
        EditText note = Ui.input(this, "Optional note for the doctor");
        note.setSingleLine(false);
        note.setMinHeight(Ui.dp(this, 92));
        note.setGravity(Gravity.TOP);
        note.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        note.setText(entry.note);
        effects.addView(note);
        page.addView(effects);

        Button save = Ui.primaryButton(this, "Save check-in");
        save.setOnClickListener(view -> {
            entry.doseTaken = doseTaken.isChecked();
            entry.dose = dose.getText().toString().trim();
            entry.communication = communication.value;
            entry.engagement = engagement.value;
            entry.focus = focus.value;
            entry.mood = mood.value;
            entry.appetite = appetite.value;
            entry.sleepHours = sleep.getProgress() / 4f;
            entry.sleepChange = sleepChange.isChecked();
            entry.tummyUpset = tummyUpset.isChecked();
            entry.headache = headache.isChecked();
            entry.irritability = irritability.isChecked();
            entry.otherSideEffect = other.isChecked();
            entry.note = note.getText().toString().trim();
            store.saveEntry(entry);
            Toast.makeText(this, "Check-in saved", Toast.LENGTH_SHORT).show();
            nav.setVisibility(View.VISIBLE);
            renderTab("Today");
            syncInBackground(null, false);
        });
        page.addView(save);
        Button cancel = Ui.secondaryButton(this, "Cancel");
        cancel.setOnClickListener(view -> { nav.setVisibility(View.VISIBLE); renderTab(activeTab); });
        page.addView(cancel);
        content.addView(scroll);
    }

    private RatingPicker ratingCard(LinearLayout page, String title, String subtitle, int selected) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, title));
        card.addView(Ui.text(this, subtitle, 13, Ui.MUTED));
        RatingPicker picker = new RatingPicker(selected);
        card.addView(picker.row);
        page.addView(card);
        return picker;
    }

    private final class RatingPicker {
        final LinearLayout row = Ui.horizontal(MainActivity.this);
        final List<Button> buttons = new ArrayList<>();
        int value;

        RatingPicker(int selected) {
            value = selected;
            String[] labels = {"−−", "−", "•", "+", "++"};
            for (int index = 0; index < labels.length; index++) {
                Button button = new Button(MainActivity.this);
                button.setText(labels[index]);
                button.setTextSize(18);
                button.setAllCaps(false);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setPadding(0, 0, 0, 0);
                final int chosen = index + 1;
                button.setOnClickListener(view -> { value = chosen; refresh(); });
                buttons.add(button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 52), 1);
                params.setMargins(Ui.dp(MainActivity.this, 3), Ui.dp(MainActivity.this, 12),
                        Ui.dp(MainActivity.this, 3), 0);
                row.addView(button, params);
            }
            refresh();
        }

        void refresh() {
            for (int index = 0; index < buttons.size(); index++) {
                boolean selected = index + 1 == value;
                Button button = buttons.get(index);
                button.setTextColor(selected ? Ui.PURPLE : Ui.INK);
                button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                button.setBackground(Ui.background(selected ? Ui.LAVENDER : Ui.LIGHT, 15, MainActivity.this));
            }
        }
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(label);
        checkbox.setTextSize(15);
        checkbox.setTextColor(Ui.INK);
        checkbox.setChecked(checked);
        checkbox.setButtonTintList(ColorStateList.valueOf(Ui.PURPLE));
        checkbox.setPadding(Ui.dp(this, 2), Ui.dp(this, 7), 0, Ui.dp(this, 7));
        checkbox.setMinHeight(Ui.dp(this, 46));
        return checkbox;
    }

    private View historyScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, "History", "Every daily observation in one place", "▦");
        List<TrackerStore.DailyEntry> entries = store.getEntries();
        if (entries.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.heading(this, "No check-ins yet"));
            empty.addView(Ui.text(this, "Your saved days will appear here.", 14, Ui.MUTED));
            page.addView(empty);
        } else {
            for (int index = entries.size() - 1; index >= 0; index--) {
                TrackerStore.DailyEntry entry = entries.get(index);
                LinearLayout card = Ui.card(this);
                LocalDate date = LocalDate.parse(entry.date);
                card.addView(Ui.heading(this, friendlyDate.format(date)));
                float average = (entry.communication + entry.engagement + entry.focus + entry.mood + entry.appetite) / 5f;
                card.addView(Ui.text(this,
                        "Overall response " + trimFloat(average) + "/5  •  Sleep " + trimFloat(entry.sleepHours) + "h",
                        14, Ui.MUTED));
                card.addView(Ui.text(this,
                        entry.doseTaken ? "✓ Medication taken" + (entry.dose.isEmpty() ? "" : " • " + entry.dose)
                                : "Medication not marked taken",
                        14, entry.doseTaken ? Ui.GREEN : Ui.MUTED));
                if (entry.hasSideEffects()) card.addView(Ui.text(this, "Possible side effects noted", 14, 0xFF984762));
                if (!entry.note.isEmpty()) card.addView(Ui.text(this, "“" + entry.note + "”", 14, Ui.INK));
                card.setOnClickListener(view -> showEntryEditor(date));
                page.addView(card);
            }
        }
        return scroll;
    }

    private View reportsScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, "Doctor reports", "Clear summaries to share at visits", "▥");
        List<TrackerStore.DailyEntry> entries = store.getEntries();
        LinearLayout glance = Ui.card(this);
        glance.addView(Ui.heading(this, "At a glance"));
        if (entries.isEmpty()) {
            glance.addView(Ui.text(this, "Complete a daily check-in to begin building trends.", 14, Ui.MUTED));
        } else {
            int sideEffectDays = 0;
            float total = 0;
            for (TrackerStore.DailyEntry entry : entries) {
                total += (entry.communication + entry.engagement + entry.focus + entry.mood + entry.appetite) / 5f;
                if (entry.hasSideEffects()) sideEffectDays++;
            }
            glance.addView(Ui.text(this,
                    entries.size() + " check-ins • Average response " + trimFloat(total / entries.size()) + "/5 • "
                            + sideEffectDays + " side-effect days", 14, Ui.MUTED));
        }
        page.addView(glance);

        page.addView(Ui.section(this, "Milestone reports"));
        page.addView(reportCard("6", "Week 6 report", "First 42 days of ratings, medication and notes", 42));
        page.addView(reportCard("12", "Week 12 report", "Full 84-day trial summary", 84));

        Button current = Ui.secondaryButton(this, "Preview current PDF now");
        current.setOnClickListener(view -> shareReport(Math.max(1, store.currentDay()), "Current progress report"));
        page.addView(current);

        LinearLayout included = Ui.card(this);
        included.addView(Ui.heading(this, "What’s included"));
        included.addView(Ui.text(this,
                "✓ Medication and dose history\n✓ Communication, engagement, focus, mood and appetite trends\n"
                        + "✓ Sleep and possible side-effect log\n✓ Every caregiver note",
                14, Ui.MUTED));
        page.addView(included);

        TextView notice = Ui.text(this,
                "This tracker records observations only. Medication decisions should be made with the prescriber.",
                12, Ui.GREEN);
        notice.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        notice.setBackground(Ui.background(Ui.MINT, 14, this));
        page.addView(notice);
        return scroll;
    }

    private View reportCard(String number, String title, String subtitle, int dayLimit) {
        LinearLayout card = Ui.horizontal(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(Ui.background(Ui.WHITE, 18, this));
        Ui.setMargins(card, 0, 0, 0, 12);
        TextView icon = Ui.text(this, number, 21, Ui.INK);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.background(Ui.LAVENDER, 14, this));
        card.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));
        LinearLayout words = Ui.vertical(this);
        words.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 6), 0);
        words.addView(Ui.heading(this, title));
        words.addView(Ui.text(this, subtitle, 12, Ui.MUTED));
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView action = Ui.text(this, store.currentDay() >= dayLimit ? "PDF" : "Day " + dayLimit, 12,
                store.currentDay() >= dayLimit ? Ui.GREEN : Ui.MUTED);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(action);
        card.setOnClickListener(view -> {
            if (store.currentDay() >= dayLimit) shareReport(dayLimit, title);
            else Toast.makeText(this, title + " will be ready on day " + dayLimit, Toast.LENGTH_LONG).show();
        });
        return card;
    }

    private void shareReport(int dayLimit, String label) {
        try {
            File pdf = ReportGenerator.create(this, store, dayLimit, label);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", pdf);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, store.getProfile().childName + " — " + label);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share report with doctor"));
        } catch (Exception exception) {
            Toast.makeText(this, "Could not create report: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private View settingsScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        TrackerStore.Profile profile = store.getProfile();
        addHeader(page, "Settings", "Profile, dates and private backup", "⚙");

        LinearLayout profileCard = Ui.card(this);
        profileCard.addView(Ui.heading(this, "Trial details"));
        EditText child = Ui.input(this, "Child’s name"); child.setText(profile.childName);
        EditText dose = Ui.input(this, "Prescribed dose"); dose.setText(profile.defaultDose);
        EditText doctor = Ui.input(this, "Doctor’s name (optional)"); doctor.setText(profile.doctorName);
        profileCard.addView(child); profileCard.addView(dose); profileCard.addView(doctor);
        final LocalDate[] selectedStart = {LocalDate.parse(profile.startDate)};
        Button dateButton = Ui.secondaryButton(this, "Start: " + friendlyDate.format(selectedStart[0]));
        dateButton.setOnClickListener(view -> pickDate(selectedStart[0], date -> {
            selectedStart[0] = date;
            dateButton.setText("Start: " + friendlyDate.format(date));
        }));
        profileCard.addView(dateButton);
        Button save = Ui.primaryButton(this, "Save trial details");
        save.setOnClickListener(view -> {
            profile.childName = valueOr(child, "Elle");
            profile.defaultDose = dose.getText().toString().trim();
            profile.doctorName = doctor.getText().toString().trim();
            profile.startDate = selectedStart[0].toString();
            store.saveProfile(profile);
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            renderTab("Settings");
        });
        profileCard.addView(save);
        page.addView(profileCard);

        page.addView(Ui.section(this, "Encrypted GitHub sync"));
        LinearLayout syncCard = Ui.card(this);
        syncCard.addView(Ui.heading(this, "🔐 USTungsten/ASDTrack"));
        syncCard.addView(Ui.text(this,
                "Both phones use the same family passphrase. GitHub receives only encrypted data; the token and passphrase are protected by this phone’s Android Keystore.",
                14, Ui.MUTED));
        SecureSettings.Config syncConfig = secureSettings.get();
        EditText token = Ui.input(this, "Fine-grained GitHub token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(syncConfig.token);
        EditText passphrase = Ui.input(this, "Family passphrase — at least 12 characters");
        passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passphrase.setText(syncConfig.passphrase);
        syncCard.addView(token);
        syncCard.addView(passphrase);
        syncCard.addView(Ui.text(this, "Encrypted file: private-data/elle-tracker.enc", 12, Ui.MUTED));
        Button saveSync = Ui.secondaryButton(this, "Save secure sync settings");
        saveSync.setOnClickListener(view -> {
            try {
                syncConfig.owner = "USTungsten";
                syncConfig.repo = "ASDTrack";
                syncConfig.branch = "main";
                syncConfig.path = "private-data/elle-tracker.enc";
                syncConfig.token = token.getText().toString().trim();
                syncConfig.passphrase = passphrase.getText().toString();
                if (!syncConfig.isReady()) {
                    Toast.makeText(this, "Enter a token and a family passphrase of at least 12 characters", Toast.LENGTH_LONG).show();
                    return;
                }
                secureSettings.save(syncConfig);
                Toast.makeText(this, "Sync settings saved securely", Toast.LENGTH_SHORT).show();
            } catch (Exception exception) {
                Toast.makeText(this, "Could not secure the sync settings", Toast.LENGTH_LONG).show();
            }
        });
        syncCard.addView(saveSync);
        Button syncNow = Ui.primaryButton(this, "↻  Sync both phones now");
        syncNow.setOnClickListener(view -> {
            try {
                syncConfig.token = token.getText().toString().trim();
                syncConfig.passphrase = passphrase.getText().toString();
                secureSettings.save(syncConfig);
                syncInBackground(syncNow, true);
            } catch (Exception exception) {
                Toast.makeText(this, "Could not secure the sync settings", Toast.LENGTH_LONG).show();
            }
        });
        syncCard.addView(syncNow);
        syncCard.addView(Ui.text(this,
                "Keep the family passphrase somewhere safe. The encrypted data cannot be recovered without it.",
                12, 0xFF984762));
        page.addView(syncCard);

        page.addView(Ui.section(this, "Move data between phones"));
        LinearLayout backupCard = Ui.card(this);
        backupCard.addView(Ui.text(this,
                "Use a backup to move or combine care on another phone. Backups contain private health observations; share them only with someone you trust.",
                14, Ui.MUTED));
        Button export = Ui.secondaryButton(this, "Share data backup");
        export.setOnClickListener(view -> shareBackup());
        backupCard.addView(export);
        Button importButton = Ui.secondaryButton(this, "Import data backup");
        importButton.setOnClickListener(view -> chooseBackup());
        backupCard.addView(importButton);
        page.addView(backupCard);

        LinearLayout safety = Ui.card(this);
        safety.setBackground(Ui.background(Ui.MINT, 18, this));
        safety.addView(Ui.heading(this, "Medical safety"));
        safety.addView(Ui.text(this,
                "Do not change or stop medication based on this app. Contact Elle’s prescriber about concerns or side effects. For urgent symptoms, seek emergency care.",
                13, Ui.GREEN));
        page.addView(safety);
        return scroll;
    }

    private void shareBackup() {
        try {
            File directory = new File(getFilesDir(), "backups");
            if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create backup folder");
            File backup = new File(directory, "elles-journey-backup.json");
            try (FileOutputStream stream = new FileOutputStream(backup)) {
                stream.write(store.exportJson().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", backup);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share private backup"));
        } catch (Exception exception) {
            Toast.makeText(this, "Could not create backup", Toast.LENGTH_LONG).show();
        }
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT_BACKUP_REQUEST);
    }

    private void syncInBackground(Button button, boolean notify) {
        SecureSettings.Config config = secureSettings.get();
        if (!config.isReady()) {
            if (notify) Toast.makeText(this, "Save the GitHub token and family passphrase first", Toast.LENGTH_LONG).show();
            return;
        }
        if (button != null) {
            button.setEnabled(false);
            button.setText("Syncing…");
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                GitHubSync.Result result = GitHubSync.sync(store, config);
                runOnUiThread(() -> {
                    if (button != null) {
                        button.setEnabled(true);
                        button.setText("↻  Sync both phones now");
                    }
                    if (notify) {
                        Toast.makeText(this, "Synced " + result.entries + " check-ins securely", Toast.LENGTH_LONG).show();
                        renderTab("Settings");
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (button != null) {
                        button.setEnabled(true);
                        button.setText("↻  Sync both phones now");
                    }
                    if (notify) Toast.makeText(this, "Sync failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_BACKUP_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
            if (stream == null) throw new Exception("File unavailable");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            byte[] bytes = output.toByteArray();
            if (bytes.length < 1 || !store.importJson(new String(bytes, StandardCharsets.UTF_8))) {
                throw new Exception("Not a valid Elle’s Journey backup");
            }
            Toast.makeText(this, "Backup imported", Toast.LENGTH_SHORT).show();
            renderTab("Today");
        } catch (Exception exception) {
            Toast.makeText(this, "Could not import backup: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private interface DateConsumer { void accept(LocalDate date); }

    private void pickDate(LocalDate initial, DateConsumer consumer) {
        new DatePickerDialog(this, (picker, year, month, day) -> consumer.accept(LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private String valueOr(EditText input, String fallback) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private String trimFloat(float value) {
        if (Math.abs(value - Math.round(value)) < .001f) return String.valueOf(Math.round(value));
        return String.format(Locale.US, "%.1f", value);
    }
}
