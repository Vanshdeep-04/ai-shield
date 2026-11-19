# AI Shield mobile app: running on an Android phone

The app uses React Native with Expo SDK 58 and TypeScript. Google Sign-In uses native code, so **Gmail only works in a development build**. In Expo Go, the app runs with manual analysis only.

## 1. Configure

```powershell
cd mobile
```

```powershell
Copy-Item .env.example .env
```

Set these values in `mobile/.env`:
- `EXPO_PUBLIC_API_BASE_URL=http://<your PC's Wi-Fi IP>:8080`. Run `ipconfig` and use the IPv4 address. Don't use `localhost`: on the phone, that means the phone itself.
- `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=<Web client ID>`. See `docs/GMAIL_OAUTH.md`.

**Never put the Gemini key in this file.** Everything in `mobile/.env` is bundled into the app.

## 2. Install and check

```powershell
npm install
```

```powershell
npm run check
```

`npm run check` runs the TypeScript check and the Jest tests.

## 3. Build the development app

Choose one of these two options.

### Option A: EAS cloud build (no Android SDK on your PC)

You need a free Expo account. Sign in once:

```powershell
npx eas-cli@latest login
```

The app is linked to the EAS project `@vanshdeep_04/ai-shield` (see `extra.eas.projectId` in `app.config.ts`).

**Always build through the helper script.** This folder sits inside a larger git repository, and plain `eas build` would package from that repository's root. The script makes EAS upload only the `mobile` folder, filtered by `.easignore`, so `node_modules`, `.env` and signing keys are excluded.

```powershell
.\eas-build.ps1
```

When the build finishes, open its link on your phone and install the APK. You may need to allow "install unknown apps" for your browser.

To get the SHA-1 for the Android OAuth client, use either of these:
- Go to **expo.dev → your project → Credentials → Android → com.aishield.app** and read the keystore's **SHA-1 Fingerprint**.
- Or run this, choose the `development` profile, and the keystore details include the SHA-1:

```powershell
.\eas-build.ps1 credentials -p android
```

### Option B: Local build

This needs Android Studio with the Android SDK and **JDK 17 or 21**. Gradle may not support JDK 25 yet; Android Studio's bundled JDK works.

1. Install Android Studio from https://developer.android.com/studio and open its SDK Manager.
2. Point `ANDROID_HOME` at your SDK folder.
3. Point `JAVA_HOME` at Android Studio's `jbr` folder for the build.
4. Enable **Developer options → USB debugging** on the phone, then connect it by USB.
5. Build and install:

```powershell
npx expo run:android
```

## 4. Run a demo on the phone

1. Start the backend on the PC (see `docs/SETUP.md`) and allow Java through the Windows firewall for **private** networks.
2. Make sure the phone and the PC are on the same Wi-Fi network.
3. Start the JavaScript server for the dev build:

```powershell
npx expo start --dev-client
```

4. Open **AI Shield** on the phone. It connects to the dev server automatically, or you can scan the QR code.
5. On the Home screen:
   - The status should show no "Can't reach the AI Shield server" error.
   - Tap **Connect Gmail**, then **Choose an email to analyse**, pick a synthetic test email, and tap **Analyse this email**.
   - Or tap **Paste an email manually** and paste one of the samples from `docs/samples/`.
6. Use the moon/sun button in the header to switch between dark and light mode. **Privacy and settings → Appearance** also offers *System*.

## Screens

| Screen | Purpose |
|---|---|
| Home | Branding, Gmail connection status, connect and choose actions, manual input, real recent analyses |
| Choose an email | Your inbox showing sender, subject and date only, with Gmail search and paging |
| Review email | The selected email shown as plain text. Links can't be tapped and remote images don't load. Analyse and cancel buttons. |
| Paste an email | Manual input for testing the engine without Gmail |
| Result | Risk badge (icon and text), score, explanation, what to do, warning signs labelled *AI interpretation* or *Observed*, link findings, limitations, feedback |
| History | Saved summaries with paging, pull-to-refresh, empty and error states, and delete-all |
| Privacy and settings | Appearance (System / Light / Dark), what goes to Gemini, Gmail permissions, what's stored, Disconnect Gmail |

## Security notes

- The Gmail access token stays inside Google Play services on the phone. The app sends the backend only a Google **ID token**, which proves who you are and grants no Gmail access.
- The app requests `gmail.readonly` only. It needs no SMS, contacts or storage permissions, and SMS and contacts permissions are explicitly blocked in `app.config.ts`.
- Release builds block plain-HTTP traffic, so a deployed backend must use HTTPS. Debug builds allow HTTP for local demos.
