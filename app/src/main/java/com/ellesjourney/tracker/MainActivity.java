package com.ellesjourney.tracker;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int IMPORT_BACKUP_REQUEST = 4102;
    private static final int VOICE_TRANSCRIPTION_REQUEST = 4103;
    private static final int VIDEO_CAPTURE_REQUEST = 4104;
    private static final String[] WEEKLY_PROMPTS = {
            "Elle, tell me about your day.",
            "What is one thing you did today?",
            "Who did you spend time with, and what did you do?",
            "What was your favorite part? Why?",
            "Is there anything else you want to tell me?"
    };
    private static final String[] RESPONSE_CHOICES = {
            "No response, or the response was not about the question",
            "Yes/no, one word, or a copied phrase",
            "Shared one real detail",
            "Shared two or more connected details",
            "Shared a short story, added more, or asked a question"
    };
    private static final String[] SUPPORT_CHOICES = {
            "Only the set question — no extra help",
            "Repeated the set question",
            "Asked a more specific question",
            "Gave choices",
            "Gave words for Elle to copy"
    };
    private static final String[] COMMUNICATION_CHOICES = {
            "Spoken words", "Gesture or acting", "AAC or device", "Words plus gesture/acting", "Other"
    };
    private final DateTimeFormatter friendlyDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private TrackerStore store;
    private SecureSettings secureSettings;
    private FrameLayout content;
    private LinearLayout nav;
    private String activeTab = "Today";
    private EditText pendingVoiceInput;
    private TrackerStore.WeeklySample pendingVideoSample;
    private TextView pendingVideoStatus;
    private File pendingVideoFile;

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

        page.addView(Ui.heading(this, "Prescribed leucovorin dosage"));
        page.addView(Ui.text(this, "Enter the amount for each scheduled dose. You can change this later.", 13, Ui.MUTED));
        Spinner frequency = frequencySpinner(2);
        page.addView(frequency);
        LinearLayout doseRow = Ui.horizontal(this);
        EditText morningDose = Ui.decimalInput(this, "Dose 1 (mg)");
        EditText eveningDose = Ui.decimalInput(this, "Dose 2 (mg)");
        LinearLayout.LayoutParams morningParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
        morningParams.setMargins(0, Ui.dp(this, 7), Ui.dp(this, 5), Ui.dp(this, 13));
        LinearLayout.LayoutParams eveningParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
        eveningParams.setMargins(Ui.dp(this, 5), Ui.dp(this, 7), 0, Ui.dp(this, 13));
        doseRow.addView(morningDose, morningParams);
        doseRow.addView(eveningDose, eveningParams);
        page.addView(doseRow);

        TextView startLabel = Ui.heading(this, "Trial start date");
        page.addView(startLabel);
        Button startDate = Ui.secondaryButton(this, friendlyDate.format(LocalDate.now()));
        final LocalDate[] selectedStart = {LocalDate.now()};
        startDate.setOnClickListener(view -> pickDate(selectedStart[0], date -> {
            selectedStart[0] = date;
            startDate.setText(friendlyDate.format(date));
        }));
        page.addView(startDate);

        LinearLayout schedule = Ui.card(this);
        schedule.addView(cardHeader("▦", "Weekly care schedule", "The Today screen shows only the relevant school or therapy entry"));
        CheckBox setupMondaySchool = checkbox("Monday — school", true);
        CheckBox setupTuesdaySchool = checkbox("Tuesday — school + weekly language sample", true);
        CheckBox setupWednesdayTheraplay = checkbox("Wednesday — Theraplay Speech + OT", true);
        schedule.addView(setupMondaySchool);
        schedule.addView(setupTuesdaySchool);
        schedule.addView(setupWednesdayTheraplay);
        schedule.addView(Ui.text(this,
                "Tuesday after school is the main Weekly Talk Check. Wednesday after Speech/OT is the backup. Thursday and Friday can be health-only home days.",
                12, Ui.MUTED));
        page.addView(schedule);

        LinearLayout school = Ui.card(this);
        school.addView(cardHeader("School", "School and teacher", "Enter this once to make Tamika’s updates easier"));
        EditText setupSchoolName = Ui.input(this, "School name");
        EditText setupTeacherName = Ui.input(this, "Primary teacher");
        EditText setupClassName = Ui.input(this, "Class or grade");
        Spinner setupSchoolMethod = simpleSpinner(new String[]{
                "In person at pickup", "Teacher message or app", "Email", "Phone call", "Written daily note"
        }, 0);
        school.addView(setupSchoolName);
        school.addView(setupTeacherName);
        school.addView(setupClassName);
        school.addView(Ui.text(this, "HOW THE UPDATE USUALLY ARRIVES", 10, Ui.MUTED));
        school.addView(setupSchoolMethod);
        page.addView(school);

        LinearLayout therapy = Ui.card(this);
        therapy.addView(cardHeader("Speech", "Theraplay Speech and OT", "Provider names stay attached to their notes"));
        EditText setupTherapyClinic = Ui.input(this, "Clinic or provider");
        setupTherapyClinic.setText("Theraplay");
        EditText setupSpeechTherapist = Ui.input(this, "Speech therapist");
        EditText setupOtTherapist = Ui.input(this, "OT therapist");
        Spinner setupTherapyMethod = simpleSpinner(new String[]{
                "In person after session", "Clinician message or app", "Email", "Phone call", "Written session note"
        }, 0);
        therapy.addView(setupTherapyClinic);
        therapy.addView(setupSpeechTherapist);
        therapy.addView(setupOtTherapist);
        therapy.addView(Ui.text(this, "HOW THE UPDATE USUALLY ARRIVES", 10, Ui.MUTED));
        therapy.addView(setupTherapyMethod);
        page.addView(therapy);

        final LocalDate[] selectedAwayFriday = {nextFriday(LocalDate.now())};
        LinearLayout away = Ui.card(this);
        away.addView(cardHeader("Schedule", "Every-other-weekend schedule", "Away days are never counted as low scores or missed observations"));
        CheckBox setupAwayWeekends = checkbox("Elle is away every other weekend", true);
        Button awayFriday = Ui.secondaryButton(this,
                "First away Friday: " + friendlyDate.format(selectedAwayFriday[0]));
        awayFriday.setOnClickListener(view -> pickDate(selectedAwayFriday[0], date -> {
            selectedAwayFriday[0] = date;
            awayFriday.setText("First away Friday: " + friendlyDate.format(date));
        }));
        away.addView(setupAwayWeekends);
        away.addView(awayFriday);
        away.addView(Ui.text(this,
                "The app treats Friday through Sunday as an away period. You can still log medication confirmed by another caregiver.",
                12, Ui.MUTED));
        page.addView(away);

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
            profile.morningDose = morningDose.getText().toString().trim();
            profile.eveningDose = eveningDose.getText().toString().trim();
            profile.doseUnit = "mg";
            profile.dosesPerDay = frequency.getSelectedItemPosition() + 1;
            if (profile.dosesPerDay == 2 && profile.eveningDose.isEmpty()) profile.eveningDose = profile.morningDose;
            profile.defaultDose = prescribedDoseSummary(profile);
            profile.doctorName = "";
            profile.schoolMonday = setupMondaySchool.isChecked();
            profile.schoolTuesday = setupTuesdaySchool.isChecked();
            profile.theraplayWednesday = setupWednesdayTheraplay.isChecked();
            profile.weeklySampleDay = DayOfWeek.TUESDAY.getValue();
            profile.weeklyBackupDay = DayOfWeek.WEDNESDAY.getValue();
            profile.schoolName = setupSchoolName.getText().toString().trim();
            profile.teacherName = setupTeacherName.getText().toString().trim();
            profile.className = setupClassName.getText().toString().trim();
            profile.schoolUpdateMethod = String.valueOf(setupSchoolMethod.getSelectedItem());
            profile.therapyClinic = setupTherapyClinic.getText().toString().trim();
            profile.speechTherapist = setupSpeechTherapist.getText().toString().trim();
            profile.otTherapist = setupOtTherapist.getText().toString().trim();
            profile.therapyUpdateMethod = String.valueOf(setupTherapyMethod.getSelectedItem());
            profile.awayWeekendsEnabled = setupAwayWeekends.isChecked();
            profile.awayWeekendAnchor = selectedAwayFriday[0].toString();
            profile.awayWeekendLabel = "Away with grandmother";
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
        addNavItem("Today");
        addNavItem("History");
        addNavItem("Reports");
        addNavItem("Settings");
    }

    private void addNavItem(String label) {
        boolean selected = label.equals(activeTab);
        LinearLayout item = Ui.vertical(this);
        item.setGravity(Gravity.CENTER);
        item.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 5));
        item.setBackground(Ui.background(selected ? Ui.LAVENDER : Ui.WHITE, 16, this));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label + " tab" + (selected ? ", selected" : ""));

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(navIconFor(label));
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setContentDescription("");
        item.addView(iconView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView labelView = Ui.text(this, label, 11, selected ? Ui.PURPLE : Ui.INK);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setOnClickListener(view -> renderTab(label));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(Ui.dp(this, 3), Ui.dp(this, 2), Ui.dp(this, 3), Ui.dp(this, 2));
        nav.addView(item, params);
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
        ImageView badge = artImage(iconResourceFor(title + " " + subtitle + " " + icon), 50);
        badge.setBackground(Ui.background(Ui.LAVENDER, 17, this));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));
        Ui.setMargins(row, 0, 0, 0, 20);
        page.addView(row);
    }

    private void addTodayHeader(LinearLayout page, String title, String subtitle, String icon) {
        LinearLayout row = Ui.horizontal(this);
        LinearLayout words = Ui.vertical(this);
        TextView eyebrow = Ui.text(this, "ELLE’S JOURNEY", 11, Ui.PURPLE);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(.1f);
        words.addView(eyebrow);
        words.addView(Ui.title(this, title));
        words.addView(Ui.text(this, subtitle, 15, Ui.MUTED));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView badge = artImage(R.drawable.nav_today_3d, 48);
        badge.setBackground(Ui.borderedBackground(Ui.LAVENDER, 0xFFDFD9FF, 16, this));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        Ui.setMargins(row, 0, 0, 0, 18);
        page.addView(row);
    }

    private LinearLayout cardHeader(String iconText, String title, String subtitle) {
        LinearLayout row = Ui.horizontal(this);
        ImageView icon = artImage(iconResourceFor(iconText + " " + title + " " + subtitle), 42);
        icon.setBackground(Ui.background(Ui.LAVENDER, 13, this));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout words = Ui.vertical(this);
        words.setPadding(Ui.dp(this, 11), 0, 0, 0);
        words.addView(Ui.heading(this, title));
        words.addView(Ui.text(this, subtitle, 12, Ui.MUTED));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Ui.setMargins(row, 0, 0, 0, 12);
        return row;
    }

    private ImageView artImage(int resource, int sizeDp) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        image.setContentDescription("");
        image.setMinimumWidth(Ui.dp(this, sizeDp));
        image.setMinimumHeight(Ui.dp(this, sizeDp));
        return image;
    }

    private int navIconFor(String label) {
        if ("History".equals(label)) return R.drawable.nav_history_3d;
        if ("Reports".equals(label)) return R.drawable.nav_reports_3d;
        if ("Settings".equals(label)) return R.drawable.nav_settings_3d;
        return R.drawable.nav_today_3d;
    }

    private int iconResourceFor(String hint) {
        String value = hint.toLowerCase(Locale.US);
        if (value.contains("voice") || value.contains("dictat") || value.contains("microphone")) return R.drawable.feature_voice_3d;
        if (value.contains("school") || value.contains("teacher") || value.contains("speech")
                || value.contains("therapy") || value.contains("theraplay") || value.contains("care-team")) return R.drawable.feature_school_3d;
        if (value.contains("sleep") || value.contains("bowel") || value.contains("health") || value.contains("comfort")) return R.drawable.feature_sleep_3d;
        if (value.contains("dose") || value.contains("leucovorin") || value.contains("medication") || value.contains("rx")) return R.drawable.feature_medicine_3d;
        if (value.contains("video")) return R.drawable.feature_video_3d;
        if (value.contains("report")) return R.drawable.nav_reports_3d;
        if (value.contains("history")) return R.drawable.nav_history_3d;
        if (value.contains("setting") || value.contains("profile") || value.contains("schedule")) return R.drawable.nav_settings_3d;
        if (value.contains("note") || value.contains("example") || value.contains("moment")) return R.drawable.feature_notes_3d;
        return R.drawable.feature_communication_3d;
    }

    private String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private Spinner frequencySpinner(int dosesPerDay) {
        Spinner spinner = new Spinner(this);
        String[] choices = {"Once daily", "Twice daily"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(1, dosesPerDay - 1)));
        spinner.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        params.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 8));
        spinner.setLayoutParams(params);
        return spinner;
    }

    private boolean hasStructuredDose(TrackerStore.Profile profile) {
        return profile.morningDose != null && !profile.morningDose.isEmpty();
    }

    private String prescribedDoseSummary(TrackerStore.Profile profile) {
        String unit = profile.doseUnit == null || profile.doseUnit.isEmpty() ? "mg" : profile.doseUnit;
        if (profile.morningDose == null || profile.morningDose.isEmpty()) return profile.defaultDose == null ? "" : profile.defaultDose;
        if (profile.dosesPerDay < 2) return profile.morningDose + " " + unit + " once daily";
        if (profile.morningDose.equals(profile.eveningDose)) return profile.morningDose + " " + unit + " × 2";
        return profile.morningDose + " " + unit + " + " + profile.eveningDose + " " + unit;
    }

    private String dailyDoseTotal(TrackerStore.Profile profile) {
        try {
            double first = Double.parseDouble(profile.morningDose);
            double second = profile.dosesPerDay < 2 || profile.eveningDose == null || profile.eveningDose.isEmpty()
                    ? 0 : Double.parseDouble(profile.eveningDose);
            return trimFloat((float) (first + second)) + " " + profile.doseUnit + "/day";
        } catch (Exception ignored) {
            return "Dose set";
        }
    }

    private boolean prescribedDosesComplete(TrackerStore.DailyEntry entry, TrackerStore.Profile profile) {
        return entry.morningDoseTaken && (profile.dosesPerDay < 2 || entry.eveningDoseTaken);
    }

    private String dailyEntryDoseSummary(TrackerStore.DailyEntry entry, String unitValue) {
        String unit = unitValue == null || unitValue.isEmpty() ? "mg" : unitValue;
        List<String> doses = new ArrayList<>();
        if (entry.morningDoseTaken) doses.add((entry.morningDose.isEmpty() ? "amount not entered" : entry.morningDose + " " + unit) + " first dose");
        if (entry.eveningDoseTaken) doses.add((entry.eveningDose.isEmpty() ? "amount not entered" : entry.eveningDose + " " + unit) + " second dose");
        return String.join(" + ", doses);
    }

    private float responseAverage(TrackerStore.DailyEntry entry) {
        return (entry.communication + entry.engagement + entry.focus + entry.mood + entry.appetite) / 5f;
    }

    private View todayScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        TrackerStore.Profile profile = store.getProfile();
        LocalDate today = LocalDate.now();
        TrackerStore.DailyEntry todayEntry = store.getEntry(today.toString());
        boolean scheduledAway = profile.isScheduledAway(today);
        addTodayHeader(page, greeting(), friendlyDate.format(today), "🌿");

        LinearLayout progressCard = Ui.card(this);
        progressCard.setBackground(Ui.gradient(Ui.PURPLE, Ui.PURPLE_LIGHT, 24, this));
        LinearLayout top = Ui.horizontal(this);
        TextView week = Ui.heading(this, "Week " + store.currentWeek() + " of 12");
        week.setTextColor(Ui.WHITE);
        TextView percent = Ui.text(this, "Day " + store.currentDay(), 14, 0xE6FFFFFF);
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
        int ratedDays = 0;
        int awayDays = 0;
        for (TrackerStore.DailyEntry entry : store.getEntries()) {
            if (entry.hasRatings) ratedDays++;
            else if (entry.isNotObserved()) awayDays++;
        }
        String dayContext = scheduledAway ? profile.awayWeekendLabel + " • no full check-in expected"
                : today.getDayOfWeek() == DayOfWeek.TUESDAY ? "School today • Weekly Talk Check after school"
                : today.getDayOfWeek() == DayOfWeek.WEDNESDAY ? "Speech/OT today • backup Talk Check day"
                : "Observed check-ins " + ratedDays + (awayDays > 0 ? " • " + awayDays + " away day(s)" : "");
        TextView count = Ui.text(this, dayContext, 13, 0xE6FFFFFF);
        progressCard.addView(count);
        page.addView(progressCard);

        if (scheduledAway) {
            LinearLayout away = Ui.card(this);
            away.setBackground(Ui.background(Ui.PEACH, 20, this));
            away.addView(cardHeader("Schedule", profile.awayWeekendLabel,
                    "No communication score is expected while Elle is not with you"));
            away.addView(Ui.text(this,
                    "Only enter information you know. A dose reported by another caregiver can be marked “Confirmed by caregiver.” If you do not know, choose “Not confirmed.”",
                    13, 0xFF98611C));
            Button awayButton = Ui.secondaryButton(this,
                    todayEntry != null && todayEntry.isNotObserved() ? "Update today’s reported information" : "Mark today not observed");
            awayButton.setOnClickListener(view -> {
                TrackerStore.DailyEntry entry = store.getEntry(today.toString());
                boolean newEntry = entry == null;
                if (newEntry) entry = new TrackerStore.DailyEntry();
                entry.date = today.toString();
                entry.observationStatus = "Away / not observed";
                entry.hasRatings = false;
                if (newEntry) {
                    entry.healthObserved = false;
                    entry.doseConfirmation = "Not confirmed";
                }
                store.saveEntry(entry);
                showEntryEditor(today);
            });
            away.addView(awayButton);
            page.addView(away);
        }

        LinearLayout medication = Ui.card(this);
        medication.addView(cardHeader("Rx", "Leucovorin",
                scheduledAway ? "Log only what you observed or another caregiver confirmed" : "Today’s medication"));
        if (hasStructuredDose(profile)) {
            LinearLayout prescribed = Ui.horizontal(this);
            LinearLayout details = Ui.vertical(this);
            details.addView(Ui.text(this, "PRESCRIBED SCHEDULE", 10, Ui.MUTED));
            TextView doseTitle = Ui.heading(this, prescribedDoseSummary(profile));
            doseTitle.setTextSize(21);
            details.addView(doseTitle);
            prescribed.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView total = statusPill(dailyDoseTotal(profile), Ui.LAVENDER, Ui.PURPLE);
            prescribed.addView(total, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38)));
            medication.addView(prescribed);

            LinearLayout doseStatus = Ui.horizontal(this);
            boolean firstTaken = todayEntry != null && todayEntry.morningDoseTaken;
            boolean secondTaken = todayEntry != null && todayEntry.eveningDoseTaken;
            doseStatus.addView(statusPill((firstTaken ? "✓ " : "○ ") + "First  " + profile.morningDose + " " + profile.doseUnit,
                    firstTaken ? Ui.MINT : Ui.PEACH, firstTaken ? Ui.GREEN : 0xFF9A641F),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            if (profile.dosesPerDay >= 2) {
                doseStatus.addView(statusPill((secondTaken ? "✓ " : "○ ") + "Second  " + profile.eveningDose + " " + profile.doseUnit,
                        secondTaken ? Ui.MINT : Ui.PEACH, secondTaken ? Ui.GREEN : 0xFF9A641F),
                        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            }
            medication.addView(doseStatus);
            Button logDose = Ui.primaryButton(this, todayEntry != null && prescribedDosesComplete(todayEntry, profile)
                    ? "Review today’s doses" : "Log today’s dose");
            logDose.setOnClickListener(view -> showEntryEditor(today));
            medication.addView(logDose);
        } else {
            medication.addView(Ui.text(this,
                    profile.defaultDose.isEmpty() ? "Add the prescribed dose to show the daily medication schedule."
                            : "Current note: " + profile.defaultDose + "\nAdd structured dose amounts for clearer tracking.",
                    14, Ui.MUTED));
            Button addDose = Ui.secondaryButton(this, "Add dosage in Settings");
            addDose.setOnClickListener(view -> renderTab("Settings"));
            medication.addView(addDose);
        }
        page.addView(medication);

        LinearLayout checkin = Ui.card(this);
        boolean homeDay = today.getDayOfWeek() == DayOfWeek.THURSDAY || today.getDayOfWeek() == DayOfWeek.FRIDAY;
        String checkinTitle = scheduledAway ? "No full check-in needed today"
                : todayEntry == null ? "Daily response check-in" : todayEntry.hasRatings
                ? "Today’s response is logged" : "Health-only day is logged";
        String checkinHelp = scheduledAway ? "Away days never become zero scores"
                : homeDay ? "A medication and health-only entry is okay on a quiet home day"
                : "Communication, connection, focus, mood and appetite • 0–5";
        checkin.addView(cardHeader("5", checkinTitle, checkinHelp));
        Button log = Ui.primaryButton(this, todayEntry == null ? "Start today’s entry" : "Edit today’s entry");
        log.setOnClickListener(view -> showEntryEditor(today));
        checkin.addView(log);
        if (todayEntry != null && todayEntry.hasRatings) {
            LinearLayout quick = Ui.horizontal(this);
            quick.addView(statusPill("Response " + trimFloat(responseAverage(todayEntry)) + "/5", Ui.LAVENDER, Ui.PURPLE),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            quick.addView(statusPill("☾ Sleep " + trimFloat(todayEntry.sleepHours) + "h", Ui.PEACH, 0xFFA65E24),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            checkin.addView(quick);
        }
        page.addView(checkin);

        DayOfWeek dayOfWeek = today.getDayOfWeek();
        String careSource = null;
        String careTitle = null;
        if (dayOfWeek == DayOfWeek.MONDAY && profile.schoolMonday) {
            careSource = "Teacher";
            careTitle = "Add today’s school update";
        } else if (dayOfWeek == DayOfWeek.TUESDAY && profile.schoolTuesday) {
            careSource = "Teacher";
            careTitle = "Add today’s school update";
        } else if (dayOfWeek == DayOfWeek.WEDNESDAY && profile.theraplayWednesday) {
            careSource = "Theraplay • Speech + OT";
            careTitle = "Add today’s Theraplay update";
        }
        if (careSource != null) {
            final String selectedCareSource = careSource;
            List<TrackerStore.CareUpdate> todayUpdates = store.getCareUpdates(today.toString());
            LinearLayout care = Ui.card(this);
            care.addView(cardHeader("✎", careTitle,
                    todayUpdates.isEmpty() ? "Voice-to-text, tags and exact examples"
                            : todayUpdates.size() + " care-team update(s) saved today"));
            Button addCare = Ui.primaryButton(this, todayUpdates.isEmpty() ? "Add update" : "Add or review update");
            addCare.setOnClickListener(view -> showCareUpdateEditor(today, selectedCareSource));
            care.addView(addCare);
            page.addView(care);
        }

        TrackerStore.WeeklySample weeklySample = store.getWeeklySample(store.currentWeek());
        LinearLayout weekly = Ui.card(this);
        boolean sampleDay = dayOfWeek.getValue() == profile.weeklySampleDay;
        boolean backupSampleDay = dayOfWeek.getValue() == profile.weeklyBackupDay;
        String weeklyHelp = sampleDay ? "Today is the preferred day: after school"
                : backupSampleDay ? "Use today after Speech/OT only if Tuesday was missed"
                : "Best on Tuesday after school; Wednesday is the backup";
        weekly.addView(cardHeader("Talk", weeklySample == null ? "Weekly Talk Check" : "Week "
                        + store.currentWeek() + " Talk Check saved", weeklyHelp));
        Button weeklyButton = Ui.secondaryButton(this, weeklySample == null ? "Start Weekly Talk Check" : "Review Weekly Talk Check");
        weeklyButton.setOnClickListener(view -> showWeeklySampleEditor(store.currentWeek()));
        weekly.addView(weeklyButton);
        page.addView(weekly);

        TrackerStore.WeeklySample baseline = store.getWeeklySample(0);
        if (baseline == null) {
            LinearLayout startingPoint = Ui.card(this);
            startingPoint.addView(cardHeader("Talk", "Save Elle’s starting example",
                    "Use last week’s first tell-us-about-your-day moment as the comparison point"));
            startingPoint.addView(Ui.text(this,
                    "Enter the hand-holding reenactment and Elle’s exact words, “Help mommy.” The report will label it by date and will not assume the medication caused it.",
                    13, Ui.MUTED));
            Button addStartingPoint = Ui.secondaryButton(this, "Add starting example");
            addStartingPoint.setOnClickListener(view -> showWeeklySampleEditor(0));
            startingPoint.addView(addStartingPoint);
            page.addView(startingPoint);
        }

        page.addView(Ui.section(this, "Next milestone"));
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
        TrackerStore.DailyEntry existing = store.getEntry(date.toString());
        TrackerStore.DailyEntry entry = existing == null ? new TrackerStore.DailyEntry() : existing;
        entry.date = date.toString();
        showDailyRatingsStep(date, entry);
    }

    private void showDailyRatingsStep(LocalDate date, TrackerStore.DailyEntry entry) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        int dayNumber = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(store.getProfile().startDate), date) + 1);
        addHeader(page, "Daily check-in", "Step 1 of 3 • " + friendlyDate.format(date) + " • Day " + dayNumber, "1");
        TrackerStore.Profile profile = store.getProfile();

        LinearLayout observation = Ui.card(this);
        observation.addView(cardHeader("Notes", "How much could you observe today?",
                "Away and health-only days are not given communication scores"));
        String[] observationChoices = {
                "Observed enough to rate", "Partial / health only", "Away / not observed"
        };
        Spinner observationStatus = simpleSpinner(observationChoices,
                choiceIndex(observationChoices, entry.observationStatus));
        observation.addView(observationStatus);
        observation.addView(Ui.text(this,
                "Choose “Away / not observed” when Elle is with her grandmother. Choose “Partial / health only” on a quiet home day when there was not enough interaction to rate.",
                12, Ui.MUTED));
        page.addView(observation);

        LinearLayout medicine = Ui.card(this);
        medicine.addView(cardHeader("Rx", "Leucovorin doses",
                hasStructuredDose(profile) ? "Prescribed: " + prescribedDoseSummary(profile)
                        : "Enter the amount actually given"));
        CheckBox firstDoseTaken = checkbox(profile.dosesPerDay < 2 ? "Daily dose taken" : "First dose taken",
                entry.morningDoseTaken || (entry.doseTaken && !entry.eveningDoseTaken));
        EditText firstDose = Ui.decimalInput(this, "First dose amount (mg)");
        firstDose.setText(!entry.morningDose.isEmpty() ? entry.morningDose : profile.morningDose);
        medicine.addView(firstDoseTaken);
        medicine.addView(firstDose);
        boolean showSecondDose = profile.dosesPerDay >= 2 || entry.eveningDoseTaken || !entry.eveningDose.isEmpty();
        CheckBox secondDoseTaken = checkbox("Second dose taken", entry.eveningDoseTaken);
        EditText secondDose = Ui.decimalInput(this, "Second dose amount (mg)");
        secondDose.setText(!entry.eveningDose.isEmpty() ? entry.eveningDose : profile.eveningDose);
        if (showSecondDose) {
            medicine.addView(secondDoseTaken);
            medicine.addView(secondDose);
        }
        medicine.addView(Ui.text(this, "HOW DO YOU KNOW ABOUT THE DOSE?", 10, Ui.MUTED));
        String[] confirmationChoices = {"Observed by us", "Confirmed by caregiver", "Not given / missed", "Not confirmed"};
        Spinner doseConfirmation = simpleSpinner(confirmationChoices,
                choiceIndex(confirmationChoices, entry.doseConfirmation));
        medicine.addView(doseConfirmation);
        medicine.addView(Ui.text(this,
                "Not confirmed does not mean missed. It means you do not know.", 12, Ui.MUTED));
        page.addView(medicine);

        LinearLayout ratingsSection = Ui.vertical(this);
        LinearLayout baseline = Ui.card(this);
        baseline.setBackground(Ui.background(Ui.LAVENDER, 18, this));
        baseline.addView(Ui.heading(this, "Use 3 for Elle’s usual baseline"));
        baseline.addView(Ui.text(this,
                "0 much lower • 1 lower • 2 slightly lower • 3 usual • 4 clearly higher • 5 exceptional for Elle",
                12, Ui.PURPLE));
        ratingsSection.addView(baseline);

        RatingPicker communication = ratingCard(ratingsSection, "Communication", "Sharing needs, ideas or experiences",
                "Notice words, AAC, gestures, clearer meaning and attempts to tell or ask—not whether speech was perfect.",
                entry.communication);
        RatingPicker engagement = ratingCard(ratingsSection, "Social connection", "Seeks, responds and shares attention",
                "Look for responding to people, seeking interaction, sharing attention or enjoying a moment together.",
                entry.engagement);
        RatingPicker focus = ratingCard(ratingsSection, "Focus & participation", "Stays with an activity or instruction",
                "Compare how she joins, follows along and stays engaged with her own usual ability.", entry.focus);
        RatingPicker mood = ratingCard(ratingsSection, "Mood & regulation", "Comfort, flexibility and recovery",
                "Rate comfort and recovery after frustration—not good versus bad behavior.", entry.mood);
        RatingPicker appetite = ratingCard(ratingsSection, "Appetite", "Amount eaten compared with usual",
                "Consider illness, unfamiliar foods and schedule changes when the amount differs.", entry.appetite);
        page.addView(ratingsSection);
        observationStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ratingsSection.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button continueButton = Ui.primaryButton(this, "Continue to health & bowel");
        continueButton.setOnClickListener(view -> {
            boolean wasFirstTaken = entry.morningDoseTaken;
            boolean wasSecondTaken = entry.eveningDoseTaken;
            entry.morningDoseTaken = firstDoseTaken.isChecked();
            entry.eveningDoseTaken = showSecondDose && secondDoseTaken.isChecked();
            entry.morningDose = firstDose.getText().toString().trim();
            entry.eveningDose = showSecondDose ? secondDose.getText().toString().trim() : "";
            entry.doseTaken = entry.morningDoseTaken || entry.eveningDoseTaken;
            entry.dose = dailyEntryDoseSummary(entry, profile.doseUnit);
            entry.doseConfirmation = String.valueOf(doseConfirmation.getSelectedItem());
            if (doseConfirmation.getSelectedItemPosition() >= 2) {
                entry.morningDoseTaken = false;
                entry.eveningDoseTaken = false;
                entry.doseTaken = false;
                entry.dose = "";
            }
            entry.observationStatus = String.valueOf(observationStatus.getSelectedItem());
            entry.hasRatings = observationStatus.getSelectedItemPosition() == 0;
            String timeNow = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
            if (entry.morningDoseTaken && !wasFirstTaken && entry.morningDoseTime.isEmpty()) entry.morningDoseTime = timeNow;
            if (entry.eveningDoseTaken && !wasSecondTaken && entry.eveningDoseTime.isEmpty()) entry.eveningDoseTime = timeNow;
            entry.communication = communication.value;
            entry.engagement = engagement.value;
            entry.focus = focus.value;
            entry.mood = mood.value;
            entry.appetite = appetite.value;
            showDailyHealthStep(date, entry);
        });
        page.addView(continueButton);
        Button cancel = Ui.secondaryButton(this, "Cancel");
        cancel.setOnClickListener(view -> { nav.setVisibility(View.VISIBLE); renderTab(activeTab); });
        page.addView(cancel);
        content.addView(scroll);
    }

    private void showDailyHealthStep(LocalDate date, TrackerStore.DailyEntry entry) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, "Health & comfort", "Step 2 of 3 • " + friendlyDate.format(date), "2");

        LinearLayout sleepCard = Ui.card(this);
        CheckBox healthObserved = checkbox("We know enough to record health details", entry.healthObserved);
        sleepCard.addView(healthObserved);
        TextView sleepValue = Ui.heading(this, "Sleep: " + trimFloat(entry.sleepHours) + " hours");
        sleepCard.addView(sleepValue);
        sleepCard.addView(Ui.text(this,
                "Record hours plus unusual trouble falling asleep, waking, restlessness or daytime tiredness.",
                12, Ui.MUTED));
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
        page.addView(effects);

        LinearLayout bowel = Ui.card(this);
        bowel.addView(cardHeader("●", "Bowel movements", "Useful tolerability and daily-context information"));
        bowel.addView(Ui.text(this, "HOW MANY TODAY?", 10, Ui.MUTED));
        Spinner bowelCount = simpleSpinner(new String[]{"Not logged", "None", "1", "2", "3+"},
                entry.bowelMovementCount < 0 ? 0 : Math.min(4, entry.bowelMovementCount + 1));
        bowel.addView(bowelCount);
        bowel.addView(Ui.text(this, "CLOSEST CONSISTENCY", 10, Ui.MUTED));
        Spinner bowelConsistency = simpleSpinner(new String[]{
                "Not logged", "1 • Separate hard pellets", "2 • Lumpy", "3 • Cracked surface",
                "4 • Smooth and formed", "5 • Soft pieces", "6 • Mushy", "7 • Liquid"
        }, Math.max(0, Math.min(7, entry.bowelConsistency)));
        bowel.addView(bowelConsistency);
        CheckBox bowelPain = checkbox("Pain or straining", entry.bowelPain);
        CheckBox bowelUrgency = checkbox("Urgency or accident", entry.bowelUrgency);
        bowel.addView(bowelPain);
        bowel.addView(bowelUrgency);
        bowel.addView(Ui.text(this,
                "Choose the closest description. This is context for the clinician, not a diagnosis.",
                12, Ui.MUTED));
        page.addView(bowel);

        Button continueButton = Ui.primaryButton(this, "Continue to notes");
        continueButton.setOnClickListener(view -> {
            entry.sleepHours = sleep.getProgress() / 4f;
            entry.healthObserved = healthObserved.isChecked();
            entry.sleepChange = sleepChange.isChecked();
            entry.tummyUpset = tummyUpset.isChecked();
            entry.headache = headache.isChecked();
            entry.irritability = irritability.isChecked();
            entry.otherSideEffect = other.isChecked();
            int countSelection = bowelCount.getSelectedItemPosition();
            entry.bowelMovementCount = countSelection == 0 ? -1 : countSelection - 1;
            entry.bowelConsistency = bowelConsistency.getSelectedItemPosition();
            entry.bowelPain = bowelPain.isChecked();
            entry.bowelUrgency = bowelUrgency.isChecked();
            showDailyNotesStep(date, entry);
        });
        page.addView(continueButton);
        Button back = Ui.secondaryButton(this, "Back to ratings");
        back.setOnClickListener(view -> showDailyRatingsStep(date, entry));
        page.addView(back);
        content.addView(scroll);
    }

    private void showDailyNotesStep(LocalDate date, TrackerStore.DailyEntry entry) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, "Notes & examples", "Step 3 of 3 • " + friendlyDate.format(date), "3");

        LinearLayout moment = Ui.card(this);
        moment.addView(cardHeader("✎", "Communication moment", "Concrete examples make the report more useful"));
        EditText context = multilineInput("What happened and where?", entry.momentContext, 74);
        EditText exactWords = multilineInput("Elle’s exact words or communication example", entry.exactWords, 74);
        Spinner communicationMode = simpleSpinner(COMMUNICATION_CHOICES,
                choiceIndex(COMMUNICATION_CHOICES, entry.communicationMode));
        EditText caregiverMeaning = multilineInput("What do you think Elle was telling you? Leave blank if unsure.",
                entry.caregiverMeaning, 72);
        CheckBox eventConfirmed = checkbox("Someone could confirm this was a real event", entry.eventConfirmed);
        EditText prompts = multilineInput("What help or prompts were given?", entry.promptsUsed, 74);
        EditText observer = Ui.input(this, "Who observed it?");
        observer.setText(entry.observer.isEmpty() ? "Tamika" : entry.observer);
        Button dictateContext = Ui.primaryButton(this, "Speak this moment into the phone");
        dictateContext.setOnClickListener(view -> startVoiceTranscription(context));
        moment.addView(dictateContext);
        moment.addView(context);
        moment.addView(exactWords);
        Button dictateWords = Ui.secondaryButton(this, "Speak Elle’s exact words");
        dictateWords.setOnClickListener(view -> startVoiceTranscription(exactWords));
        moment.addView(dictateWords);
        moment.addView(Ui.text(this, "HOW DID ELLE COMMUNICATE?", 10, Ui.MUTED));
        moment.addView(communicationMode);
        moment.addView(caregiverMeaning);
        moment.addView(eventConfirmed);
        moment.addView(prompts);
        moment.addView(observer);
        moment.addView(Ui.text(this,
                "Gestures, acting, AAC and remembered phrases count. Save exactly what happened and choose “I’m not sure” by leaving the meaning blank.",
                12, Ui.PURPLE));
        page.addView(moment);

        LinearLayout contextCard = Ui.card(this);
        contextCard.addView(Ui.heading(this, "Anything that affected today?"));
        contextCard.addView(Ui.text(this,
                "Examples: illness, poor sleep, school event, therapy change, unusual routine or dose change.",
                12, Ui.MUTED));
        EditText factors = multilineInput("Optional context", entry.factors, 76);
        EditText note = multilineInput("Other optional note for Elle’s doctor", entry.note, 92);
        contextCard.addView(factors);
        contextCard.addView(note);
        page.addView(contextCard);

        Button save = Ui.primaryButton(this, "Save daily check-in");
        save.setOnClickListener(view -> {
            entry.momentContext = context.getText().toString().trim();
            entry.exactWords = exactWords.getText().toString().trim();
            entry.communicationMode = String.valueOf(communicationMode.getSelectedItem());
            entry.caregiverMeaning = caregiverMeaning.getText().toString().trim();
            entry.eventConfirmed = eventConfirmed.isChecked();
            entry.promptsUsed = prompts.getText().toString().trim();
            entry.observer = observer.getText().toString().trim();
            entry.factors = factors.getText().toString().trim();
            entry.note = note.getText().toString().trim();
            store.saveEntry(entry);
            Toast.makeText(this, "Check-in saved", Toast.LENGTH_SHORT).show();
            nav.setVisibility(View.VISIBLE);
            renderTab("Today");
            syncInBackground(null, false);
        });
        page.addView(save);
        Button back = Ui.secondaryButton(this, "Back to health & bowel");
        back.setOnClickListener(view -> showDailyHealthStep(date, entry));
        page.addView(back);
        content.addView(scroll);
    }

    private void showCareUpdateEditor(LocalDate date, String defaultSource) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, defaultSource.startsWith("Teacher") ? "School update" : "Theraplay update",
                friendlyDate.format(date) + " • voice-to-text or keyboard", "✎");
        TrackerStore.Profile profile = store.getProfile();

        TrackerStore.CareUpdate update = null;
        for (TrackerStore.CareUpdate candidate : store.getCareUpdates(date.toString())) {
            if (candidate.source.equals(defaultSource)
                    || (defaultSource.startsWith("Theraplay") && candidate.source.startsWith("Theraplay"))) {
                update = candidate;
            }
        }
        if (update == null) update = new TrackerStore.CareUpdate();
        update.date = date.toString();
        final TrackerStore.CareUpdate careUpdate = update;

        LinearLayout sourceCard = Ui.card(this);
        sourceCard.addView(cardHeader("#", "Update source", "The report keeps each observer or provider clearly labeled"));
        String[] sourceChoices = {"Teacher", "Theraplay • Speech", "Theraplay • OT", "Theraplay • Speech + OT", "Other therapy"};
        int selectedSource = 0;
        for (int index = 0; index < sourceChoices.length; index++) {
            if (sourceChoices[index].equals(careUpdate.source) || sourceChoices[index].equals(defaultSource)) selectedSource = index;
        }
        Spinner source = simpleSpinner(sourceChoices, selectedSource);
        EditText provider = Ui.input(this, "Person or provider");
        provider.setText(careUpdate.provider.isEmpty()
                ? providerForSource(profile, sourceChoices[selectedSource])
                : careUpdate.provider);
        EditText enteredBy = Ui.input(this, "Entered by");
        enteredBy.setText(careUpdate.enteredBy.isEmpty() ? "Tamika" : careUpdate.enteredBy);
        sourceCard.addView(source);
        sourceCard.addView(provider);
        sourceCard.addView(enteredBy);
        if (careUpdate.provider.isEmpty()) {
            source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    provider.setText(providerForSource(profile, sourceChoices[position]));
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        page.addView(sourceCard);

        LinearLayout transcriptCard = Ui.card(this);
        transcriptCard.addView(cardHeader("🎙", "Dictate the update", "Speech becomes editable, searchable text"));
        EditText transcript = multilineInput("What did the teacher or therapist report?", careUpdate.transcript, 130);
        Button dictate = Ui.primaryButton(this, "🎙  Start voice-to-text");
        dictate.setOnClickListener(view -> startVoiceTranscription(transcript));
        transcriptCard.addView(dictate);
        transcriptCard.addView(transcript);
        transcriptCard.addView(Ui.text(this,
                "Tamika: repeat what the teacher or therapist said, then fix any wrong words. The saved text is what appears in Elle’s report. Raw microphone audio is not stored.",
                12, Ui.MUTED));
        page.addView(transcriptCard);

        LinearLayout tags = Ui.card(this);
        tags.addView(cardHeader("✓", "Tag what was reported", "Tags summarize themes without changing the original words"));
        CheckBox spontaneous = checkbox("Spontaneous communication", careUpdate.spontaneousCommunication);
        CheckBox peers = checkbox("Peer interaction", careUpdate.peerInteraction);
        CheckBox toldDay = checkbox("Told about her day", careUpdate.toldAboutDay);
        CheckBox directions = checkbox("Followed directions", careUpdate.followedDirections);
        CheckBox participation = checkbox("Participation", careUpdate.participation);
        CheckBox regulation = checkbox("Regulation or sensory", careUpdate.regulationSensory);
        CheckBox independence = checkbox("Independence or self-care", careUpdate.independenceSelfCare);
        CheckBox concern = checkbox("Concern", careUpdate.concern);
        tags.addView(spontaneous); tags.addView(peers); tags.addView(toldDay); tags.addView(directions);
        tags.addView(participation); tags.addView(regulation); tags.addView(independence); tags.addView(concern);
        page.addView(tags);

        LinearLayout therapy = Ui.card(this);
        therapy.addView(cardHeader("◎", "Session detail", "Especially useful for Speech and OT visits"));
        EditText goal = multilineInput("Goal or skill practiced", careUpdate.goalSkill, 72);
        EditText support = multilineInput("Support used and measurable progress", careUpdate.supportProgress, 82);
        EditText home = multilineInput("Home recommendation", careUpdate.homeRecommendation, 72);
        therapy.addView(goal);
        therapy.addView(support);
        therapy.addView(home);
        page.addView(therapy);

        Button save = Ui.primaryButton(this, "Save care-team update");
        save.setOnClickListener(view -> {
            careUpdate.source = String.valueOf(source.getSelectedItem());
            careUpdate.provider = provider.getText().toString().trim();
            careUpdate.enteredBy = enteredBy.getText().toString().trim();
            careUpdate.transcript = transcript.getText().toString().trim();
            careUpdate.spontaneousCommunication = spontaneous.isChecked();
            careUpdate.peerInteraction = peers.isChecked();
            careUpdate.toldAboutDay = toldDay.isChecked();
            careUpdate.followedDirections = directions.isChecked();
            careUpdate.participation = participation.isChecked();
            careUpdate.regulationSensory = regulation.isChecked();
            careUpdate.independenceSelfCare = independence.isChecked();
            careUpdate.concern = concern.isChecked();
            careUpdate.goalSkill = goal.getText().toString().trim();
            careUpdate.supportProgress = support.getText().toString().trim();
            careUpdate.homeRecommendation = home.getText().toString().trim();
            store.saveCareUpdate(careUpdate);
            Toast.makeText(this, "Care-team update saved", Toast.LENGTH_SHORT).show();
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

    private void showWeeklySampleEditor(int weekNumber) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        String reviewName = weekNumber == 0 ? "Starting example" : "Week " + weekNumber;
        addHeader(page, "Weekly Talk Check", reviewName + " • about 5 minutes", "Talk");

        TrackerStore.WeeklySample existing = store.getWeeklySample(weekNumber);
        TrackerStore.WeeklySample sample = existing == null ? new TrackerStore.WeeklySample() : existing;
        sample.weekNumber = weekNumber;
        if (existing == null) sample.date = LocalDate.now().toString();
        sample.ensurePromptResponses();

        LinearLayout protocol = Ui.card(this);
        protocol.addView(cardHeader("Talk", "Tamika, the app will guide you", "One question at a time, using very simple steps"));
        protocol.addView(Ui.text(this,
                "Say the words shown on the screen. Wait 10 seconds before helping. Gestures, acting, AAC and remembered phrases all count. Save Elle’s exact words, even when you are not sure what they mean.",
                14, Ui.INK));
        TextView rule = Ui.text(this,
                "Do not correct Elle during this short check. Praise her when she is finished. Stop if she is tired, upset or does not want to continue.",
                12, Ui.PURPLE);
        rule.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        rule.setBackground(Ui.background(Ui.LAVENDER, 13, this));
        Ui.setMargins(rule, 0, 12, 0, 0);
        protocol.addView(rule);
        page.addView(protocol);

        final LocalDate[] sampleDate = {LocalDate.parse(sample.date)};
        LinearLayout timing = Ui.card(this);
        timing.addView(cardHeader("Schedule", "Use a similar time each week", "Tuesday after school is best; Wednesday after Speech/OT is the backup"));
        Button dateButton = Ui.secondaryButton(this, "Date: " + friendlyDate.format(sampleDate[0]));
        dateButton.setOnClickListener(view -> pickDate(sampleDate[0], date -> {
            sampleDate[0] = date;
            dateButton.setText("Date: " + friendlyDate.format(date));
        }));
        Spinner setting = simpleSpinner(new String[]{
                "After school", "After Speech/OT", "Home day", "Other"
        }, choiceIndex(new String[]{"After school", "After Speech/OT", "Home day", "Other"}, sample.setting));
        timing.addView(dateButton);
        timing.addView(Ui.text(this, "WHAT HAPPENED BEFORE THE TALK CHECK?", 10, Ui.MUTED));
        timing.addView(setting);
        page.addView(timing);

        if (sample.hasPromptData()) {
            LinearLayout saved = Ui.card(this);
            saved.setBackground(Ui.background(Ui.MINT, 18, this));
            saved.addView(Ui.heading(this, "A Talk Check is already saved"));
            saved.addView(Ui.text(this,
                    "Current automatic score: " + trimFloat(sample.promptScoreAverage()) + "/5. You can review each answer and correct it.",
                    13, Ui.GREEN));
            page.addView(saved);
        }

        Button start = Ui.primaryButton(this, sample.hasPromptData() ? "Review question 1" : "Start question 1");
        start.setOnClickListener(view -> {
            sample.date = sampleDate[0].toString();
            sample.setting = String.valueOf(setting.getSelectedItem());
            sample.completed = true;
            sample.interruptionReason = "";
            showWeeklyPromptStep(sample, 0);
        });
        page.addView(start);
        Button cancel = Ui.secondaryButton(this, "Cancel");
        cancel.setOnClickListener(view -> { nav.setVisibility(View.VISIBLE); renderTab(activeTab); });
        page.addView(cancel);
        content.addView(scroll);
    }

    private void showWeeklyPromptStep(TrackerStore.WeeklySample sample, int promptIndex) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        TrackerStore.PromptResponse response = sample.promptResponses.get(promptIndex);
        addHeader(page, "Weekly Talk Check", "Question " + (promptIndex + 1) + " of 5", "Talk");

        LinearLayout progress = Ui.horizontal(this);
        for (int index = 0; index < WEEKLY_PROMPTS.length; index++) {
            TextView dot = Ui.text(this, String.valueOf(index + 1), 12,
                    index <= promptIndex ? Ui.WHITE : Ui.MUTED);
            dot.setGravity(Gravity.CENTER);
            dot.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            dot.setBackground(Ui.background(index <= promptIndex ? Ui.PURPLE : Ui.LIGHT, 12, this));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1);
            params.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), Ui.dp(this, 12));
            progress.addView(dot, params);
        }
        page.addView(progress);

        LinearLayout ask = Ui.card(this);
        ask.setBackground(Ui.gradient(0xFFEFF7FF, 0xFFF4F1FF, 21, this));
        ask.addView(Ui.text(this, "SAY EXACTLY THIS", 11, Ui.PURPLE));
        TextView prompt = Ui.heading(this, "“" + WEEKLY_PROMPTS[promptIndex] + "”");
        prompt.setTextSize(22);
        Ui.setMargins(prompt, 0, 8, 0, 8);
        ask.addView(prompt);
        ask.addView(Ui.text(this,
                "Say it once. Then tap the button and stay quiet until the timer ends.", 13, Ui.MUTED));
        TextView timer = Ui.heading(this, response.recorded ? "Answer already saved" : "Ready to wait");
        timer.setGravity(Gravity.CENTER);
        timer.setTextColor(Ui.PURPLE);
        Ui.setMargins(timer, 0, 14, 0, 0);
        ask.addView(timer);
        Button waitButton = Ui.primaryButton(this, response.recorded ? "Run the 10-second wait again" : "Start 10-second wait");
        ask.addView(waitButton);
        page.addView(ask);

        LinearLayout answer = Ui.card(this);
        answer.addView(cardHeader("Notes", "Record what Elle did", "The app calculates the 0–5 score for Tamika"));
        answer.addView(Ui.text(this, "WHAT WAS ELLE’S RESPONSE?", 10, Ui.MUTED));
        Spinner responseType = simpleSpinner(RESPONSE_CHOICES, response.responseType);
        answer.addView(responseType);
        answer.addView(Ui.text(this, "HOW MUCH EXTRA HELP DID YOU GIVE?", 10, Ui.MUTED));
        Spinner support = simpleSpinner(SUPPORT_CHOICES, response.supportLevel);
        answer.addView(support);
        answer.addView(Ui.text(this, "HOW DID ELLE COMMUNICATE?", 10, Ui.MUTED));
        Spinner communicationMode = simpleSpinner(COMMUNICATION_CHOICES,
                choiceIndex(COMMUNICATION_CHOICES, response.communicationMode));
        answer.addView(communicationMode);
        EditText exactWords = multilineInput("Elle’s exact words, gesture or action", response.exactWords, 88);
        answer.addView(exactWords);
        Button dictate = Ui.secondaryButton(this, "Speak Elle’s exact words into the phone");
        dictate.setOnClickListener(view -> startVoiceTranscription(exactWords));
        answer.addView(dictate);
        EditText meaning = multilineInput("What do you think Elle was telling you? Leave blank if unsure.",
                response.caregiverMeaning, 80);
        answer.addView(meaning);
        CheckBox confirmed = checkbox("Someone could confirm this was a real event", response.eventConfirmed);
        answer.addView(confirmed);
        TextView unsure = Ui.text(this,
                "It is okay to be unsure. Do not change Elle’s words to make them sound clearer.", 12, Ui.PURPLE);
        unsure.setPadding(Ui.dp(this, 11), Ui.dp(this, 10), Ui.dp(this, 11), Ui.dp(this, 10));
        unsure.setBackground(Ui.background(Ui.LAVENDER, 13, this));
        answer.addView(unsure);
        TextView scorePreview = Ui.heading(this, "Automatic score: " + response.score() + "/5");
        Ui.setMargins(scorePreview, 0, 13, 0, 0);
        answer.addView(scorePreview);
        answer.setVisibility(response.recorded ? View.VISIBLE : View.GONE);
        page.addView(answer);

        Runnable refreshScore = () -> {
            TrackerStore.PromptResponse preview = new TrackerStore.PromptResponse(promptIndex + 1);
            preview.responseType = responseType.getSelectedItemPosition();
            preview.supportLevel = support.getSelectedItemPosition();
            scorePreview.setText("Automatic score: " + preview.score() + "/5");
        };
        AdapterView.OnItemSelectedListener scoreListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refreshScore.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        responseType.setOnItemSelectedListener(scoreListener);
        support.setOnItemSelectedListener(scoreListener);

        waitButton.setOnClickListener(view -> {
            waitButton.setEnabled(false);
            timer.setText("Wait quietly… 10");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                int seconds = 9;
                @Override public void run() {
                    if (seconds > 0) {
                        timer.setText("Wait quietly… " + seconds--);
                        handler.postDelayed(this, 1000);
                    } else {
                        timer.setText("Now record what Elle did");
                        waitButton.setText("10-second wait complete");
                        answer.setVisibility(View.VISIBLE);
                        scroll.post(() -> scroll.smoothScrollTo(0, answer.getTop()));
                    }
                }
            }, 1000);
        });

        Button next = Ui.primaryButton(this, promptIndex == WEEKLY_PROMPTS.length - 1
                ? "Save answer and review" : "Save answer and continue");
        next.setOnClickListener(view -> {
            response.recorded = true;
            response.responseType = responseType.getSelectedItemPosition();
            response.supportLevel = support.getSelectedItemPosition();
            response.communicationMode = String.valueOf(communicationMode.getSelectedItem());
            response.exactWords = exactWords.getText().toString().trim();
            response.caregiverMeaning = meaning.getText().toString().trim();
            response.eventConfirmed = confirmed.isChecked();
            if (promptIndex < WEEKLY_PROMPTS.length - 1) showWeeklyPromptStep(sample, promptIndex + 1);
            else showWeeklyReview(sample);
        });
        answer.addView(next);
        Button notAsked = Ui.secondaryButton(this, "This question was not asked");
        notAsked.setOnClickListener(view -> {
            response.recorded = false;
            response.exactWords = "";
            response.caregiverMeaning = "";
            if (promptIndex < WEEKLY_PROMPTS.length - 1) showWeeklyPromptStep(sample, promptIndex + 1);
            else showWeeklyReview(sample);
        });
        answer.addView(notAsked);

        Button stop = Ui.secondaryButton(this, "Stop for today");
        stop.setOnClickListener(view -> {
            sample.completed = false;
            sample.interruptionReason = "Stopped because Elle was tired, upset, or did not want to continue.";
            store.saveWeeklySample(sample);
            Toast.makeText(this, "Partial Talk Check saved", Toast.LENGTH_SHORT).show();
            nav.setVisibility(View.VISIBLE);
            renderTab("Today");
        });
        page.addView(stop);
        if (promptIndex > 0) {
            Button back = Ui.secondaryButton(this, "Back to question " + promptIndex);
            back.setOnClickListener(view -> showWeeklyPromptStep(sample, promptIndex - 1));
            page.addView(back);
        }
        content.addView(scroll);
    }

    private void showWeeklyReview(TrackerStore.WeeklySample sample) {
        content.removeAllViews();
        nav.setVisibility(View.GONE);
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        String label = sample.weekNumber == 0 ? "Starting example" : "Week " + sample.weekNumber;
        addHeader(page, "Review before saving", label + " • Tamika can correct anything", "Notes");

        LinearLayout summary = Ui.card(this);
        summary.addView(cardHeader("Talk", "Talk Check summary",
                "Automatic average " + trimFloat(sample.promptScoreAverage()) + "/5 • "
                        + sample.recordedPromptCount() + " of 5 questions saved"));
        for (int index = 0; index < sample.promptResponses.size(); index++) {
            TrackerStore.PromptResponse response = sample.promptResponses.get(index);
            String answer = response.exactWords.isEmpty() ? "No exact words saved" : "“" + response.exactWords + "”";
            TextView line = Ui.text(this,
                    (index + 1) + ". " + response.score() + "/5 • " + answer,
                    13, response.recorded ? Ui.INK : Ui.MUTED);
            line.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
            final int promptIndex = index;
            line.setOnClickListener(view -> showWeeklyPromptStep(sample, promptIndex));
            summary.addView(line);
        }
        summary.addView(Ui.text(this,
                "A 3 means one relevant detail without extra help. Elle’s acting or remembered phrase can count when it clearly communicates a real event.",
                12, Ui.PURPLE));
        page.addView(summary);

        LinearLayout observed = Ui.card(this);
        observed.addView(cardHeader("Notes", "Two final observations", "Use simple facts—do not guess"));
        Spinner sampleTurns = simpleSpinner(new String[]{
                "0 back-and-forth turns", "1 back-and-forth turn", "2 back-and-forth turns",
                "3 back-and-forth turns", "4 back-and-forth turns", "5+ back-and-forth turns"
        }, Math.max(0, Math.min(5, sample.conversationTurns)));
        CheckBox feelingWord = checkbox("Elle used a feeling word or showed a feeling", sample.usedFeelingWord);
        CheckBox askedFollowUp = checkbox("Elle added more or asked a follow-up question", sample.askedFollowUp);
        EditText sampleNote = multilineInput("Anything important about today? Optional.", sample.note, 82);
        observed.addView(sampleTurns);
        observed.addView(feelingWord);
        observed.addView(askedFollowUp);
        observed.addView(sampleNote);
        page.addView(observed);

        LinearLayout video = Ui.card(this);
        video.addView(cardHeader("Video", "Optional private video", "Useful as an example, but not proof of what caused a change"));
        TextView videoStatus = Ui.text(this, weeklyVideoExists(sample)
                ? "Video is available on this phone" : sample.videoRecorded
                ? "Video was recorded on the other phone" : "No video recorded", 13, Ui.MUTED);
        video.addView(videoStatus);
        Button recordVideo = Ui.primaryButton(this, "Record this Talk Check locally");
        recordVideo.setOnClickListener(view -> startVideoCapture(sample, videoStatus));
        video.addView(recordVideo);
        if (weeklyVideoExists(sample)) {
            Button openVideo = Ui.secondaryButton(this, "Open local video");
            openVideo.setOnClickListener(view -> openWeeklyVideo(sample));
            video.addView(openVideo);
        }
        video.addView(Ui.text(this,
                "The video stays on this phone. Shared backups and reports include only a video marker.",
                12, Ui.GREEN));
        page.addView(video);

        Button save = Ui.primaryButton(this, "Save " + label);
        save.setOnClickListener(view -> {
            sample.completed = sample.recordedPromptCount() == WEEKLY_PROMPTS.length;
            sample.interruptionReason = sample.completed || sample.weekNumber == 0 ? ""
                    : "One or more standardized questions were not asked.";
            sample.detailCount = sample.totalPromptDetails();
            sample.conversationTurns = sampleTurns.getSelectedItemPosition();
            sample.usedFeelingWord = feelingWord.isChecked();
            sample.askedFollowUp = askedFollowUp.isChecked();
            sample.extraSupport = supportSummary(sample);
            sample.note = sampleNote.getText().toString().trim();
            int promptScore = Math.round(sample.promptScoreAverage());
            sample.tellsAboutDay = promptScore;
            sample.smallTalk = promptScore;
            sample.openQuestions = promptScore;
            sample.backAndForth = Math.min(5, sample.conversationTurns);
            sample.followUpQuestions = sample.askedFollowUp ? Math.max(4, promptScore) : Math.min(3, promptScore);
            store.saveWeeklySample(sample);
            Toast.makeText(this, label + " saved", Toast.LENGTH_SHORT).show();
            nav.setVisibility(View.VISIBLE);
            renderTab("Today");
            syncInBackground(null, false);
        });
        page.addView(save);
        Button back = Ui.secondaryButton(this, "Back to question 5");
        back.setOnClickListener(view -> showWeeklyPromptStep(sample, WEEKLY_PROMPTS.length - 1));
        page.addView(back);
        content.addView(scroll);
    }

    private String supportSummary(TrackerStore.WeeklySample sample) {
        List<String> support = new ArrayList<>();
        for (int index = 0; index < sample.promptResponses.size(); index++) {
            TrackerStore.PromptResponse response = sample.promptResponses.get(index);
            if (response.recorded && response.supportLevel > 0) {
                support.add("Question " + (index + 1) + ": " + SUPPORT_CHOICES[response.supportLevel].toLowerCase(Locale.US));
            }
        }
        return support.isEmpty() ? "No extra help beyond the set questions" : String.join("; ", support);
    }

    private TextView promptBlock(String label, String prompt) {
        TextView block = Ui.text(this, label + "\n" + prompt, 13, Ui.INK);
        block.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        block.setBackground(Ui.background(Ui.LIGHT, 13, this));
        Ui.setMargins(block, 0, 0, 0, 8);
        return block;
    }

    private EditText multilineInput(String hint, String value, int minimumHeightDp) {
        EditText input = Ui.input(this, hint);
        input.setSingleLine(false);
        input.setMinHeight(Ui.dp(this, minimumHeightDp));
        input.setGravity(Gravity.TOP);
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        input.setText(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 13));
        input.setLayoutParams(params);
        return input;
    }

    private Spinner simpleSpinner(String[] choices, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(choices.length - 1, selected)));
        spinner.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        params.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 13));
        spinner.setLayoutParams(params);
        return spinner;
    }

    private RatingPicker ratingCard(LinearLayout page, String title, String subtitle, String help, int selected) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, title));
        card.addView(Ui.text(this, subtitle, 13, Ui.MUTED));
        LinearLayout legend = Ui.horizontal(this);
        TextView low = Ui.text(this, "0 • much lower", 10, Ui.MUTED);
        TextView baseline = Ui.text(this, "3 • typical", 10, Ui.MUTED);
        TextView high = Ui.text(this, "5 • much higher", 10, Ui.MUTED);
        legend.addView(low, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        baseline.setGravity(Gravity.CENTER);
        legend.addView(baseline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        high.setGravity(Gravity.END);
        legend.addView(high, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Ui.setMargins(legend, 0, 10, 0, 0);
        card.addView(legend);
        RatingPicker picker = new RatingPicker(selected);
        card.addView(picker.row);
        TextView explanation = Ui.text(this, help, 12, Ui.PURPLE);
        explanation.setPadding(Ui.dp(this, 11), Ui.dp(this, 10), Ui.dp(this, 11), Ui.dp(this, 10));
        explanation.setBackground(Ui.background(Ui.LAVENDER, 13, this));
        Ui.setMargins(explanation, 0, 10, 0, 0);
        card.addView(explanation);
        page.addView(card);
        return picker;
    }

    private final class RatingPicker {
        final LinearLayout row = Ui.horizontal(MainActivity.this);
        final List<Button> buttons = new ArrayList<>();
        int value;

        RatingPicker(int selected) {
            value = Math.max(0, Math.min(5, selected));
            String[] labels = {"0", "1", "2", "3", "4", "5"};
            for (int index = 0; index < labels.length; index++) {
                Button button = new Button(MainActivity.this);
                button.setText(labels[index]);
                button.setTextSize(18);
                button.setAllCaps(false);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setPadding(0, 0, 0, 0);
                final int chosen = index;
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
                boolean selected = index == value;
                Button button = buttons.get(index);
                button.setTextColor(selected ? Ui.WHITE : Ui.INK);
                button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                button.setBackground(Ui.background(selected ? Ui.PURPLE : Ui.LIGHT, 13, MainActivity.this));
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
        addHeader(page, "History", "Search and filter every observation", "History");
        List<TrackerStore.DailyEntry> entries = store.getEntries();
        List<TrackerStore.WeeklySample> samples = store.getWeeklySamples();
        List<TrackerStore.CareUpdate> careUpdates = store.getCareUpdates();
        if (entries.isEmpty() && samples.isEmpty() && careUpdates.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.heading(this, "No observations yet"));
            empty.addView(Ui.text(this, "Daily check-ins, weekly reviews and care-team updates will appear here.", 14, Ui.MUTED));
            page.addView(empty);
            return scroll;
        }

        int observedDays = 0;
        int awayDays = 0;
        int doses = 0;
        for (TrackerStore.DailyEntry entry : entries) {
            if (entry.hasRatings) observedDays++;
            if (entry.isNotObserved()) awayDays++;
            doses += entry.dosesTakenCount();
        }
        LinearLayout summary = Ui.card(this);
        summary.setBackground(Ui.gradient(Ui.PURPLE, Ui.PURPLE_LIGHT, 22, this));
        TextView summaryTitle = Ui.heading(this, "Your records at a glance");
        summaryTitle.setTextColor(Ui.WHITE);
        summary.addView(summaryTitle);
        summary.addView(Ui.text(this,
                observedDays + " observed check-ins  •  " + doses + " doses  •  "
                        + careUpdates.size() + " school/therapy notes"
                        + (awayDays > 0 ? "  •  " + awayDays + " away day(s)" : ""),
                13, 0xE6FFFFFF));
        page.addView(summary);

        EditText search = Ui.input(this, "Search notes, names, or Elle’s exact words");
        page.addView(search);
        String[] typeChoices = {"All entries", "Daily", "School", "Speech / OT", "Weekly Talk Check", "Away days"};
        Spinner typeFilter = simpleSpinner(typeChoices, 0);
        int currentWeek = Math.max(1, store.currentWeek());
        String[] weekChoices = new String[currentWeek + 2];
        weekChoices[0] = "All weeks";
        weekChoices[1] = "Starting example";
        for (int index = 1; index <= currentWeek; index++) weekChoices[index + 1] = "Week " + index;
        Spinner weekFilter = simpleSpinner(weekChoices, 0);
        Spinner sort = simpleSpinner(new String[]{"Newest first", "Oldest first"}, 0);
        page.addView(Ui.text(this, "SHOW", 10, Ui.MUTED));
        page.addView(typeFilter);
        page.addView(Ui.text(this, "WEEK", 10, Ui.MUTED));
        page.addView(weekFilter);
        page.addView(Ui.text(this, "ORDER", 10, Ui.MUTED));
        page.addView(sort);

        TextView resultCount = Ui.text(this, "", 12, Ui.MUTED);
        page.addView(resultCount);
        LinearLayout results = Ui.vertical(this);
        page.addView(results);

        List<HistoryItem> allItems = buildHistoryItems(entries, samples, careUpdates, store.getProfile());
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.US);
            String selectedType = String.valueOf(typeFilter.getSelectedItem());
            int selectedWeekPosition = weekFilter.getSelectedItemPosition();
            int selectedWeek = selectedWeekPosition == 0 ? -1 : selectedWeekPosition - 1;
            List<HistoryItem> filtered = new ArrayList<>();
            for (HistoryItem item : allItems) {
                boolean typeMatch = "All entries".equals(selectedType)
                        || "Away days".equals(selectedType) && "away".equals(item.type)
                        || "Daily".equals(selectedType) && "daily".equals(item.type)
                        || "School".equals(selectedType) && "school".equals(item.type)
                        || "Speech / OT".equals(selectedType) && "therapy".equals(item.type)
                        || "Weekly Talk Check".equals(selectedType) && "weekly".equals(item.type);
                boolean weekMatch = selectedWeek < 0 || item.week == selectedWeek;
                boolean searchMatch = query.isEmpty() || item.searchText.contains(query);
                if (typeMatch && weekMatch && searchMatch) filtered.add(item);
            }
            Collections.sort(filtered, Comparator.comparing((HistoryItem item) -> item.date)
                    .thenComparing(item -> item.title));
            if (sort.getSelectedItemPosition() == 0) Collections.reverse(filtered);
            results.removeAllViews();
            resultCount.setText(filtered.size() + (filtered.size() == 1 ? " matching entry" : " matching entries"));
            if (filtered.isEmpty()) {
                LinearLayout empty = Ui.card(this);
                empty.addView(Ui.heading(this, "No entries match"));
                empty.addView(Ui.text(this, "Try a different word, entry type, or week.", 13, Ui.MUTED));
                results.addView(empty);
                return;
            }
            Map<String, List<HistoryItem>> byDate = new LinkedHashMap<>();
            for (HistoryItem item : filtered) byDate.computeIfAbsent(item.date.toString(), key -> new ArrayList<>()).add(item);
            for (List<HistoryItem> dayItems : byDate.values()) {
                LinearLayout dayCard = Ui.card(this);
                LocalDate date = dayItems.get(0).date;
                dayCard.addView(Ui.heading(this, friendlyDate.format(date)));
                dayCard.addView(Ui.text(this,
                        date.getDayOfWeek().toString().substring(0, 1)
                                + date.getDayOfWeek().toString().substring(1).toLowerCase(Locale.US)
                                + " • " + dayItems.size() + (dayItems.size() == 1 ? " entry" : " entries"),
                        12, Ui.MUTED));
                for (HistoryItem item : dayItems) dayCard.addView(historyItemView(item));
                results.addView(dayCard);
            }
        };

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render[0].run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        typeFilter.setOnItemSelectedListener(filterListener);
        weekFilter.setOnItemSelectedListener(filterListener);
        sort.setOnItemSelectedListener(filterListener);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { render[0].run(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        render[0].run();
        return scroll;
    }

    private List<HistoryItem> buildHistoryItems(List<TrackerStore.DailyEntry> entries,
                                                List<TrackerStore.WeeklySample> samples,
                                                List<TrackerStore.CareUpdate> careUpdates,
                                                TrackerStore.Profile profile) {
        List<HistoryItem> items = new ArrayList<>();
        for (TrackerStore.DailyEntry entry : entries) {
            LocalDate date = safeDate(entry.date, LocalDate.now());
            boolean away = entry.isNotObserved();
            String title = away ? "Away / not observed" : entry.hasRatings ? "Daily check-in" : "Health-only entry";
            String summary = entry.hasRatings
                    ? "Response " + trimFloat(responseAverage(entry)) + "/5"
                            + (entry.tellsAboutDay >= 0 ? " • tells about her day " + entry.tellsAboutDay + "/5" : "")
                    : entry.observationStatus;
            summary += entry.hasAnyDose() ? " • " + entry.dosesTakenCount() + " dose(s) • " + entry.doseConfirmation
                    : " • medication " + entry.doseConfirmation.toLowerCase(Locale.US);
            String searchText = title + " " + summary + " " + entry.momentContext + " " + entry.exactWords
                    + " " + entry.caregiverMeaning + " " + entry.promptsUsed + " " + entry.observer
                    + " " + entry.factors + " " + entry.note;
            HistoryItem item = new HistoryItem(date, away ? "away" : "daily", title, summary,
                    searchText.toLowerCase(Locale.US), weekForDate(date, profile),
                    away ? R.drawable.nav_today_3d : R.drawable.feature_communication_3d);
            item.open = () -> showEntryEditor(date);
            items.add(item);
        }
        for (TrackerStore.WeeklySample sample : samples) {
            LocalDate date = safeDate(sample.date, LocalDate.now());
            String title = sample.weekNumber == 0 ? "Starting communication example" : "Week " + sample.weekNumber + " Talk Check";
            String summary = sample.hasPromptData()
                    ? "Automatic score " + trimFloat(sample.promptScoreAverage()) + "/5 • "
                    + sample.recordedPromptCount() + "/5 questions • " + sample.totalPromptDetails() + " details • " + sample.setting
                    : "Earlier weekly review " + trimFloat(sample.ratingAverage()) + "/5";
            StringBuilder searchText = new StringBuilder(title).append(' ').append(summary).append(' ').append(sample.note);
            for (TrackerStore.PromptResponse response : sample.promptResponses) {
                searchText.append(' ').append(response.exactWords).append(' ').append(response.caregiverMeaning);
            }
            HistoryItem item = new HistoryItem(date, "weekly", title, summary,
                    searchText.toString().toLowerCase(Locale.US), sample.weekNumber, R.drawable.feature_video_3d);
            item.open = () -> showWeeklySampleEditor(sample.weekNumber);
            items.add(item);
        }
        for (TrackerStore.CareUpdate update : careUpdates) {
            LocalDate date = safeDate(update.date, LocalDate.now());
            boolean school = update.source.startsWith("Teacher");
            String title = update.source + (update.provider.isEmpty() ? "" : " • " + update.provider);
            String summary = update.transcript.isEmpty() ? "No transcript saved" : update.transcript;
            String searchText = title + " " + summary + " " + update.tagSummary() + " "
                    + update.goalSkill + " " + update.supportProgress + " " + update.homeRecommendation;
            HistoryItem item = new HistoryItem(date, school ? "school" : "therapy", title, summary,
                    searchText.toLowerCase(Locale.US), weekForDate(date, profile), R.drawable.feature_school_3d);
            item.open = () -> showCareUpdateEditor(date, update.source);
            items.add(item);
        }
        return items;
    }

    private View historyItemView(HistoryItem item) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 9));
        row.addView(artImage(item.iconResource, 44), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout copy = Ui.vertical(this);
        copy.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 5), 0);
        copy.addView(Ui.text(this, item.title, 14, Ui.INK));
        copy.addView(Ui.text(this, item.summary, 12, Ui.MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = Ui.text(this, "›", 25, Ui.PURPLE);
        row.addView(arrow);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> item.open.run());
        return row;
    }

    private int weekForDate(LocalDate date, TrackerStore.Profile profile) {
        try {
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(profile.startDate), date);
            return days < 0 ? 0 : Math.min(12, (int) (days / 7) + 1);
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static final class HistoryItem {
        final LocalDate date;
        final String type;
        final String title;
        final String summary;
        final String searchText;
        final int week;
        final int iconResource;
        Runnable open;

        HistoryItem(LocalDate date, String type, String title, String summary, String searchText,
                    int week, int iconResource) {
            this.date = date;
            this.type = type;
            this.title = title;
            this.summary = summary;
            this.searchText = searchText;
            this.week = week;
            this.iconResource = iconResource;
        }
    }

    private View reportsScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout page = page(scroll);
        addHeader(page, "Doctor reports", "Clear summaries to share at visits", "▥");
        List<TrackerStore.DailyEntry> entries = store.getEntries();
        List<TrackerStore.WeeklySample> weeklySamples = store.getWeeklySamples();
        List<TrackerStore.CareUpdate> careUpdates = store.getCareUpdates();
        TrackerStore.Profile profile = store.getProfile();
        LinearLayout medication = Ui.card(this);
        medication.addView(cardHeader("Rx", "Medication overview",
                hasStructuredDose(profile) ? "Leucovorin • " + prescribedDoseSummary(profile) : "Leucovorin"));
        int recordedDoses = 0;
        int unconfirmedMedicationDays = 0;
        int missedMedicationDays = 0;
        for (TrackerStore.DailyEntry entry : entries) {
            recordedDoses += entry.dosesTakenCount();
            if ("Not confirmed".equals(entry.doseConfirmation)) unconfirmedMedicationDays++;
            if ("Not given / missed".equals(entry.doseConfirmation)) missedMedicationDays++;
        }
        medication.addView(Ui.text(this,
                recordedDoses + " doses recorded" + (hasStructuredDose(profile) ? " • " + dailyDoseTotal(profile) + " prescribed" : ""),
                14, Ui.GREEN));
        if (unconfirmedMedicationDays > 0) medication.addView(Ui.text(this,
                unconfirmedMedicationDays + " day(s) marked not confirmed—not automatically counted as missed doses.",
                12, Ui.MUTED));
        if (missedMedicationDays > 0) medication.addView(Ui.text(this,
                missedMedicationDays + " day(s) explicitly marked not given / missed.",
                12, 0xFF984762));
        page.addView(medication);
        LinearLayout glance = Ui.card(this);
        glance.addView(Ui.heading(this, "At a glance"));
        if (entries.isEmpty()) {
            glance.addView(Ui.text(this, "Complete a daily check-in to begin building trends.", 14, Ui.MUTED));
        } else {
            int sideEffectDays = 0;
            int ratedDays = 0;
            int awayDays = 0;
            float total = 0;
            for (TrackerStore.DailyEntry entry : entries) {
                if (entry.hasRatings) {
                    total += responseAverage(entry);
                    ratedDays++;
                }
                if (entry.isNotObserved()) awayDays++;
                if (entry.hasSideEffects()) sideEffectDays++;
            }
            glance.addView(Ui.text(this,
                    ratedDays + " observed check-ins"
                            + (ratedDays > 0 ? " • Average response " + trimFloat(total / ratedDays) + "/5" : "")
                            + " • " + awayDays + " away day(s) • " + sideEffectDays + " concern day(s)",
                    14, Ui.MUTED));
        }
        glance.addView(Ui.text(this,
                weeklySamples.size() + " Talk Check or starting example(s) • " + careUpdates.size()
                        + " school/Speech/OT update(s)", 14, Ui.PURPLE));
        glance.addView(Ui.text(this,
                "Away and health-only days are excluded from communication averages.", 12, Ui.GREEN));
        page.addView(glance);

        TrackerStore.WeeklySample starting = null;
        TrackerStore.WeeklySample latest = null;
        for (TrackerStore.WeeklySample sample : weeklySamples) {
            if (sample.weekNumber == 0 && sample.hasPromptData()) starting = sample;
            else if (sample.weekNumber > 0 && sample.hasPromptData()) latest = sample;
        }
        LinearLayout communication = Ui.card(this);
        communication.addView(cardHeader("Talk", "Communication comparison",
                "Same five questions, same wait time, every week"));
        if (starting != null && latest != null) {
            communication.addView(Ui.heading(this,
                    "Starting " + trimFloat(starting.promptScoreAverage()) + "/5  →  Week "
                            + latest.weekNumber + " " + trimFloat(latest.promptScoreAverage()) + "/5"));
            communication.addView(Ui.text(this,
                    "Details shared: " + starting.totalPromptDetails() + " → " + latest.totalPromptDetails()
                            + " • Back-and-forth turns: " + starting.conversationTurns + " → " + latest.conversationTurns,
                    13, Ui.PURPLE));
        } else if (starting != null) {
            communication.addView(Ui.text(this,
                    "Starting example saved at " + trimFloat(starting.promptScoreAverage())
                            + "/5. Complete the next Weekly Talk Check to begin the comparison.",
                    14, Ui.MUTED));
        } else {
            communication.addView(Ui.text(this,
                    "Add Elle’s starting example so the report can compare later weeks with her starting point.",
                    14, Ui.MUTED));
            Button addStarting = Ui.secondaryButton(this, "Add starting example");
            addStarting.setOnClickListener(view -> showWeeklySampleEditor(0));
            communication.addView(addStarting);
        }
        communication.addView(Ui.text(this,
                "The report describes changes that happened during the leucovorin trial. It cannot prove what caused them.",
                12, Ui.GREEN));
        page.addView(communication);

        page.addView(Ui.section(this, "Milestone reports"));
        page.addView(reportCard("6", "Week 6 report", "First 42 days of ratings, medication and notes", 42));
        page.addView(reportCard("12", "Week 12 report", "Full 84-day trial summary", 84));

        Button current = Ui.secondaryButton(this, "Preview current PDF now");
        current.setOnClickListener(view -> shareReport(Math.max(1, store.currentDay()), "Current progress report"));
        page.addView(current);

        LinearLayout included = Ui.card(this);
        included.addView(Ui.heading(this, "What’s included"));
        included.addView(Ui.text(this,
                "✓ Medication and dose history\n✓ Daily communication, telling-about-her-day and response trends\n"
                        + "✓ Sleep, bowel and possible side-effect log\n✓ Five-question Weekly Talk Checks with automatic scores\n"
                        + "✓ Teacher, Speech and OT notes labeled by source\n✓ Structured examples, prompts and caregiver notes\n"
                        + "✓ Observed, health-only and away days kept separate\n✓ Video index only — video files are never embedded",
                14, Ui.MUTED));
        page.addView(included);

        TextView notice = Ui.text(this,
                "Improvement may coincide with the leucovorin trial, but this family tracker cannot establish cause. Medication decisions belong with Elle’s prescriber.",
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
        profileCard.addView(cardHeader("Rx", "Trial and dosage details", "Used throughout daily logs and doctor reports"));
        EditText child = Ui.input(this, "Child’s name"); child.setText(profile.childName);
        Spinner frequency = frequencySpinner(profile.dosesPerDay);
        EditText morningDose = Ui.decimalInput(this, "First dose amount (mg)"); morningDose.setText(profile.morningDose);
        EditText eveningDose = Ui.decimalInput(this, "Second dose amount (mg)"); eveningDose.setText(profile.eveningDose);
        EditText doseNote = Ui.input(this, "Additional dosage note (optional)"); doseNote.setText(profile.defaultDose);
        EditText doctor = Ui.input(this, "Doctor’s name (optional)"); doctor.setText(profile.doctorName);
        profileCard.addView(child);
        profileCard.addView(Ui.text(this, "LEUCOVORIN DOSE PER ADMINISTRATION", 10, Ui.MUTED));
        profileCard.addView(frequency);
        profileCard.addView(morningDose);
        profileCard.addView(eveningDose);
        profileCard.addView(doseNote);
        profileCard.addView(doctor);
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
            profile.morningDose = morningDose.getText().toString().trim();
            profile.eveningDose = eveningDose.getText().toString().trim();
            profile.doseUnit = "mg";
            profile.dosesPerDay = frequency.getSelectedItemPosition() + 1;
            if (profile.dosesPerDay == 2 && profile.eveningDose.isEmpty()) profile.eveningDose = profile.morningDose;
            profile.defaultDose = doseNote.getText().toString().trim();
            profile.doctorName = doctor.getText().toString().trim();
            profile.startDate = selectedStart[0].toString();
            store.saveProfile(profile);
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            renderTab("Settings");
        });
        profileCard.addView(save);
        page.addView(profileCard);

        page.addView(Ui.section(this, "Care schedule"));
        LinearLayout schoolCard = Ui.card(this);
        schoolCard.addView(cardHeader("School", "School and classroom", "Used to prefill teacher updates and reports"));
        EditText schoolName = Ui.input(this, "School name"); schoolName.setText(profile.schoolName);
        EditText teacherName = Ui.input(this, "Primary teacher"); teacherName.setText(profile.teacherName);
        EditText className = Ui.input(this, "Class or grade"); className.setText(profile.className);
        String[] schoolMethods = {"In person at pickup", "Teacher message or app", "Email", "Phone call", "Written daily note"};
        Spinner schoolMethod = simpleSpinner(schoolMethods, choiceIndex(schoolMethods, profile.schoolUpdateMethod));
        schoolCard.addView(schoolName);
        schoolCard.addView(teacherName);
        schoolCard.addView(className);
        schoolCard.addView(Ui.text(this, "USUAL UPDATE METHOD", 10, Ui.MUTED));
        schoolCard.addView(schoolMethod);
        page.addView(schoolCard);

        LinearLayout therapyCard = Ui.card(this);
        therapyCard.addView(cardHeader("Speech", "Speech and OT", "Speech and OT names remain separate in the report"));
        EditText therapyClinic = Ui.input(this, "Clinic or provider"); therapyClinic.setText(profile.therapyClinic);
        EditText speechTherapist = Ui.input(this, "Speech therapist"); speechTherapist.setText(profile.speechTherapist);
        EditText otTherapist = Ui.input(this, "OT therapist"); otTherapist.setText(profile.otTherapist);
        String[] therapyMethods = {"In person after session", "Clinician message or app", "Email", "Phone call", "Written session note"};
        Spinner therapyMethod = simpleSpinner(therapyMethods, choiceIndex(therapyMethods, profile.therapyUpdateMethod));
        therapyCard.addView(therapyClinic);
        therapyCard.addView(speechTherapist);
        therapyCard.addView(otTherapist);
        therapyCard.addView(Ui.text(this, "USUAL UPDATE METHOD", 10, Ui.MUTED));
        therapyCard.addView(therapyMethod);
        page.addView(therapyCard);

        LinearLayout scheduleCard = Ui.card(this);
        scheduleCard.addView(cardHeader("Schedule", "Elle’s weekly schedule", "Controls the simple steps shown on Today"));
        CheckBox mondaySchool = checkbox("Monday — school", profile.schoolMonday);
        CheckBox tuesdaySchool = checkbox("Tuesday — school + main Weekly Talk Check", profile.schoolTuesday);
        CheckBox wednesdayTheraplay = checkbox("Wednesday — Theraplay Speech + OT", profile.theraplayWednesday);
        scheduleCard.addView(mondaySchool);
        scheduleCard.addView(tuesdaySchool);
        scheduleCard.addView(wednesdayTheraplay);
        scheduleCard.addView(Ui.text(this,
                "Wednesday after Speech/OT is the backup Talk Check day. Thursday and Friday can be medication and health-only home days.",
                12, Ui.MUTED));

        CheckBox awayWeekends = checkbox("Elle is away every other weekend", profile.awayWeekendsEnabled);
        final LocalDate[] awayAnchor = {safeDate(profile.awayWeekendAnchor, nextFriday(LocalDate.now()))};
        Button awayDate = Ui.secondaryButton(this, "First away Friday: " + friendlyDate.format(awayAnchor[0]));
        awayDate.setOnClickListener(view -> pickDate(awayAnchor[0], date -> {
            awayAnchor[0] = date;
            awayDate.setText("First away Friday: " + friendlyDate.format(date));
        }));
        scheduleCard.addView(awayWeekends);
        scheduleCard.addView(awayDate);
        scheduleCard.addView(Ui.text(this,
                "Scheduled Friday–Sunday away days pause the full check-in. Unknown information stays unknown—it is never scored as zero or marked as a missed dose.",
                12, Ui.PURPLE));
        Button saveSchedule = Ui.secondaryButton(this, "Save care schedule");
        saveSchedule.setOnClickListener(view -> {
            profile.schoolMonday = mondaySchool.isChecked();
            profile.schoolTuesday = tuesdaySchool.isChecked();
            profile.theraplayWednesday = wednesdayTheraplay.isChecked();
            profile.weeklySampleDay = DayOfWeek.TUESDAY.getValue();
            profile.weeklyBackupDay = DayOfWeek.WEDNESDAY.getValue();
            profile.schoolName = schoolName.getText().toString().trim();
            profile.teacherName = teacherName.getText().toString().trim();
            profile.className = className.getText().toString().trim();
            profile.schoolUpdateMethod = String.valueOf(schoolMethod.getSelectedItem());
            profile.therapyClinic = therapyClinic.getText().toString().trim();
            profile.speechTherapist = speechTherapist.getText().toString().trim();
            profile.otTherapist = otTherapist.getText().toString().trim();
            profile.therapyUpdateMethod = String.valueOf(therapyMethod.getSelectedItem());
            profile.awayWeekendsEnabled = awayWeekends.isChecked();
            profile.awayWeekendAnchor = awayAnchor[0].toString();
            profile.awayWeekendLabel = "Away with grandmother";
            store.saveProfile(profile);
            Toast.makeText(this, "Care schedule saved", Toast.LENGTH_SHORT).show();
            renderTab("Settings");
        });
        scheduleCard.addView(saveSchedule);
        page.addView(scheduleCard);

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

    private void startVoiceTranscription(EditText target) {
        pendingVoiceInput = target;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the school or therapy update");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            startActivityForResult(intent, VOICE_TRANSCRIPTION_REQUEST);
        } catch (ActivityNotFoundException exception) {
            pendingVoiceInput = null;
            Toast.makeText(this, "Voice-to-text is not available on this phone", Toast.LENGTH_LONG).show();
        }
    }

    private void startVideoCapture(TrackerStore.WeeklySample sample, TextView status) {
        try {
            File directory = new File(getFilesDir(), "videos");
            if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create video folder");
            pendingVideoFile = new File(directory, "week-" + sample.weekNumber + "-" + System.currentTimeMillis() + ".mp4");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", pendingVideoFile);
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 120);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) throw new Exception("No camera app is available");
            pendingVideoSample = sample;
            pendingVideoStatus = status;
            startActivityForResult(intent, VIDEO_CAPTURE_REQUEST);
        } catch (Exception exception) {
            pendingVideoFile = null;
            pendingVideoSample = null;
            pendingVideoStatus = null;
            Toast.makeText(this, "Could not open the camera: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean weeklyVideoExists(TrackerStore.WeeklySample sample) {
        return sample.videoFileName != null && !sample.videoFileName.isEmpty()
                && new File(new File(getFilesDir(), "videos"), sample.videoFileName).exists();
    }

    private void openWeeklyVideo(TrackerStore.WeeklySample sample) {
        try {
            File video = new File(new File(getFilesDir(), "videos"), sample.videoFileName);
            if (!video.exists()) throw new Exception("Video is not stored on this phone");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", video);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                        Toast.makeText(this, "Synced " + result.records + " records securely", Toast.LENGTH_LONG).show();
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
        if (requestCode == VOICE_TRANSCRIPTION_REQUEST) {
            if (resultCode == RESULT_OK && data != null && pendingVoiceInput != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String existing = pendingVoiceInput.getText().toString().trim();
                    pendingVoiceInput.setText(existing.isEmpty() ? results.get(0) : existing + "\n" + results.get(0));
                    pendingVoiceInput.setSelection(pendingVoiceInput.getText().length());
                }
            }
            pendingVoiceInput = null;
            return;
        }
        if (requestCode == VIDEO_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && pendingVideoFile != null && pendingVideoFile.exists()
                    && pendingVideoFile.length() > 0 && pendingVideoSample != null) {
                pendingVideoSample.videoRecorded = true;
                pendingVideoSample.videoFileName = pendingVideoFile.getName();
                if (pendingVideoStatus != null) {
                    pendingVideoStatus.setText("✓ Video recorded locally on this phone");
                    pendingVideoStatus.setTextColor(Ui.GREEN);
                }
            } else if (pendingVideoFile != null && pendingVideoFile.exists()) {
                pendingVideoFile.delete();
            }
            pendingVideoFile = null;
            pendingVideoSample = null;
            pendingVideoStatus = null;
            return;
        }
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

    private int choiceIndex(String[] choices, String value) {
        if (value != null) {
            for (int index = 0; index < choices.length; index++) {
                if (choices[index].equals(value)) return index;
            }
        }
        return 0;
    }

    private LocalDate nextFriday(LocalDate date) {
        LocalDate candidate = date;
        while (candidate.getDayOfWeek() != DayOfWeek.FRIDAY) candidate = candidate.plusDays(1);
        return candidate;
    }

    private LocalDate safeDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String providerForSource(TrackerStore.Profile profile, String source) {
        if (source.startsWith("Teacher")) {
            String person = profile.teacherName.isEmpty() ? "Teacher not added yet" : profile.teacherName;
            String classroom = profile.className.isEmpty() ? "" : " • " + profile.className;
            return person + classroom + " • " + profile.schoolUpdateMethod;
        }
        String clinic = profile.therapyClinic.isEmpty() ? "Theraplay" : profile.therapyClinic;
        if (source.contains("Speech + OT")) {
            String speech = profile.speechTherapist.isEmpty() ? "Speech provider not added" : profile.speechTherapist;
            String ot = profile.otTherapist.isEmpty() ? "OT provider not added" : profile.otTherapist;
            return clinic + " • " + speech + " + " + ot + " • " + profile.therapyUpdateMethod;
        }
        if (source.contains("Speech")) {
            String speech = profile.speechTherapist.isEmpty() ? "Speech provider not added" : profile.speechTherapist;
            return clinic + " • " + speech + " • " + profile.therapyUpdateMethod;
        }
        if (source.contains("OT")) {
            String ot = profile.otTherapist.isEmpty() ? "OT provider not added" : profile.otTherapist;
            return clinic + " • " + ot + " • " + profile.therapyUpdateMethod;
        }
        return clinic + " • " + profile.therapyUpdateMethod;
    }

    private String trimFloat(float value) {
        if (Math.abs(value - Math.round(value)) < .001f) return String.valueOf(Math.round(value));
        return String.format(Locale.US, "%.1f", value);
    }
}
