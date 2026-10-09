package com.ellesjourney.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class TrackerStore {
    private static final String PREFS = "elles_journey_data";
    private static final String PROFILE = "profile";
    private static final String ENTRIES = "entries";
    private static final String CARE_UPDATES = "care_updates";
    private static final String WEEKLY_SAMPLES = "weekly_samples";
    private final SharedPreferences preferences;

    TrackerStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasProfile() {
        return preferences.contains(PROFILE);
    }

    Profile getProfile() {
        Profile profile = new Profile();
        try {
            String raw = preferences.getString(PROFILE, null);
            if (raw != null) profile.read(new JSONObject(raw));
        } catch (JSONException ignored) {
        }
        return profile;
    }

    void saveProfile(Profile profile) {
        profile.updatedAt = System.currentTimeMillis();
        preferences.edit().putString(PROFILE, profile.toJson().toString()).apply();
    }

    List<DailyEntry> getEntries() {
        List<DailyEntry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(ENTRIES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                entries.add(DailyEntry.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(entries, Comparator.comparing(entry -> entry.date));
        return entries;
    }

    DailyEntry getEntry(String date) {
        for (DailyEntry entry : getEntries()) {
            if (entry.date.equals(date)) return entry;
        }
        return null;
    }

    void saveEntry(DailyEntry updated) {
        updated.updatedAt = System.currentTimeMillis();
        List<DailyEntry> entries = getEntries();
        replaceDaily(entries, updated);
        writeDaily(entries);
    }

    List<CareUpdate> getCareUpdates() {
        List<CareUpdate> updates = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(CARE_UPDATES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                updates.add(CareUpdate.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(updates, Comparator.comparing(update -> update.date));
        return updates;
    }

    List<CareUpdate> getCareUpdates(String date) {
        List<CareUpdate> matches = new ArrayList<>();
        for (CareUpdate update : getCareUpdates()) {
            if (update.date.equals(date)) matches.add(update);
        }
        return matches;
    }

    void saveCareUpdate(CareUpdate updated) {
        if (updated.id == null || updated.id.isEmpty()) updated.id = UUID.randomUUID().toString();
        updated.updatedAt = System.currentTimeMillis();
        List<CareUpdate> updates = getCareUpdates();
        replaceCareUpdate(updates, updated);
        writeCareUpdates(updates);
    }

    List<WeeklySample> getWeeklySamples() {
        List<WeeklySample> samples = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(WEEKLY_SAMPLES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                samples.add(WeeklySample.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(samples, Comparator.comparingInt(sample -> sample.weekNumber));
        return samples;
    }

    WeeklySample getWeeklySample(int weekNumber) {
        for (WeeklySample sample : getWeeklySamples()) {
            if (sample.weekNumber == weekNumber) return sample;
        }
        return null;
    }

    void saveWeeklySample(WeeklySample updated) {
        updated.updatedAt = System.currentTimeMillis();
        List<WeeklySample> samples = getWeeklySamples();
        replaceWeeklySample(samples, updated);
        writeWeeklySamples(samples);
    }

    int currentDay() {
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(getProfile().startDate), LocalDate.now()) + 1;
            return (int) Math.max(1, Math.min(84, days));
        } catch (Exception ignored) {
            return 1;
        }
    }

    int currentWeek() {
        return Math.min(12, ((currentDay() - 1) / 7) + 1);
    }

    String exportJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "elles-journey-backup-v3");
            root.put("profile", new JSONObject(preferences.getString(PROFILE, "{}")));
            root.put("entries", new JSONArray(preferences.getString(ENTRIES, "[]")));
            root.put("careUpdates", new JSONArray(preferences.getString(CARE_UPDATES, "[]")));
            root.put("weeklySamples", new JSONArray(preferences.getString(WEEKLY_SAMPLES, "[]")));
        } catch (JSONException ignored) {
        }
        return root.toString();
    }

    boolean importJson(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            String format = root.optString("format");
            if (!"elles-journey-backup-v1".equals(format)
                    && !"elles-journey-backup-v2".equals(format)
                    && !"elles-journey-backup-v3".equals(format)) return false;

            List<DailyEntry> dailyEntries = getEntries();
            JSONArray importedEntries = root.optJSONArray("entries");
            if (importedEntries != null) {
                for (int index = 0; index < importedEntries.length(); index++) {
                    DailyEntry incoming = DailyEntry.fromJson(importedEntries.getJSONObject(index));
                    DailyEntry local = findDaily(dailyEntries, incoming.date);
                    if (local == null || incoming.updatedAt >= local.updatedAt) replaceDaily(dailyEntries, incoming);
                }
            }

            List<CareUpdate> careUpdates = getCareUpdates();
            JSONArray importedCare = root.optJSONArray("careUpdates");
            if (importedCare != null) {
                for (int index = 0; index < importedCare.length(); index++) {
                    CareUpdate incoming = CareUpdate.fromJson(importedCare.getJSONObject(index));
                    CareUpdate local = findCareUpdate(careUpdates, incoming.id);
                    if (local == null || incoming.updatedAt >= local.updatedAt) replaceCareUpdate(careUpdates, incoming);
                }
            }

            List<WeeklySample> weeklySamples = getWeeklySamples();
            JSONArray importedWeekly = root.optJSONArray("weeklySamples");
            if (importedWeekly != null) {
                for (int index = 0; index < importedWeekly.length(); index++) {
                    WeeklySample incoming = WeeklySample.fromJson(importedWeekly.getJSONObject(index));
                    WeeklySample local = findWeeklySample(weeklySamples, incoming.weekNumber);
                    if (local == null || incoming.updatedAt >= local.updatedAt) replaceWeeklySample(weeklySamples, incoming);
                }
            }

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(ENTRIES, toDailyJson(dailyEntries).toString());
            editor.putString(CARE_UPDATES, toCareJson(careUpdates).toString());
            editor.putString(WEEKLY_SAMPLES, toWeeklyJson(weeklySamples).toString());

            JSONObject incomingProfile = root.optJSONObject("profile");
            if (incomingProfile != null) {
                JSONObject localProfile = new JSONObject(preferences.getString(PROFILE, "{}"));
                if (!hasProfile() || incomingProfile.optLong("updatedAt", 0) >= localProfile.optLong("updatedAt", 0)) {
                    editor.putString(PROFILE, incomingProfile.toString());
                }
            }
            editor.apply();
            return true;
        } catch (JSONException exception) {
            return false;
        }
    }

    private void writeDaily(List<DailyEntry> entries) {
        preferences.edit().putString(ENTRIES, toDailyJson(entries).toString()).apply();
    }

    private void writeCareUpdates(List<CareUpdate> updates) {
        preferences.edit().putString(CARE_UPDATES, toCareJson(updates).toString()).apply();
    }

    private void writeWeeklySamples(List<WeeklySample> samples) {
        preferences.edit().putString(WEEKLY_SAMPLES, toWeeklyJson(samples).toString()).apply();
    }

    private static JSONArray toDailyJson(List<DailyEntry> entries) {
        Collections.sort(entries, Comparator.comparing(entry -> entry.date));
        JSONArray array = new JSONArray();
        for (DailyEntry entry : entries) array.put(entry.toJson());
        return array;
    }

    private static JSONArray toCareJson(List<CareUpdate> updates) {
        Collections.sort(updates, Comparator.comparing(update -> update.date));
        JSONArray array = new JSONArray();
        for (CareUpdate update : updates) array.put(update.toJson());
        return array;
    }

    private static JSONArray toWeeklyJson(List<WeeklySample> samples) {
        Collections.sort(samples, Comparator.comparingInt(sample -> sample.weekNumber));
        JSONArray array = new JSONArray();
        for (WeeklySample sample : samples) array.put(sample.toJson());
        return array;
    }

    private static DailyEntry findDaily(List<DailyEntry> entries, String date) {
        for (DailyEntry entry : entries) if (entry.date.equals(date)) return entry;
        return null;
    }

    private static void replaceDaily(List<DailyEntry> entries, DailyEntry updated) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).date.equals(updated.date)) {
                entries.set(index, updated);
                return;
            }
        }
        entries.add(updated);
    }

    private static CareUpdate findCareUpdate(List<CareUpdate> updates, String id) {
        for (CareUpdate update : updates) if (update.id.equals(id)) return update;
        return null;
    }

    private static void replaceCareUpdate(List<CareUpdate> updates, CareUpdate updated) {
        for (int index = 0; index < updates.size(); index++) {
            if (updates.get(index).id.equals(updated.id)) {
                updates.set(index, updated);
                return;
            }
        }
        updates.add(updated);
    }

    private static WeeklySample findWeeklySample(List<WeeklySample> samples, int weekNumber) {
        for (WeeklySample sample : samples) if (sample.weekNumber == weekNumber) return sample;
        return null;
    }

    private static void replaceWeeklySample(List<WeeklySample> samples, WeeklySample updated) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).weekNumber == updated.weekNumber) {
                samples.set(index, updated);
                return;
            }
        }
        samples.add(updated);
    }

    static final class Profile {
        String childName = "Elle";
        String startDate = LocalDate.now().toString();
        String medication = "Leucovorin";
        String defaultDose = "";
        String morningDose = "";
        String eveningDose = "";
        String doseUnit = "mg";
        int dosesPerDay = 2;
        String doctorName = "";
        boolean schoolMonday = true;
        boolean schoolTuesday = true;
        boolean theraplayWednesday = true;
        int weeklySampleDay = 2;
        int weeklyBackupDay = 3;
        String schoolName = "";
        String teacherName = "";
        String className = "";
        String schoolUpdateMethod = "In person at pickup";
        String therapyClinic = "Theraplay";
        String speechTherapist = "";
        String otTherapist = "";
        String therapyUpdateMethod = "In person after session";
        boolean awayWeekendsEnabled = true;
        String awayWeekendAnchor = nextFriday(LocalDate.now()).toString();
        String awayWeekendLabel = "Away with grandmother";
        long updatedAt;

        void read(JSONObject json) {
            childName = json.optString("childName", childName);
            startDate = json.optString("startDate", startDate);
            medication = json.optString("medication", medication);
            defaultDose = json.optString("defaultDose", defaultDose);
            morningDose = json.optString("morningDose", morningDose);
            eveningDose = json.optString("eveningDose", eveningDose);
            doseUnit = json.optString("doseUnit", doseUnit);
            dosesPerDay = json.optInt("dosesPerDay", dosesPerDay);
            doctorName = json.optString("doctorName", doctorName);
            schoolMonday = json.optBoolean("schoolMonday", schoolMonday);
            schoolTuesday = json.optBoolean("schoolTuesday", schoolTuesday);
            theraplayWednesday = json.optBoolean("theraplayWednesday", theraplayWednesday);
            weeklySampleDay = json.optInt("weeklySampleDay", weeklySampleDay);
            weeklyBackupDay = json.optInt("weeklyBackupDay", weeklyBackupDay);
            schoolName = json.optString("schoolName", schoolName);
            teacherName = json.optString("teacherName", teacherName);
            className = json.optString("className", className);
            schoolUpdateMethod = json.optString("schoolUpdateMethod", schoolUpdateMethod);
            therapyClinic = json.optString("therapyClinic", therapyClinic);
            speechTherapist = json.optString("speechTherapist", speechTherapist);
            otTherapist = json.optString("otTherapist", otTherapist);
            therapyUpdateMethod = json.optString("therapyUpdateMethod", therapyUpdateMethod);
            awayWeekendsEnabled = json.optBoolean("awayWeekendsEnabled", awayWeekendsEnabled);
            awayWeekendAnchor = json.optString("awayWeekendAnchor", awayWeekendAnchor);
            awayWeekendLabel = json.optString("awayWeekendLabel", awayWeekendLabel);
            updatedAt = json.optLong("updatedAt", 0);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("childName", childName);
                json.put("startDate", startDate);
                json.put("medication", medication);
                json.put("defaultDose", defaultDose);
                json.put("morningDose", morningDose);
                json.put("eveningDose", eveningDose);
                json.put("doseUnit", doseUnit);
                json.put("dosesPerDay", dosesPerDay);
                json.put("doctorName", doctorName);
                json.put("schoolMonday", schoolMonday);
                json.put("schoolTuesday", schoolTuesday);
                json.put("theraplayWednesday", theraplayWednesday);
                json.put("weeklySampleDay", weeklySampleDay);
                json.put("weeklyBackupDay", weeklyBackupDay);
                json.put("schoolName", schoolName);
                json.put("teacherName", teacherName);
                json.put("className", className);
                json.put("schoolUpdateMethod", schoolUpdateMethod);
                json.put("therapyClinic", therapyClinic);
                json.put("speechTherapist", speechTherapist);
                json.put("otTherapist", otTherapist);
                json.put("therapyUpdateMethod", therapyUpdateMethod);
                json.put("awayWeekendsEnabled", awayWeekendsEnabled);
                json.put("awayWeekendAnchor", awayWeekendAnchor);
                json.put("awayWeekendLabel", awayWeekendLabel);
                json.put("updatedAt", updatedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        boolean isScheduledAway(LocalDate date) {
            if (!awayWeekendsEnabled) return false;
            try {
                LocalDate anchor = LocalDate.parse(awayWeekendAnchor);
                long offset = ChronoUnit.DAYS.between(anchor, date);
                long cycleDay = Math.floorMod(offset, 14);
                return cycleDay >= 0 && cycleDay <= 2;
            } catch (Exception ignored) {
                return false;
            }
        }

        private static LocalDate nextFriday(LocalDate date) {
            LocalDate candidate = date;
            while (candidate.getDayOfWeek().getValue() != 5) candidate = candidate.plusDays(1);
            return candidate;
        }
    }

    static final class DailyEntry {
        String date = LocalDate.now().toString();
        boolean doseTaken;
        String dose = "";
        boolean morningDoseTaken;
        boolean eveningDoseTaken;
        String morningDose = "";
        String eveningDose = "";
        String morningDoseTime = "";
        String eveningDoseTime = "";
        String doseConfirmation = "Observed by us";
        String observationStatus = "Observed enough to rate";
        boolean hasRatings = true;
        boolean healthObserved = true;
        int communication = 3;
        int tellsAboutDay = -1;
        int engagement = 3;
        int focus = 3;
        int mood = 3;
        int appetite = 3;
        float sleepHours = 8f;
        boolean sleepChange;
        boolean tummyUpset;
        boolean headache;
        boolean irritability;
        boolean otherSideEffect;
        int bowelMovementCount = -1;
        int bowelConsistency;
        boolean bowelPain;
        boolean bowelUrgency;
        String note = "";
        String momentContext = "";
        String exactWords = "";
        String communicationMode = "Spoken words";
        String caregiverMeaning = "";
        boolean eventConfirmed;
        String promptsUsed = "";
        String observer = "";
        String factors = "";
        long updatedAt;

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("date", date);
                json.put("doseTaken", doseTaken);
                json.put("dose", dose);
                json.put("morningDoseTaken", morningDoseTaken);
                json.put("eveningDoseTaken", eveningDoseTaken);
                json.put("morningDose", morningDose);
                json.put("eveningDose", eveningDose);
                json.put("morningDoseTime", morningDoseTime);
                json.put("eveningDoseTime", eveningDoseTime);
                json.put("doseConfirmation", doseConfirmation);
                json.put("observationStatus", observationStatus);
                json.put("hasRatings", hasRatings);
                json.put("healthObserved", healthObserved);
                json.put("communication", communication);
                json.put("tellsAboutDay", tellsAboutDay);
                json.put("engagement", engagement);
                json.put("focus", focus);
                json.put("mood", mood);
                json.put("appetite", appetite);
                json.put("sleepHours", sleepHours);
                json.put("sleepChange", sleepChange);
                json.put("tummyUpset", tummyUpset);
                json.put("headache", headache);
                json.put("irritability", irritability);
                json.put("otherSideEffect", otherSideEffect);
                json.put("bowelMovementCount", bowelMovementCount);
                json.put("bowelConsistency", bowelConsistency);
                json.put("bowelPain", bowelPain);
                json.put("bowelUrgency", bowelUrgency);
                json.put("note", note);
                json.put("momentContext", momentContext);
                json.put("exactWords", exactWords);
                json.put("communicationMode", communicationMode);
                json.put("caregiverMeaning", caregiverMeaning);
                json.put("eventConfirmed", eventConfirmed);
                json.put("promptsUsed", promptsUsed);
                json.put("observer", observer);
                json.put("factors", factors);
                json.put("updatedAt", updatedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static DailyEntry fromJson(JSONObject json) {
            DailyEntry entry = new DailyEntry();
            entry.date = json.optString("date", entry.date);
            entry.doseTaken = json.optBoolean("doseTaken");
            entry.dose = json.optString("dose", "");
            boolean hasStructuredDose = json.has("morningDoseTaken") || json.has("eveningDoseTaken");
            entry.morningDoseTaken = json.optBoolean("morningDoseTaken", !hasStructuredDose && entry.doseTaken);
            entry.eveningDoseTaken = json.optBoolean("eveningDoseTaken");
            entry.morningDose = json.optString("morningDose", !hasStructuredDose ? entry.dose : "");
            entry.eveningDose = json.optString("eveningDose", "");
            entry.morningDoseTime = json.optString("morningDoseTime", "");
            entry.eveningDoseTime = json.optString("eveningDoseTime", "");
            entry.doseConfirmation = json.optString("doseConfirmation", "Observed by us");
            entry.observationStatus = json.optString("observationStatus", "Observed enough to rate");
            entry.hasRatings = json.optBoolean("hasRatings", true);
            entry.healthObserved = json.optBoolean("healthObserved", true);
            entry.communication = json.optInt("communication", 3);
            entry.tellsAboutDay = json.optInt("tellsAboutDay", -1);
            entry.engagement = json.optInt("engagement", 3);
            entry.focus = json.optInt("focus", 3);
            entry.mood = json.optInt("mood", 3);
            entry.appetite = json.optInt("appetite", 3);
            entry.sleepHours = (float) json.optDouble("sleepHours", 8);
            entry.sleepChange = json.optBoolean("sleepChange");
            entry.tummyUpset = json.optBoolean("tummyUpset");
            entry.headache = json.optBoolean("headache");
            entry.irritability = json.optBoolean("irritability");
            entry.otherSideEffect = json.optBoolean("otherSideEffect");
            entry.bowelMovementCount = json.optInt("bowelMovementCount", -1);
            entry.bowelConsistency = json.optInt("bowelConsistency", 0);
            entry.bowelPain = json.optBoolean("bowelPain");
            entry.bowelUrgency = json.optBoolean("bowelUrgency");
            entry.note = json.optString("note", "");
            entry.momentContext = json.optString("momentContext", "");
            entry.exactWords = json.optString("exactWords", "");
            entry.communicationMode = json.optString("communicationMode", "Spoken words");
            entry.caregiverMeaning = json.optString("caregiverMeaning", "");
            entry.eventConfirmed = json.optBoolean("eventConfirmed");
            entry.promptsUsed = json.optString("promptsUsed", "");
            entry.observer = json.optString("observer", "");
            entry.factors = json.optString("factors", "");
            entry.updatedAt = json.optLong("updatedAt", 0);
            return entry;
        }

        boolean hasSideEffects() {
            return sleepChange || tummyUpset || headache || irritability || otherSideEffect;
        }

        boolean hasAnyDose() {
            return morningDoseTaken || eveningDoseTaken || doseTaken;
        }

        boolean hasBowelData() {
            return bowelMovementCount >= 0 || bowelConsistency > 0 || bowelPain || bowelUrgency;
        }

        int dosesTakenCount() {
            int count = 0;
            if (morningDoseTaken) count++;
            if (eveningDoseTaken) count++;
            return count == 0 && doseTaken ? 1 : count;
        }

        boolean isNotObserved() {
            return !hasRatings && observationStatus.toLowerCase(Locale.US).contains("away");
        }
    }

    static final class CareUpdate {
        String id = "";
        String date = LocalDate.now().toString();
        String source = "Teacher";
        String provider = "";
        String enteredBy = "Tamika";
        String transcript = "";
        boolean spontaneousCommunication;
        boolean peerInteraction;
        boolean toldAboutDay;
        boolean followedDirections;
        boolean participation;
        boolean regulationSensory;
        boolean independenceSelfCare;
        boolean concern;
        String goalSkill = "";
        String supportProgress = "";
        String homeRecommendation = "";
        long updatedAt;

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("date", date);
                json.put("source", source);
                json.put("provider", provider);
                json.put("enteredBy", enteredBy);
                json.put("transcript", transcript);
                json.put("spontaneousCommunication", spontaneousCommunication);
                json.put("peerInteraction", peerInteraction);
                json.put("toldAboutDay", toldAboutDay);
                json.put("followedDirections", followedDirections);
                json.put("participation", participation);
                json.put("regulationSensory", regulationSensory);
                json.put("independenceSelfCare", independenceSelfCare);
                json.put("concern", concern);
                json.put("goalSkill", goalSkill);
                json.put("supportProgress", supportProgress);
                json.put("homeRecommendation", homeRecommendation);
                json.put("updatedAt", updatedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static CareUpdate fromJson(JSONObject json) {
            CareUpdate update = new CareUpdate();
            update.id = json.optString("id", UUID.randomUUID().toString());
            update.date = json.optString("date", update.date);
            update.source = json.optString("source", update.source);
            update.provider = json.optString("provider", "");
            update.enteredBy = json.optString("enteredBy", "Tamika");
            update.transcript = json.optString("transcript", "");
            update.spontaneousCommunication = json.optBoolean("spontaneousCommunication");
            update.peerInteraction = json.optBoolean("peerInteraction");
            update.toldAboutDay = json.optBoolean("toldAboutDay");
            update.followedDirections = json.optBoolean("followedDirections");
            update.participation = json.optBoolean("participation");
            update.regulationSensory = json.optBoolean("regulationSensory");
            update.independenceSelfCare = json.optBoolean("independenceSelfCare");
            update.concern = json.optBoolean("concern");
            update.goalSkill = json.optString("goalSkill", "");
            update.supportProgress = json.optString("supportProgress", "");
            update.homeRecommendation = json.optString("homeRecommendation", "");
            update.updatedAt = json.optLong("updatedAt", 0);
            return update;
        }

        String tagSummary() {
            List<String> tags = new ArrayList<>();
            if (spontaneousCommunication) tags.add("spontaneous communication");
            if (peerInteraction) tags.add("peer interaction");
            if (toldAboutDay) tags.add("told about day");
            if (followedDirections) tags.add("followed directions");
            if (participation) tags.add("participation");
            if (regulationSensory) tags.add("regulation/sensory");
            if (independenceSelfCare) tags.add("independence/self-care");
            if (concern) tags.add("concern");
            return String.join(", ", tags);
        }
    }

    static final class WeeklySample {
        int weekNumber = 1;
        String date = LocalDate.now().toString();
        int startsCommunication = 3;
        int backAndForth = 3;
        int smallTalk = 3;
        int tellsAboutDay = 3;
        int openQuestions = 3;
        int followUpQuestions = 3;
        boolean completed = true;
        int detailCount;
        int conversationTurns;
        boolean usedFeelingWord;
        boolean askedFollowUp;
        String extraSupport = "Exact prompts and wait time only";
        String note = "";
        boolean videoRecorded;
        String videoFileName = "";
        String setting = "After school";
        String interruptionReason = "";
        final List<PromptResponse> promptResponses = new ArrayList<>();
        long updatedAt;

        WeeklySample() {
            ensurePromptResponses();
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("weekNumber", weekNumber);
                json.put("date", date);
                json.put("startsCommunication", startsCommunication);
                json.put("backAndForth", backAndForth);
                json.put("smallTalk", smallTalk);
                json.put("tellsAboutDay", tellsAboutDay);
                json.put("openQuestions", openQuestions);
                json.put("followUpQuestions", followUpQuestions);
                json.put("completed", completed);
                json.put("detailCount", detailCount);
                json.put("conversationTurns", conversationTurns);
                json.put("usedFeelingWord", usedFeelingWord);
                json.put("askedFollowUp", askedFollowUp);
                json.put("extraSupport", extraSupport);
                json.put("note", note);
                json.put("videoRecorded", videoRecorded);
                json.put("videoFileName", videoFileName);
                json.put("setting", setting);
                json.put("interruptionReason", interruptionReason);
                JSONArray responses = new JSONArray();
                for (PromptResponse response : promptResponses) responses.put(response.toJson());
                json.put("promptResponses", responses);
                json.put("updatedAt", updatedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static WeeklySample fromJson(JSONObject json) {
            WeeklySample sample = new WeeklySample();
            sample.weekNumber = json.optInt("weekNumber", 1);
            sample.date = json.optString("date", sample.date);
            sample.startsCommunication = json.optInt("startsCommunication", 3);
            sample.backAndForth = json.optInt("backAndForth", 3);
            sample.smallTalk = json.optInt("smallTalk", 3);
            sample.tellsAboutDay = json.optInt("tellsAboutDay", 3);
            sample.openQuestions = json.optInt("openQuestions", 3);
            sample.followUpQuestions = json.optInt("followUpQuestions", 3);
            sample.completed = json.optBoolean("completed", true);
            sample.detailCount = json.optInt("detailCount", 0);
            sample.conversationTurns = json.optInt("conversationTurns", 0);
            sample.usedFeelingWord = json.optBoolean("usedFeelingWord");
            sample.askedFollowUp = json.optBoolean("askedFollowUp");
            sample.extraSupport = json.optString("extraSupport", "");
            sample.note = json.optString("note", "");
            sample.videoRecorded = json.optBoolean("videoRecorded");
            sample.videoFileName = json.optString("videoFileName", "");
            sample.setting = json.optString("setting", "After school");
            sample.interruptionReason = json.optString("interruptionReason", "");
            JSONArray responses = json.optJSONArray("promptResponses");
            if (responses != null) {
                sample.promptResponses.clear();
                for (int index = 0; index < responses.length(); index++) {
                    sample.promptResponses.add(PromptResponse.fromJson(responses.optJSONObject(index), index + 1));
                }
            }
            sample.ensurePromptResponses();
            sample.updatedAt = json.optLong("updatedAt", 0);
            return sample;
        }

        float ratingAverage() {
            if (hasPromptData()) return promptScoreAverage();
            return (startsCommunication + backAndForth + smallTalk + tellsAboutDay
                    + openQuestions + followUpQuestions) / 6f;
        }

        void ensurePromptResponses() {
            while (promptResponses.size() < 5) promptResponses.add(new PromptResponse(promptResponses.size() + 1));
            while (promptResponses.size() > 5) promptResponses.remove(promptResponses.size() - 1);
        }

        boolean hasPromptData() {
            for (PromptResponse response : promptResponses) if (response.recorded) return true;
            return false;
        }

        float promptScoreAverage() {
            int total = 0;
            int count = 0;
            for (PromptResponse response : promptResponses) {
                if (!response.recorded) continue;
                total += response.score();
                count++;
            }
            return count == 0 ? 0 : total / (float) count;
        }

        int recordedPromptCount() {
            int count = 0;
            for (PromptResponse response : promptResponses) if (response.recorded) count++;
            return count;
        }

        int totalPromptDetails() {
            int total = 0;
            for (PromptResponse response : promptResponses) if (response.recorded) total += response.detailCount();
            return total;
        }
    }

    static final class PromptResponse {
        int promptNumber;
        boolean recorded;
        int responseType;
        int supportLevel;
        String communicationMode = "Spoken words";
        String exactWords = "";
        String caregiverMeaning = "";
        boolean eventConfirmed;

        PromptResponse(int promptNumber) {
            this.promptNumber = promptNumber;
        }

        int score() {
            int[] baseScores = {0, 2, 3, 4, 5};
            int score = baseScores[Math.max(0, Math.min(baseScores.length - 1, responseType))];
            if (supportLevel == 1) score = Math.min(score, 4);
            else if (supportLevel == 2) score = Math.min(score, 3);
            else if (supportLevel == 3) score = Math.min(score, 2);
            else if (supportLevel >= 4) score = Math.min(score, 1);
            return score;
        }

        int detailCount() {
            if (responseType <= 1) return 0;
            if (responseType == 2) return 1;
            if (responseType == 3) return 2;
            return 3;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("promptNumber", promptNumber);
                json.put("recorded", recorded);
                json.put("responseType", responseType);
                json.put("supportLevel", supportLevel);
                json.put("communicationMode", communicationMode);
                json.put("exactWords", exactWords);
                json.put("caregiverMeaning", caregiverMeaning);
                json.put("eventConfirmed", eventConfirmed);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static PromptResponse fromJson(JSONObject json, int fallbackNumber) {
            PromptResponse response = new PromptResponse(fallbackNumber);
            if (json == null) return response;
            response.promptNumber = json.optInt("promptNumber", fallbackNumber);
            response.recorded = json.optBoolean("recorded");
            response.responseType = json.optInt("responseType", 0);
            response.supportLevel = json.optInt("supportLevel", 0);
            response.communicationMode = json.optString("communicationMode", "Spoken words");
            response.exactWords = json.optString("exactWords", "");
            response.caregiverMeaning = json.optString("caregiverMeaning", "");
            response.eventConfirmed = json.optBoolean("eventConfirmed");
            return response;
        }
    }
}
