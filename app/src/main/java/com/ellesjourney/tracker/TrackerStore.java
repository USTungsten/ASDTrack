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

final class TrackerStore {
    private static final String PREFS = "elles_journey_data";
    private static final String PROFILE = "profile";
    private static final String ENTRIES = "entries";
    private final SharedPreferences preferences;

    TrackerStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasProfile() {
        return preferences.contains(PROFILE);
    }

    Profile getProfile() {
        Profile profile = new Profile();
        profile.childName = "Elle";
        profile.startDate = LocalDate.now().toString();
        profile.medication = "Leucovorin";
        profile.defaultDose = "";
        profile.morningDose = "";
        profile.eveningDose = "";
        profile.doseUnit = "mg";
        profile.dosesPerDay = 2;
        profile.doctorName = "";
        profile.updatedAt = 0;
        try {
            String raw = preferences.getString(PROFILE, null);
            if (raw != null) {
                JSONObject json = new JSONObject(raw);
                profile.childName = json.optString("childName", profile.childName);
                profile.startDate = json.optString("startDate", profile.startDate);
                profile.medication = json.optString("medication", profile.medication);
                profile.defaultDose = json.optString("defaultDose", "");
                profile.morningDose = json.optString("morningDose", "");
                profile.eveningDose = json.optString("eveningDose", "");
                profile.doseUnit = json.optString("doseUnit", "mg");
                profile.dosesPerDay = json.optInt("dosesPerDay", 2);
                profile.doctorName = json.optString("doctorName", "");
                profile.updatedAt = json.optLong("updatedAt", 0);
            }
        } catch (JSONException ignored) {
        }
        return profile;
    }

    void saveProfile(Profile profile) {
        JSONObject json = new JSONObject();
        try {
            profile.updatedAt = System.currentTimeMillis();
            json.put("childName", profile.childName);
            json.put("startDate", profile.startDate);
            json.put("medication", profile.medication);
            json.put("defaultDose", profile.defaultDose);
            json.put("morningDose", profile.morningDose);
            json.put("eveningDose", profile.eveningDose);
            json.put("doseUnit", profile.doseUnit);
            json.put("dosesPerDay", profile.dosesPerDay);
            json.put("doctorName", profile.doctorName);
            json.put("updatedAt", profile.updatedAt);
            preferences.edit().putString(PROFILE, json.toString()).apply();
        } catch (JSONException ignored) {
        }
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
        List<DailyEntry> entries = getEntries();
        boolean replaced = false;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).date.equals(updated.date)) {
                entries.set(index, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) entries.add(updated);
        Collections.sort(entries, Comparator.comparing(entry -> entry.date));
        JSONArray array = new JSONArray();
        for (DailyEntry entry : entries) array.put(entry.toJson());
        preferences.edit().putString(ENTRIES, array.toString()).apply();
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
            root.put("format", "elles-journey-backup-v1");
            root.put("profile", new JSONObject(preferences.getString(PROFILE, "{}")));
            root.put("entries", new JSONArray(preferences.getString(ENTRIES, "[]")));
        } catch (JSONException ignored) {
        }
        return root.toString();
    }

    boolean importJson(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            if (!"elles-journey-backup-v1".equals(root.optString("format"))) return false;
            List<DailyEntry> merged = getEntries();
            boolean hadLocalEntries = !merged.isEmpty();
            JSONArray imported = root.getJSONArray("entries");
            for (int index = 0; index < imported.length(); index++) {
                DailyEntry incoming = DailyEntry.fromJson(imported.getJSONObject(index));
                boolean replaced = false;
                for (int localIndex = 0; localIndex < merged.size(); localIndex++) {
                    if (merged.get(localIndex).date.equals(incoming.date)) {
                        merged.set(localIndex, incoming);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) merged.add(incoming);
            }
            Collections.sort(merged, Comparator.comparing(entry -> entry.date));
            JSONArray mergedJson = new JSONArray();
            for (DailyEntry entry : merged) mergedJson.put(entry.toJson());
            SharedPreferences.Editor editor = preferences.edit().putString(ENTRIES, mergedJson.toString());
            JSONObject incomingProfile = root.getJSONObject("profile");
            JSONObject localProfile = new JSONObject(preferences.getString(PROFILE, "{}"));
            long incomingUpdated = incomingProfile.optLong("updatedAt", 0);
            long localUpdated = localProfile.optLong("updatedAt", 0);
            if (!hasProfile() || !hadLocalEntries || incomingUpdated > localUpdated) {
                editor.putString(PROFILE, incomingProfile.toString());
            }
            editor.apply();
            return true;
        } catch (JSONException exception) {
            return false;
        }
    }

    static final class Profile {
        String childName;
        String startDate;
        String medication;
        String defaultDose;
        String morningDose;
        String eveningDose;
        String doseUnit;
        int dosesPerDay;
        String doctorName;
        long updatedAt;
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
        int communication = 3;
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
        String note = "";

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
                json.put("communication", communication);
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
                json.put("note", note);
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
            entry.communication = json.optInt("communication", 3);
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
            entry.note = json.optString("note", "");
            return entry;
        }

        boolean hasSideEffects() {
            return sleepChange || tummyUpset || headache || irritability || otherSideEffect;
        }

        boolean hasAnyDose() {
            return morningDoseTaken || eveningDoseTaken || doseTaken;
        }

        int dosesTakenCount() {
            int count = 0;
            if (morningDoseTaken) count++;
            if (eveningDoseTaken) count++;
            return count == 0 && doseTaken ? 1 : count;
        }
    }
}
