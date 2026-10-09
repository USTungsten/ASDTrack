# Elle's Journey

A private, local Android tracker for a 12-week leucovorin trial.

## Install on each phone

1. Send `Elle-Journey-v1.1.apk` to the Android phone.
2. Open the file on the phone.
3. If Android asks, allow installs from the app used to open the file.
4. Tap **Install**, then open **Elle's Journey**.

Android may show a Play Protect warning because this is a private APK rather than a Play Store app. The app requests no internet, location, camera, microphone, contacts, or notification permissions.

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
