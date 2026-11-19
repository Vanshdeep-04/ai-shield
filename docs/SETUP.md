# AI Shield setup: backend

All commands below are PowerShell and are run from the project root (`Ai-Shield Backup Project`) unless stated otherwise.
The mobile app setup and the Gmail OAuth setup are in `docs/MOBILE.md` and `docs/GMAIL_OAUTH.md`.

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer. JDK 25 is tested. | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| MySQL Server | 8.0 or newer | `mysql --version` |

Maven downloads go to `.tools/m2` inside the project. Nothing is installed globally.

## 2. Get a Gemini API key

1. Open https://aistudio.google.com/apikey and sign in with a Google account.
2. Click **Create API key**, then choose or create a Google Cloud project.
3. Copy the key into `.env` as `GEMINI_API_KEY=...`, as described in section 4.
4. Check your project's free-tier limits at https://aistudio.google.com/rate-limit. Google doesn't guarantee these limits.

**Rules for the key:**
- Keep it **only** in `.env` on the server machine.
- Never put it in the mobile app, in source code, in screenshots or in chat.
- If it leaks, delete it in AI Studio and create a new one.

The model is set by `GEMINI_MODEL`. The default `gemini-3.5-flash-lite` was listed as stable and free in Google's documentation on 2026-11-19.

## 3. Create the MySQL database

1. **Start MySQL.** On Windows, MySQL runs as the `MySQL80` service. Open **Services** (`services.msc`) and start **MySQL80**, or run this in an **administrator** PowerShell:

   ```powershell
   Start-Service MySQL80
   ```

2. **Choose a password.** Open `backend/db/mysql-setup.sql` and replace `CHANGE_ME_STRONG_PASSWORD` with a strong password of your own.

3. **Run the script** as a MySQL administrator. It asks for that administrator's password.

   ```powershell
   Get-Content backend\db\mysql-setup.sql | mysql -u root -p
   ```

The script creates only:
- a new database, `aishield`, using utf8mb4
- a new user, `aishield`@`localhost`, with rights to that database only

It doesn't touch any other database on the server.

You don't need to create any tables. Flyway creates them from `backend/src/main/resources/db/migration` the first time the backend starts.

## 4. Configure

```powershell
Copy-Item .env.example .env
```

Edit `.env` and set at least `GEMINI_API_KEY` and `DB_PASSWORD`. For local testing without Google sign-in, keep `AISHIELD_AUTH_MODE=dev`.

The backend reads `../.env` automatically. Real environment variables take precedence over it.

## 5. Build and test

```powershell
cd backend
```

```powershell
mvn -B "-Dmaven.repo.local=../.tools/m2" test
```

The tests need no API key, no network access to Gemini and no MySQL server:
- Gemini is mocked.
- The API tests run against an in-memory H2 database in MySQL compatibility mode, using the same Flyway migration.

Optionally, run one extra test against your **real** MySQL database. It covers the migration, history, feedback, cascade delete and Unicode text. It creates and deletes only its own test rows.

```powershell
$env:AISHIELD_IT_DB_URL = "jdbc:mysql://localhost:3306/aishield?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
```

```powershell
$env:AISHIELD_IT_DB_USERNAME = "aishield"
```

```powershell
$env:AISHIELD_IT_DB_PASSWORD = "<your password>"
```

```powershell
mvn -B "-Dmaven.repo.local=../.tools/m2" test "-Dtest=MySqlIntegrationTest"
```

## 6. Run

```powershell
mvn "-Dmaven.repo.local=../.tools/m2" spring-boot:run
```

Then check that it's up:

```powershell
curl.exe -s http://localhost:8080/actuator/health
```

```powershell
curl.exe -s http://localhost:8080/api/v1/status
```

Analyse a sample email (in dev mode):

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/analyses -H "Content-Type: application/json" --data-binary "@../docs/samples/phishing-bank.json"
```

## 7. Production-style auth

1. Follow `docs/GMAIL_OAUTH.md` to create a **Web** OAuth client ID.
2. Set `AISHIELD_AUTH_MODE=google`.
3. Set `GOOGLE_OAUTH_WEB_CLIENT_ID=<that client ID>`.

After that, every `/api` request must carry a valid Google ID token.

## 8. Reaching the backend from a phone

For a demo on a phone on the same Wi-Fi network:
1. Find your PC's local IP address with `ipconfig`.
2. Point the app at `http://<that IP>:8080`.
3. Allow Java through the Windows firewall for **private** networks only. Windows asks for this the first time the backend starts.

This prototype uses plain HTTP on the local network. Any deployment beyond your own network must use HTTPS, for example behind a reverse proxy with a certificate.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `GOOGLE_OAUTH_WEB_CLIENT_ID must be set` at startup | `AISHIELD_AUTH_MODE=google` is set without a client ID. Set the client ID, or use `dev` mode. |
| `Communications link failure` | The MySQL80 service isn't running, or `DB_URL` is wrong. |
| `Access denied for user 'aishield'` | `DB_PASSWORD` doesn't match the password set in `mysql-setup.sql`. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `DB_URL` for a local server. |
| Every result is `UNKNOWN` with the reason "not configured" | `GEMINI_API_KEY` is missing. |
| Result is `UNKNOWN` with the reason "quota is used up" | The free-tier daily limit was reached. Wait until after midnight Pacific time. |
| Result is `UNKNOWN` with the reason "rejected the request" or "model is not available" | Check that `GEMINI_MODEL` is a model your project can use. |
