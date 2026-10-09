package com.ellesjourney.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ReportGenerator {
    private static final int PAGE_WIDTH = 612;
    private static final int PAGE_HEIGHT = 792;
    private static final float MARGIN = 48;
    private static final DateTimeFormatter FRIENDLY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private ReportGenerator() {}

    static File create(Context context, TrackerStore store, int dayLimit, String label) throws Exception {
        TrackerStore.Profile profile = store.getProfile();
        LocalDate start = LocalDate.parse(profile.startDate);
        LocalDate cutoff = start.plusDays(Math.max(0, dayLimit - 1));
        List<TrackerStore.DailyEntry> included = new ArrayList<>();
        for (TrackerStore.DailyEntry entry : store.getEntries()) {
            LocalDate date = LocalDate.parse(entry.date);
            if (!date.isBefore(start) && !date.isAfter(cutoff)) included.add(entry);
        }

        PdfDocument document = new PdfDocument();
        drawSummaryPage(document, profile, included, start, cutoff, label, dayLimit);
        drawDailyPages(document, profile, included, label);
        File directory = new File(context.getFilesDir(), "reports");
        if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create reports folder");
        String safeLabel = label.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        File file = new File(directory, "elle-" + safeLabel + ".pdf");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            document.writeTo(stream);
        } finally {
            document.close();
        }
        return file;
    }

    private static void drawSummaryPage(PdfDocument document, TrackerStore.Profile profile,
                                        List<TrackerStore.DailyEntry> entries, LocalDate start,
                                        LocalDate cutoff, String label, int dayLimit) {
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create());
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.rgb(247, 245, 239));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.rgb(112, 87, 199));
        canvas.drawRoundRect(MARGIN, 38, PAGE_WIDTH - MARGIN, 142, 18, 18, paint);
        drawText(canvas, paint, profile.childName + " — " + label, MARGIN + 22, 76, 23, Color.WHITE, true);
        drawText(canvas, paint, "Leucovorin observation report", MARGIN + 22, 103, 13, 0xE6FFFFFF, false);
        drawText(canvas, paint, FRIENDLY.format(start) + " through " + FRIENDLY.format(cutoff),
                MARGIN + 22, 126, 11, 0xD9FFFFFF, false);

        float y = 174;
        drawText(canvas, paint, "TRIAL OVERVIEW", MARGIN, y, 10, Ui.MUTED, true);
        y += 17;
        int doses = 0;
        int sideEffectDays = 0;
        float sleepTotal = 0;
        float responseTotal = 0;
        for (TrackerStore.DailyEntry entry : entries) {
            doses += entry.dosesTakenCount();
            if (entry.hasSideEffects()) sideEffectDays++;
            sleepTotal += entry.sleepHours;
            responseTotal += average(entry);
        }
        String[] values = {
                entries.size() + "/" + dayLimit,
                entries.isEmpty() ? "—" : oneDecimal(responseTotal / entries.size()) + "/5",
                entries.isEmpty() ? "—" : oneDecimal(sleepTotal / entries.size()) + "h",
                String.valueOf(sideEffectDays)
        };
        String[] labels = {"Check-ins", "Avg response", "Avg sleep", "Effect days"};
        float cardGap = 9;
        float cardWidth = (PAGE_WIDTH - (MARGIN * 2) - (cardGap * 3)) / 4;
        for (int index = 0; index < 4; index++) {
            float left = MARGIN + index * (cardWidth + cardGap);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(left, y, left + cardWidth, y + 70, 12, 12, paint);
            drawText(canvas, paint, values[index], left + 11, y + 31, 18, Ui.INK, true);
            drawText(canvas, paint, labels[index], left + 11, y + 53, 9, Ui.MUTED, false);
        }

        y += 101;
        drawText(canvas, paint, "RESPONSE AVERAGES", MARGIN, y, 10, Ui.MUTED, true);
        y += 18;
        String[] metricNames = {"Communication", "Engagement", "Focus", "Mood", "Appetite"};
        float[] metricValues = metricAverages(entries);
        for (int index = 0; index < metricNames.length; index++) {
            drawText(canvas, paint, metricNames[index], MARGIN, y + 11, 11, Ui.INK, true);
            paint.setColor(Color.rgb(232, 229, 239));
            canvas.drawRoundRect(MARGIN + 100, y, PAGE_WIDTH - MARGIN - 38, y + 13, 7, 7, paint);
            if (!entries.isEmpty()) {
                paint.setColor(Color.rgb(112, 87, 199));
                float width = (PAGE_WIDTH - (MARGIN * 2) - 138) * (metricValues[index] / 5f);
                canvas.drawRoundRect(MARGIN + 100, y, MARGIN + 100 + width, y + 13, 7, 7, paint);
                drawText(canvas, paint, oneDecimal(metricValues[index]), PAGE_WIDTH - MARGIN - 27, y + 11, 10, Ui.INK, true);
            } else {
                drawText(canvas, paint, "—", PAGE_WIDTH - MARGIN - 23, y + 11, 10, Ui.MUTED, true);
            }
            y += 28;
        }

        y += 6;
        drawText(canvas, paint, "OVERALL RESPONSE TREND", MARGIN, y, 10, Ui.MUTED, true);
        y += 13;
        drawTrend(canvas, paint, entries, MARGIN, y, PAGE_WIDTH - MARGIN, y + 120);
        y += 145;

        drawText(canvas, paint, "POSSIBLE SIDE EFFECTS / CHANGES", MARGIN, y, 10, Ui.MUTED, true);
        y += 17;
        int[] effectCounts = effectCounts(entries);
        String[] effectNames = {"Sleep change", "Tummy upset", "Headache", "Irritability", "Other"};
        StringBuilder effects = new StringBuilder();
        for (int index = 0; index < effectNames.length; index++) {
            if (index > 0) effects.append("   •   ");
            effects.append(effectNames[index]).append(": ").append(effectCounts[index]);
        }
        drawWrapped(canvas, paint, effects.toString(), MARGIN, y, PAGE_WIDTH - MARGIN, 11, Ui.INK, 16, false);

        y += 45;
        String doseText = prescribedDoseSummary(profile);
        drawText(canvas, paint, "PRESCRIBED DOSE", MARGIN, y, 10, Ui.MUTED, true);
        drawWrapped(canvas, paint, doseText + "  •  Marked taken on " + doses + " logged days",
                MARGIN, y + 18, PAGE_WIDTH - MARGIN, 11, Ui.INK, 16, false);

        drawText(canvas, paint,
                "Caregiver observations only — not medical advice. Review medication decisions with the prescriber.",
                MARGIN, PAGE_HEIGHT - 34, 8, Ui.MUTED, false);
        document.finishPage(page);
    }

    private static void drawDailyPages(PdfDocument document, TrackerStore.Profile profile,
                                       List<TrackerStore.DailyEntry> entries, String label) {
        int pageNumber = 2;
        if (entries.isEmpty()) {
            PdfDocument.Page page = newPage(document, pageNumber);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            drawText(canvas, paint, "Daily log", MARGIN, 62, 22, Ui.INK, true);
            drawText(canvas, paint, "No check-ins were recorded in this report period.", MARGIN, 98, 12, Ui.MUTED, false);
            footer(canvas, paint, profile.childName, label, pageNumber);
            document.finishPage(page);
            return;
        }

        PdfDocument.Page page = null;
        Canvas canvas = null;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = PAGE_HEIGHT;
        for (TrackerStore.DailyEntry entry : entries) {
            float needed = entry.note.isEmpty() ? 78 : 112;
            if (page == null || y + needed > PAGE_HEIGHT - 55) {
                if (page != null) {
                    footer(canvas, paint, profile.childName, label, pageNumber - 1);
                    document.finishPage(page);
                }
                page = newPage(document, pageNumber++);
                canvas = page.getCanvas();
                drawText(canvas, paint, "Daily observation log", MARGIN, 58, 21, Ui.INK, true);
                drawText(canvas, paint, profile.childName + " • " + label, MARGIN, 80, 10, Ui.MUTED, false);
                y = 103;
            }

            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + needed - 8, 10, 10, paint);
            drawText(canvas, paint, FRIENDLY.format(LocalDate.parse(entry.date)), MARGIN + 13, y + 22, 12, Ui.INK, true);
            String scores = "Communication " + entry.communication + "  •  Engagement " + entry.engagement
                    + "  •  Focus " + entry.focus + "  •  Mood " + entry.mood + "  •  Appetite " + entry.appetite;
            drawWrapped(canvas, paint, scores, MARGIN + 13, y + 41, PAGE_WIDTH - MARGIN - 13, 9, Ui.MUTED, 13, false);
            String medication = entry.hasAnyDose() ? entry.dosesTakenCount() + " dose(s) recorded"
                    + (entry.dose.isEmpty() ? "" : ": " + entry.dose) : "No doses marked taken";
            drawText(canvas, paint, medication + "  •  Sleep " + oneDecimal(entry.sleepHours) + "h",
                    MARGIN + 13, y + 67, 9, entry.hasAnyDose() ? Ui.GREEN : Ui.MUTED, true);
            if (!entry.note.isEmpty()) {
                drawWrapped(canvas, paint, "Note: " + entry.note, MARGIN + 13, y + 88,
                        PAGE_WIDTH - MARGIN - 13, 9, Ui.INK, 13, false);
            }
            y += needed;
        }
        if (page != null) {
            footer(canvas, paint, profile.childName, label, pageNumber - 1);
            document.finishPage(page);
        }
    }

    private static PdfDocument.Page newPage(PdfDocument document, int pageNumber) {
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        page.getCanvas().drawColor(Color.rgb(247, 245, 239));
        return page;
    }

    private static void drawTrend(Canvas canvas, Paint paint, List<TrackerStore.DailyEntry> entries,
                                  float left, float top, float right, float bottom) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(221, 219, 225));
        for (int score = 0; score <= 5; score++) {
            float y = bottom - (score / 5f) * (bottom - top);
            canvas.drawLine(left + 22, y, right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, paint, String.valueOf(score), left, y + 3, 8, Ui.MUTED, false);
            paint.setStyle(Paint.Style.STROKE);
        }
        if (entries.size() > 1) {
            Path path = new Path();
            for (int index = 0; index < entries.size(); index++) {
                float x = left + 22 + (index / (float) (entries.size() - 1)) * (right - left - 22);
                float y = bottom - (average(entries.get(index)) / 5f) * (bottom - top);
                if (index == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(Ui.PURPLE);
            paint.setStrokeWidth(3);
            canvas.drawPath(path, paint);
        } else if (entries.size() == 1) {
            paint.setColor(Ui.PURPLE);
            paint.setStyle(Paint.Style.FILL);
            float y = bottom - (average(entries.get(0)) / 5f) * (bottom - top);
            canvas.drawCircle((left + right) / 2, y, 4, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, paint, entries.isEmpty() ? "No data yet" : "Each point is one daily check-in",
                left + 22, bottom + 17, 8, Ui.MUTED, false);
    }

    private static float average(TrackerStore.DailyEntry entry) {
        return (entry.communication + entry.engagement + entry.focus + entry.mood + entry.appetite) / 5f;
    }

    private static float[] metricAverages(List<TrackerStore.DailyEntry> entries) {
        float[] values = new float[5];
        if (entries.isEmpty()) return values;
        for (TrackerStore.DailyEntry entry : entries) {
            values[0] += entry.communication;
            values[1] += entry.engagement;
            values[2] += entry.focus;
            values[3] += entry.mood;
            values[4] += entry.appetite;
        }
        for (int index = 0; index < values.length; index++) values[index] /= entries.size();
        return values;
    }

    private static int[] effectCounts(List<TrackerStore.DailyEntry> entries) {
        int[] counts = new int[5];
        for (TrackerStore.DailyEntry entry : entries) {
            if (entry.sleepChange) counts[0]++;
            if (entry.tummyUpset) counts[1]++;
            if (entry.headache) counts[2]++;
            if (entry.irritability) counts[3]++;
            if (entry.otherSideEffect) counts[4]++;
        }
        return counts;
    }

    private static void footer(Canvas canvas, Paint paint, String childName, String label, int pageNumber) {
        drawText(canvas, paint, childName + " • " + label, MARGIN, PAGE_HEIGHT - 28, 8, Ui.MUTED, false);
        drawText(canvas, paint, "Page " + pageNumber, PAGE_WIDTH - MARGIN - 34, PAGE_HEIGHT - 28, 8, Ui.MUTED, false);
    }

    private static void drawText(Canvas canvas, Paint paint, String text, float x, float y,
                                 float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(text, x, y, paint);
    }

    private static float drawWrapped(Canvas canvas, Paint paint, String text, float left, float top,
                                     float right, float size, int color, float lineHeight, boolean bold) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setColor(color);
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float y = top;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > right - left && line.length() > 0) {
                canvas.drawText(line.toString(), left, y, paint);
                line = new StringBuilder(word);
                y += lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) canvas.drawText(line.toString(), left, y, paint);
        return y;
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String prescribedDoseSummary(TrackerStore.Profile profile) {
        String unit = profile.doseUnit == null || profile.doseUnit.isEmpty() ? "mg" : profile.doseUnit;
        if (profile.morningDose != null && !profile.morningDose.isEmpty()) {
            if (profile.dosesPerDay < 2) {
                return profile.morningDose + " " + unit + " once daily";
            }
            if (profile.morningDose.equals(profile.eveningDose)) {
                return profile.morningDose + " " + unit + " twice daily";
            }
            return profile.morningDose + " " + unit + " first dose + " + profile.eveningDose + " " + unit + " second dose";
        }
        return profile.defaultDose == null || profile.defaultDose.isEmpty() ? "No prescribed dose entered" : profile.defaultDose;
    }
}
