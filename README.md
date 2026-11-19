# AI Shield

AI Shield is a Gmail-first mobile app that helps users judge whether an email is phishing or a scam. It is a university security prototype. It does not claim to detect every attack.

## How it works

```
React Native app (Android)
  ├─ Google sign-in with the gmail.readonly scope (the Gmail token stays on the phone)
  ├─ The user picks one email; the app fetches only that message from the Gmail API
  └─ POST /api/v1/analyses (with a Google ID token)
        ▼
Spring Boot backend (Java 17+)
  ├─ Normalisation: safe HTML-to-text, invisible-character removal, link extraction
  ├─ URL analysis: deterministic checks; links are never opened
  ├─ Sender checks: From vs Reply-To, display-name spoofing
  ├─ Gemini API: direct REST call from Java with structured JSON output and strict validation
  ├─ Risk engine: a transparent "noisy-OR" combination (see docs/RISK_SCORING.md)
  └─ MySQL 8: per-user history and feedback (no email bodies, no tokens)
```

There is no Python, FastAPI or trained classifier. The original synopsis's TF-IDF/scikit-learn pipeline is replaced by Gemini.

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Spring Boot 3.5 service, built with Maven |
| `mobile/` | React Native (Expo) Android app |
| `docs/SETUP.md` | Backend setup, Gemini key and MySQL |
| `backend/db/mysql-setup.sql` | One-time script that creates the `aishield` database and user |
| `docs/GMAIL_OAUTH.md` | Google Cloud OAuth configuration |
| `docs/MOBILE.md` | Running the app on an Android phone |
| `docs/API.md` | REST API reference with curl examples |
| `docs/RISK_SCORING.md` | Scoring method and weights |
| `docs/PRIVACY.md` | Data flows, storage and limitations |
| `docs/samples/` | Synthetic test emails |
| `.env.example` | Configuration template (no secrets) |

## Quick start (backend)

```powershell
Copy-Item .env.example .env
```

Edit `.env`: set `GEMINI_API_KEY` and the database settings, and keep `AISHIELD_AUTH_MODE=dev` for local testing. Then:

```powershell
cd backend
```

```powershell
mvn "-Dmaven.repo.local=../.tools/m2" spring-boot:run
```

See `docs/SETUP.md` for the details.

## Quick start (mobile)

```powershell
cd mobile
```

```powershell
Copy-Item .env.example .env
```

```powershell
npm install
```

```powershell
npm run check
```

`npm run check` runs the TypeScript check and the Jest tests. To run the app on a phone, follow `docs/MOBILE.md`. To set up Google sign-in and Gmail access, follow `docs/GMAIL_OAUTH.md`.

The UI uses style A ("Clean light"). The moon/sun button in the header switches dark mode, and **Privacy and settings → Appearance** offers System, Light and Dark.
