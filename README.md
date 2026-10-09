# Elle's Journey

A private, local Android tracker for a 12-week leucovorin trial.

## Install on each phone

1. Send `Elle-Journey-v1.3.1.apk` to the Android phone.
2. Open the file on the phone.
3. If Android asks, allow installs from the app used to open the file.
4. Tap **Install**, then open **Elle's Journey**.

Android may show a Play Protect warning because this is a private APK rather than a Play Store app. The app uses internet access only for optional encrypted GitHub sync. Voice-to-text and local video use Android's system speech and camera apps; Elle's Journey does not request continuous microphone or camera access.

When updating from an earlier version, install the new APK directly over the existing app. Do not uninstall first; Android preserves the local check-ins and secure sync settings during an update.

## Version 1.3.1

- Rebuilt the bottom menu as large, reliable touch targets.
- Added a clear lavender active tab while keeping inactive tabs dark and readable.
- Made the browser preview interactive, including Day 1 saving and History.

## Version 1.3

- Three short daily steps: ratings, health/bowel, and structured notes.
- Daily `0–5` communication and telling-about-her-day observations with plain-language guidance.
- Bowel frequency, consistency, pain/straining, and urgency/accident tracking.
- Weekly communication reviews for initiation, conversational turns, small talk, personal narrative, open questions, and reciprocal questions.
- A standardized Tuesday school-day language sample with fixed prompts and optional local-only video.
- Voice-to-text school updates and structured Theraplay Speech/OT session notes.
- Monday/Tuesday school and Wednesday Theraplay schedule settings.
- Expanded doctor PDFs with weekly communication, bowel context, and care-team notes separated by source.
- Versioned encrypted sync records to prevent an older app from overwriting v1.3-only data.

Update **both phones to v1.3.1 before syncing**. Older versions cannot understand all current records.

## Version 1.2

- Refreshed native interface matching the approved browser prototype.
- Numeric `0–5` ratings with `3` clearly identified as Elle's usual baseline.
- Structured first and optional second leucovorin dose amounts.
- Daily per-dose completion and automatic time recording.
- Dosage history and recorded-dose counts in doctor reports.

## Using two phones

Each phone stores a local copy and can securely synchronize through the private `USTungsten/ASDTrack` repository. GitHub receives only `private-data/elle-tracker.enc`, encrypted on the phone with AES-256-GCM. The token, passphrase, and readable observations are never committed.

### One-time GitHub setup on each phone

1. In GitHub, create a fine-grained personal access token.
2. Limit repository access to **Only select repositories → ASDTrack**.
3. Give it **Repository permissions → Contents: Read and write**. No other write permission is needed.
4. In the app, open **Settings → Encrypted GitHub sync**.
5. Enter the token and a family passphrase of at least 12 characters.
6. Use the exact same family passphrase on both phones, then tap **Sync both phones now**.

The app automatically syncs in the background after a daily check-in. The manual backup/import buttons remain available as an offline fallback. Keep the family passphrase in a password manager; the encrypted file cannot be recovered without it.

## Doctor reports

- The Week 6 PDF unlocks on day 42.
- The Week 12 PDF unlocks on day 84.
- **Preview current PDF now** creates a report at any time.

Reports contain caregiver observations only and are not medical advice. Medication changes should be discussed with the prescriber.
