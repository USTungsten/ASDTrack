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
    private static final String[] WEEKLY_PROMPTS = {
            "Tell me about your day.",
            "What is one thing you did today?",
            "Who did you spend time with, and what did you do?",
            "What was your favorite part? Why?",
            "Is there anything else you want to tell me?"
    };

    private ReportGenerator() {}

    static File create(Context context, TrackerStore store, int dayLimit, String label) throws Exception {
        TrackerStore.Profile profile = store.getProfile();
        LocalDate start = LocalDate.parse(profile.startDate);
        LocalDate cutoff = start.plusDays(Math.max(0, dayLimit - 1));
        List<TrackerStore.DailyEntry> dailyEntries = new ArrayList<>();
        List<TrackerStore.CareUpdate> careUpdates = new ArrayList<>();
        List<TrackerStore.WeeklySample> weeklySamples = new ArrayList<>();

        for (TrackerStore.DailyEntry entry : store.getEntries()) {
            if (inRange(entry.date, start, cutoff)) dailyEntries.add(entry);
        }
        for (TrackerStore.CareUpdate update : store.getCareUpdates()) {
            if (inRange(update.date, start, cutoff)) careUpdates.add(update);
        }
        for (TrackerStore.WeeklySample sample : store.getWeeklySamples()) {
            if (sample.weekNumber == 0 || inRange(sample.date, start, cutoff)) weeklySamples.add(sample);
        }

        PdfDocument document = new PdfDocument();
        drawSummaryPage(document, profile, dailyEntries, weeklySamples, careUpdates, start, cutoff, label, dayLimit);
        int nextPage = drawDailyPages(document, profile, dailyEntries, label, 2);
        nextPage = drawWeeklyPages(document, profile, weeklySamples, label, nextPage);
        drawCarePages(document, profile, careUpdates, label, nextPage);

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

    private static boolean inRange(String dateValue, LocalDate start, LocalDate cutoff) {
        try {
            LocalDate date = LocalDate.parse(dateValue);
            return !date.isBefore(start) && !date.isAfter(cutoff);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void drawSummaryPage(PdfDocument document, TrackerStore.Profile profile,
                                        List<TrackerStore.DailyEntry> entries,
                                        List<TrackerStore.WeeklySample> weeklySamples,
                                        List<TrackerStore.CareUpdate> careUpdates,
                                        LocalDate start, LocalDate cutoff, String label, int dayLimit) {
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create());
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.rgb(244, 246, 249));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Ui.PURPLE);
        canvas.drawRoundRect(MARGIN, 36, PAGE_WIDTH - MARGIN, 140, 18, 18, paint);
        drawText(canvas, paint, profile.childName + " — " + label, MARGIN + 22, 74, 23, Color.WHITE, true);
        drawText(canvas, paint, "Leucovorin caregiver observation report", MARGIN + 22, 101, 13, 0xE6FFFFFF, false);
        drawText(canvas, paint, FRIENDLY.format(start) + " through " + FRIENDLY.format(cutoff),
                MARGIN + 22, 124, 11, 0xD9FFFFFF, false);

        int doses = 0;
        int sideEffectDays = 0;
        int bowelLoggedDays = 0;
        int bowelConcernDays = 0;
        int observedDays = 0;
        int awayDays = 0;
        int healthDays = 0;
        float sleepTotal = 0;
        for (TrackerStore.DailyEntry entry : entries) {
            doses += entry.dosesTakenCount();
            if (entry.hasRatings) observedDays++;
            if (entry.isNotObserved()) awayDays++;
            if (entry.hasSideEffects()) sideEffectDays++;
            if (entry.hasBowelData()) bowelLoggedDays++;
            if (entry.bowelPain || entry.bowelUrgency || entry.bowelConsistency <= 2 && entry.bowelConsistency > 0
                    || entry.bowelConsistency >= 6) bowelConcernDays++;
            if (entry.healthObserved) {
                sleepTotal += entry.sleepHours;
                healthDays++;
            }
        }

        float y = 170;
        drawText(canvas, paint, "TRIAL OVERVIEW", MARGIN, y, 10, Ui.MUTED, true);
        y += 17;
        String[] values = {
                String.valueOf(observedDays),
                String.valueOf(doses),
                String.valueOf(weeklySamples.size()),
                awayDays + " away"
        };
        String[] labels = {"Observed days", "Doses logged", "Talk checks", "Not scored"};
        float cardGap = 9;
        float cardWidth = (PAGE_WIDTH - (MARGIN * 2) - (cardGap * 3)) / 4;
        for (int index = 0; index < values.length; index++) {
            float left = MARGIN + index * (cardWidth + cardGap);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(left, y, left + cardWidth, y + 66, 12, 12, paint);
            drawText(canvas, paint, values[index], left + 11, y + 29, 18, Ui.INK, true);
            drawText(canvas, paint, labels[index], left + 11, y + 50, 8, Ui.MUTED, false);
        }

        y += 91;
        drawText(canvas, paint, "DAILY RATING AVERAGES • 0–5", MARGIN, y, 10, Ui.MUTED, true);
        y += 17;
        String[] metricNames = {"Communication", "Tells about day", "Social connection", "Focus", "Mood/regulation", "Appetite"};
        float[] metricValues = metricAverages(entries);
        for (int index = 0; index < metricNames.length; index++) {
            drawText(canvas, paint, metricNames[index], MARGIN, y + 10, 10, Ui.INK, true);
            paint.setColor(Color.rgb(232, 229, 239));
            canvas.drawRoundRect(MARGIN + 112, y, PAGE_WIDTH - MARGIN - 38, y + 12, 6, 6, paint);
            if (metricValues[index] >= 0) {
                paint.setColor(Ui.PURPLE);
                float width = (PAGE_WIDTH - (MARGIN * 2) - 150) * (metricValues[index] / 5f);
                canvas.drawRoundRect(MARGIN + 112, y, MARGIN + 112 + width, y + 12, 6, 6, paint);
                drawText(canvas, paint, oneDecimal(metricValues[index]), PAGE_WIDTH - MARGIN - 27, y + 10, 9, Ui.INK, true);
            } else {
                drawText(canvas, paint, "—", PAGE_WIDTH - MARGIN - 23, y + 10, 9, Ui.MUTED, true);
            }
            y += 24;
        }

        y += 2;
        drawText(canvas, paint, "OVERALL DAILY RESPONSE TREND", MARGIN, y, 10, Ui.MUTED, true);
        y += 13;
        drawTrend(canvas, paint, entries, MARGIN, y, PAGE_WIDTH - MARGIN, y + 82);
        y += 108;

        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76, 12, 12, paint);
        drawText(canvas, paint, "TOLERABILITY & CONTEXT", MARGIN + 13, y + 21, 10, Ui.MUTED, true);
        String context = "Average sleep " + (healthDays == 0 ? "—" : oneDecimal(sleepTotal / healthDays) + "h")
                + "   •   Side-effect/change days " + sideEffectDays
                + "   •   Bowel logged " + bowelLoggedDays + " days"
                + "   •   Bowel concern days " + bowelConcernDays;
        drawWrapped(canvas, paint, context, MARGIN + 13, y + 43, PAGE_WIDTH - MARGIN - 13,
                10, Ui.INK, 14, false);

        y += 94;
        drawText(canvas, paint, "PRESCRIBED DOSE", MARGIN, y, 10, Ui.MUTED, true);
        drawWrapped(canvas, paint, prescribedDoseSummary(profile), MARGIN, y + 18,
                PAGE_WIDTH - MARGIN, 11, Ui.INK, 16, false);

        TrackerStore.WeeklySample starting = null;
        TrackerStore.WeeklySample latest = null;
        for (TrackerStore.WeeklySample sample : weeklySamples) {
            if (!sample.hasPromptData()) continue;
            if (sample.weekNumber == 0) starting = sample;
            else latest = sample;
        }
        if (starting != null || latest != null) {
            y += 48;
            drawText(canvas, paint, "STANDARDIZED TALK CHECK", MARGIN, y, 10, Ui.MUTED, true);
            String comparison = starting != null && latest != null
                    ? "Starting " + oneDecimal(starting.promptScoreAverage()) + "/5 → Week " + latest.weekNumber
                    + " " + oneDecimal(latest.promptScoreAverage()) + "/5"
                    : starting != null ? "Starting example " + oneDecimal(starting.promptScoreAverage()) + "/5"
                    : "Latest Talk Check " + oneDecimal(latest.promptScoreAverage()) + "/5";
            drawWrapped(canvas, paint, comparison, MARGIN, y + 18, PAGE_WIDTH - MARGIN,
                    11, Ui.INK, 15, true);
        }

        drawText(canvas, paint,
                "Observed changes may coincide with the trial; this caregiver tracker is not a validated scale or proof of cause.",
                MARGIN, PAGE_HEIGHT - 34, 8, Ui.MUTED, false);
        document.finishPage(page);
    }

    private static int drawDailyPages(PdfDocument document, TrackerStore.Profile profile,
                                      List<TrackerStore.DailyEntry> entries, String label, int pageNumber) {
        if (entries.isEmpty()) return pageNumber;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = PAGE_HEIGHT;
        int currentPage = pageNumber;
        for (TrackerStore.DailyEntry entry : entries) {
            List<String> detailLines = dailyDetailLines(entry);
            float needed = 95;
            for (String line : detailLines) needed += wrappedHeight(paint, line, PAGE_WIDTH - (MARGIN * 2) - 26, 9, 13) + 4;
            needed = Math.min(needed, 430);
            if (page == null || y + needed > PAGE_HEIGHT - 54) {
                if (page != null) {
                    footer(canvas, paint, profile.childName, label, currentPage - 1);
                    document.finishPage(page);
                }
                page = newPage(document, currentPage++);
                canvas = page.getCanvas();
                drawText(canvas, paint, "Daily observation log", MARGIN, 56, 21, Ui.INK, true);
                drawText(canvas, paint, "Ratings, medication, bowel context and concrete examples", MARGIN, 79, 10, Ui.MUTED, false);
                y = 101;
            }

            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + needed - 8, 10, 10, paint);
            drawText(canvas, paint, FRIENDLY.format(LocalDate.parse(entry.date)), MARGIN + 13, y + 22, 12, Ui.INK, true);
            String scores = entry.hasRatings
                    ? "Communication " + entry.communication
                    + (entry.tellsAboutDay >= 0 ? "  •  Tells day " + entry.tellsAboutDay : "")
                    + "  •  Connection " + entry.engagement + "  •  Focus " + entry.focus
                    + "  •  Mood " + entry.mood + "  •  Appetite " + entry.appetite
                    : "No response ratings — " + entry.observationStatus;
            float lineY = drawWrapped(canvas, paint, scores, MARGIN + 13, y + 41,
                    PAGE_WIDTH - MARGIN - 13, 9, Ui.MUTED, 13, false) + 18;
            String medication = entry.hasAnyDose() ? entry.dosesTakenCount() + " dose(s): " + entry.dose
                    : "No dose recorded";
            medication += " • " + entry.doseConfirmation;
            String health = entry.healthObserved ? "  •  Sleep " + oneDecimal(entry.sleepHours) + "h" : "  •  Health not observed";
            lineY = drawWrapped(canvas, paint, medication + health,
                    MARGIN + 13, lineY, PAGE_WIDTH - MARGIN - 13, 9,
                    entry.hasAnyDose() ? Ui.GREEN : Ui.MUTED, 13, true) + 18;
            for (String detail : detailLines) {
                lineY = drawWrapped(canvas, paint, detail, MARGIN + 13, lineY,
                        PAGE_WIDTH - MARGIN - 13, 9, Ui.INK, 13, false) + 7;
                if (lineY > y + needed - 16) break;
            }
            y += needed;
        }
        if (page != null) {
            footer(canvas, paint, profile.childName, label, currentPage - 1);
            document.finishPage(page);
        }
        return currentPage;
    }

    private static List<String> dailyDetailLines(TrackerStore.DailyEntry entry) {
        List<String> lines = new ArrayList<>();
        if (!entry.hasRatings) lines.add("Observation status: " + entry.observationStatus);
        if (entry.hasBowelData()) {
            String count = entry.bowelMovementCount < 0 ? "count not entered" : entry.bowelMovementCount + " movement(s)";
            String consistency = entry.bowelConsistency > 0 ? " • consistency " + entry.bowelConsistency : "";
            String concerns = entry.bowelPain ? " • pain/straining" : "";
            if (entry.bowelUrgency) concerns += " • urgency/accident";
            lines.add("Bowel: " + count + consistency + concerns);
        }
        if (entry.hasSideEffects()) lines.add("Possible changes: " + effectSummary(entry));
        if (!entry.momentContext.isEmpty()) lines.add("Moment: " + clipped(entry.momentContext));
        if (!entry.exactWords.isEmpty()) lines.add("Exact example: “" + clipped(entry.exactWords) + "”");
        if (!entry.communicationMode.isEmpty()) lines.add("Communication form: " + entry.communicationMode);
        if (!entry.caregiverMeaning.isEmpty()) lines.add("Caregiver interpretation: " + clipped(entry.caregiverMeaning));
        if (entry.eventConfirmed) lines.add("Event could be confirmed by another observer");
        if (!entry.promptsUsed.isEmpty()) lines.add("Prompts/support: " + clipped(entry.promptsUsed));
        if (!entry.observer.isEmpty()) lines.add("Observed by: " + clipped(entry.observer));
        if (!entry.factors.isEmpty()) lines.add("Context that may have affected the day: " + clipped(entry.factors));
        if (!entry.note.isEmpty()) lines.add("Other note: " + clipped(entry.note));
        return lines;
    }

    private static int drawWeeklyPages(PdfDocument document, TrackerStore.Profile profile,
                                       List<TrackerStore.WeeklySample> samples, String label, int pageNumber) {
        if (samples.isEmpty()) return pageNumber;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = PAGE_HEIGHT;
        int currentPage = pageNumber;
        for (TrackerStore.WeeklySample sample : samples) {
            List<String> lines = new ArrayList<>();
            if (sample.hasPromptData()) {
                lines.add("Automatic average: " + oneDecimal(sample.promptScoreAverage()) + "/5"
                        + " • " + sample.recordedPromptCount() + "/5 questions recorded"
                        + " • " + sample.totalPromptDetails() + " details"
                        + " • " + sample.conversationTurns + " back-and-forth turns"
                        + " • " + sample.setting);
                for (int index = 0; index < sample.promptResponses.size(); index++) {
                    TrackerStore.PromptResponse response = sample.promptResponses.get(index);
                    if (!response.recorded) continue;
                    String exact = response.exactWords.isEmpty() ? "no exact words saved" : "“" + clipped(response.exactWords) + "”";
                    lines.add("Q" + (index + 1) + " " + response.score() + "/5 • " + WEEKLY_PROMPTS[index]
                            + " • " + exact + " • " + response.communicationMode);
                    if (!response.caregiverMeaning.isEmpty()) {
                        lines.add("Caregiver thought Elle meant: " + clipped(response.caregiverMeaning)
                                + (response.eventConfirmed ? " • event confirmed" : ""));
                    }
                }
                lines.add("Extra help: " + emptyAsDash(sample.extraSupport));
            } else {
                lines.add("Earlier weekly ratings: starts " + sample.startsCommunication + " • turns " + sample.backAndForth
                        + " • small talk " + sample.smallTalk + " • tells day " + sample.tellsAboutDay
                        + " • open questions " + sample.openQuestions + " • follow-up " + sample.followUpQuestions);
                lines.add("Observed counts: " + sample.detailCount + " details • " + sample.conversationTurns + " turns");
                lines.add("Extra support: " + emptyAsDash(sample.extraSupport));
            }
            if (!sample.interruptionReason.isEmpty()) lines.add("Stopped early: " + sample.interruptionReason);
            if (!sample.note.isEmpty()) lines.add("Note: " + clipped(sample.note));
            lines.add(sample.videoRecorded ? "Video marker saved; file not embedded" : "No video marker");
            float needed = 62;
            for (String line : lines) needed += wrappedHeight(paint, line, 490, 9, 13) + 7;
            needed = Math.min(needed, 620);
            if (page == null || y + needed > PAGE_HEIGHT - 54) {
                if (page != null) {
                    footer(canvas, paint, profile.childName, label, currentPage - 1);
                    document.finishPage(page);
                }
                page = newPage(document, currentPage++);
                canvas = page.getCanvas();
                drawText(canvas, paint, "Weekly Talk Checks", MARGIN, 56, 21, Ui.INK, true);
                drawText(canvas, paint, "Same five questions, 10-second wait, and automatic support-adjusted scores", MARGIN, 79, 10, Ui.MUTED, false);
                y = 101;
            }
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + needed - 8, 10, 10, paint);
            String sampleName = sample.weekNumber == 0 ? "Starting example" : "Week " + sample.weekNumber;
            drawText(canvas, paint, sampleName + " • " + FRIENDLY.format(LocalDate.parse(sample.date)),
                    MARGIN + 13, y + 22, 12, Ui.INK, true);
            float lineY = y + 44;
            for (String line : lines) {
                int color = line.startsWith("Automatic") ? Ui.PURPLE : line.startsWith("Video") ? Ui.GREEN : Ui.INK;
                lineY = drawWrapped(canvas, paint, line, MARGIN + 13, lineY,
                        PAGE_WIDTH - MARGIN - 13, 9, color, 13, line.startsWith("Automatic")) + 7;
                if (lineY > y + needed - 16) break;
            }
            y += needed;
        }
        if (page != null) {
            footer(canvas, paint, profile.childName, label, currentPage - 1);
            document.finishPage(page);
        }
        return currentPage;
    }

    private static int drawCarePages(PdfDocument document, TrackerStore.Profile profile,
                                     List<TrackerStore.CareUpdate> updates, String label, int pageNumber) {
        if (updates.isEmpty()) return pageNumber;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = PAGE_HEIGHT;
        int currentPage = pageNumber;
        for (TrackerStore.CareUpdate update : updates) {
            List<String> lines = new ArrayList<>();
            if (!update.transcript.isEmpty()) lines.add("Reported update: " + clipped(update.transcript));
            if (!update.tagSummary().isEmpty()) lines.add("Tags: " + update.tagSummary());
            if (!update.goalSkill.isEmpty()) lines.add("Goal/skill: " + clipped(update.goalSkill));
            if (!update.supportProgress.isEmpty()) lines.add("Support/progress: " + clipped(update.supportProgress));
            if (!update.homeRecommendation.isEmpty()) lines.add("Home recommendation: " + clipped(update.homeRecommendation));
            float needed = 69;
            for (String line : lines) needed += wrappedHeight(paint, line, 490, 9, 13) + 7;
            needed = Math.min(needed, 470);
            if (page == null || y + needed > PAGE_HEIGHT - 54) {
                if (page != null) {
                    footer(canvas, paint, profile.childName, label, currentPage - 1);
                    document.finishPage(page);
                }
                page = newPage(document, currentPage++);
                canvas = page.getCanvas();
                drawText(canvas, paint, "School, Speech and OT updates", MARGIN, 56, 21, Ui.INK, true);
                drawText(canvas, paint, "Each note remains labeled by its reported source", MARGIN, 79, 10, Ui.MUTED, false);
                y = 101;
            }
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + needed - 8, 10, 10, paint);
            drawText(canvas, paint, update.source + " • " + FRIENDLY.format(LocalDate.parse(update.date)),
                    MARGIN + 13, y + 22, 12, Ui.INK, true);
            String sourceLine = emptyAsDash(update.provider)
                    + (update.enteredBy.isEmpty() ? "" : "  •  entered by " + update.enteredBy);
            drawWrapped(canvas, paint, sourceLine, MARGIN + 13, y + 42,
                    PAGE_WIDTH - MARGIN - 13, 9, Ui.MUTED, 13, false);
            float lineY = y + 64;
            for (String line : lines) {
                lineY = drawWrapped(canvas, paint, line, MARGIN + 13, lineY,
                        PAGE_WIDTH - MARGIN - 13, 9, Ui.INK, 13, false) + 8;
                if (lineY > y + needed - 16) break;
            }
            y += needed;
        }
        if (page != null) {
            footer(canvas, paint, profile.childName, label, currentPage - 1);
            document.finishPage(page);
        }
        return currentPage;
    }

    private static PdfDocument.Page newPage(PdfDocument document, int pageNumber) {
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        page.getCanvas().drawColor(Color.rgb(244, 246, 249));
        return page;
    }

    private static void drawTrend(Canvas canvas, Paint paint, List<TrackerStore.DailyEntry> entries,
                                  float left, float top, float right, float bottom) {
        List<TrackerStore.DailyEntry> ratedEntries = new ArrayList<>();
        for (TrackerStore.DailyEntry entry : entries) if (entry.hasRatings) ratedEntries.add(entry);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(221, 219, 225));
        for (int score = 0; score <= 5; score++) {
            float y = bottom - (score / 5f) * (bottom - top);
            canvas.drawLine(left + 22, y, right, y, paint);
        }
        if (ratedEntries.size() > 1) {
            Path path = new Path();
            for (int index = 0; index < ratedEntries.size(); index++) {
                float x = left + 22 + (index / (float) (ratedEntries.size() - 1)) * (right - left - 22);
                float y = bottom - (average(ratedEntries.get(index)) / 5f) * (bottom - top);
                if (index == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(Ui.PURPLE);
            paint.setStrokeWidth(3);
            canvas.drawPath(path, paint);
        } else if (ratedEntries.size() == 1) {
            paint.setColor(Ui.PURPLE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle((left + right) / 2,
                    bottom - (average(ratedEntries.get(0)) / 5f) * (bottom - top), 4, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, paint, ratedEntries.isEmpty() ? "No observed response ratings yet" : "Each point is an observed daily check-in",
                left + 22, bottom + 16, 8, Ui.MUTED, false);
    }

    private static float average(TrackerStore.DailyEntry entry) {
        return (entry.communication + entry.engagement + entry.focus + entry.mood + entry.appetite) / 5f;
    }

    private static float[] metricAverages(List<TrackerStore.DailyEntry> entries) {
        float[] values = {-1, -1, -1, -1, -1, -1};
        if (entries.isEmpty()) return values;
        float[] totals = new float[6];
        int tellDayCount = 0;
        int ratedCount = 0;
        for (TrackerStore.DailyEntry entry : entries) {
            if (!entry.hasRatings) continue;
            ratedCount++;
            totals[0] += entry.communication;
            if (entry.tellsAboutDay >= 0) {
                totals[1] += entry.tellsAboutDay;
                tellDayCount++;
            }
            totals[2] += entry.engagement;
            totals[3] += entry.focus;
            totals[4] += entry.mood;
            totals[5] += entry.appetite;
        }
        if (ratedCount == 0) return values;
        values[0] = totals[0] / ratedCount;
        values[1] = tellDayCount == 0 ? -1 : totals[1] / tellDayCount;
        for (int index = 2; index < values.length; index++) values[index] = totals[index] / ratedCount;
        return values;
    }

    private static String effectSummary(TrackerStore.DailyEntry entry) {
        List<String> effects = new ArrayList<>();
        if (entry.sleepChange) effects.add("sleep change");
        if (entry.tummyUpset) effects.add("tummy upset");
        if (entry.headache) effects.add("headache");
        if (entry.irritability) effects.add("irritability");
        if (entry.otherSideEffect) effects.add("other");
        return String.join(", ", effects);
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
        String[] paragraphs = text.split("\\n", -1);
        float y = top;
        for (int paragraphIndex = 0; paragraphIndex < paragraphs.length; paragraphIndex++) {
            String[] words = paragraphs[paragraphIndex].split(" ");
            StringBuilder line = new StringBuilder();
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
            if (paragraphIndex < paragraphs.length - 1) y += lineHeight;
        }
        return y;
    }

    private static float wrappedHeight(Paint paint, String text, float width, float size, float lineHeight) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        int lines = 1;
        float current = 0;
        for (String word : text.split(" ")) {
            float wordWidth = paint.measureText(word + " ");
            if (current > 0 && current + wordWidth > width) {
                lines++;
                current = wordWidth;
            } else {
                current += wordWidth;
            }
        }
        return lines * lineHeight;
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String emptyAsDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private static String clipped(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 700 ? trimmed : trimmed.substring(0, 697) + "…";
    }

    private static String prescribedDoseSummary(TrackerStore.Profile profile) {
        String unit = profile.doseUnit == null || profile.doseUnit.isEmpty() ? "mg" : profile.doseUnit;
        if (profile.morningDose != null && !profile.morningDose.isEmpty()) {
            if (profile.dosesPerDay < 2) return profile.morningDose + " " + unit + " once daily";
            if (profile.morningDose.equals(profile.eveningDose)) return profile.morningDose + " " + unit + " twice daily";
            return profile.morningDose + " " + unit + " first dose + " + profile.eveningDose + " " + unit + " second dose";
        }
        return profile.defaultDose == null || profile.defaultDose.isEmpty() ? "No prescribed dose entered" : profile.defaultDose;
    }
}
