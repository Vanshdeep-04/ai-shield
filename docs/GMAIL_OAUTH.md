# Gmail OAuth setup (Google Cloud)

Only you can do these steps, because they need your own Google account and Google Cloud project. AI Shield never sees your Google password.

You'll create two OAuth clients:

| Client type | Used by | Where its value goes |
|---|---|---|
| **Web application** | The ID token audience. The backend verifies tokens against it, and the app's `webClientId` uses it. | `GOOGLE_OAUTH_WEB_CLIENT_ID` in `.env` and `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` in `mobile/.env` |
| **Android** | Lets Google trust the app's signing key (package name + SHA-1) | Nowhere. It only has to exist. |

**You don't need a client secret.** Never download a client secret, and never put one in the app or the repository.

## 1. Project and Gmail API

1. Open https://console.cloud.google.com/ and select a project, or create one. You can use the same project as your Gemini key.
2. Go to **APIs and Services → Library**, search for **Gmail API**, and click **Enable**.

## 2. Consent screen (Google Auth Platform)

1. Go to **Google Auth Platform → Branding**.
   - App name: `AI Shield (university prototype)`
   - User support email: your own email address
   - Developer contact: your own email address
2. Go to **Audience**.
   - User type: **External**
   - Publishing status: **Testing**
   - Under **Test users**, add every Gmail account you'll demo with. A test Gmail account with synthetic emails is recommended.
3. Go to **Data Access → Add or remove scopes**.
   - Add `https://www.googleapis.com/auth/gmail.readonly`.
   - Google marks this scope as **restricted**. In Testing mode, that's fine for up to 100 test users. Publishing the app publicly would require Google's verification and a security assessment, which is out of scope for this prototype.

## 3. Web application client

1. Go to **Clients → Create client → Web application**. Name it `AI Shield web (ID tokens)`. It needs no redirect URIs.
2. Copy the **Client ID**, which looks like `1234-abc.apps.googleusercontent.com`.
3. In the project root `.env`, set:
   - `GOOGLE_OAUTH_WEB_CLIENT_ID=<client ID>`
   - `AISHIELD_AUTH_MODE=google`
4. In `mobile/.env`, set `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=<the same client ID>`.

## 4. Android client (package name + SHA-1)

The package name is `com.aishield.app`. It's set in `mobile/app.config.ts`.

You need the **SHA-1** fingerprint of the key that signs your build.

**Local debug build** (after you've run `npx expo run:android` once, so the `mobile/android` folder exists):

```powershell
keytool -list -v -keystore mobile\android\app\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

**EAS cloud build** (the option used for this project):
- Open https://expo.dev/accounts/vanshdeep_04/projects/ai-shield/credentials, choose **Android → com.aishield.app**, and copy the keystore's **SHA-1 Fingerprint**.
- Or, from the `mobile` folder, run `.\eas-build.ps1 credentials -p android`.

Then:
1. Go to **Clients → Create client → Android**.
2. Enter the package name `com.aishield.app` and the SHA-1.
3. If you use more than one signing key, create one Android client per SHA-1. For example, a local debug build and an EAS build use different keys.

## 5. Check it works

1. Start the backend in `google` mode, then open the app.
2. Tap **Connect Gmail** and choose a test user account.
3. On Google's consent screen, allow **"Read your email messages and settings"**.
4. Tap **Choose an email to analyse**. Your inbox list should appear.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `DEVELOPER_ERROR` when connecting | The SHA-1 or package name in the Android client doesn't match the build, or `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` isn't the **Web** client ID. |
| "Access blocked: app has not completed verification" | The account isn't listed under **Test users**. |
| Inbox error "permission" (Gmail 403) | The scope wasn't granted. Tap **Allow Gmail read access** on the Home screen. |
| Backend returns 401 for every request | `GOOGLE_OAUTH_WEB_CLIENT_ID` on the backend differs from the app's web client ID, or the phone's clock is wrong. |
| Gmail API 403 "API not enabled" | Enable the Gmail API (step 1). |

## Revoking access

- In the app, go to **Privacy and settings → Disconnect Gmail**. This revokes the access token and signs you out.
- You can also remove access at https://myaccount.google.com/permissions.
