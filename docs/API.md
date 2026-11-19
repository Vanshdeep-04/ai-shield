# AI Shield REST API (v1)

- **Base URL:** `http://<host>:8080`
- **Data format:** JSON
- **Errors:** returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem objects with the fields `title`, `status`, `detail` and optionally `errors[]`.

## Authentication

- **`google` mode (default):** send `Authorization: Bearer <Google ID token>`. The token's audience must be the configured Web client ID.
- **`dev` mode (local only):** no header is needed, and every request acts as one demo user.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/actuator/health` | public | Health check. Returns `{"status":"UP"}`. |
| GET | `/api/v1/status` | public | Service status: whether AI is configured, the model name and the auth mode. Contains no secrets. |
| POST | `/api/v1/analyses` | required | Analyse one message and save a summary to your history. |
| GET | `/api/v1/analyses?page=0&size=20` | required | Your history, newest first. The maximum page size is 50. |
| GET | `/api/v1/analyses/{id}` | required | Full stored result, including your feedback. |
| DELETE | `/api/v1/analyses/{id}` | required | Delete one entry and its feedback. |
| DELETE | `/api/v1/analyses` | required | Delete all of your history. |
| PUT | `/api/v1/analyses/{id}/feedback` | required | Create or replace your feedback on an analysis. |

## POST /api/v1/analyses

### Request

```json
{
  "source": "GMAIL",
  "from": "\"Sender Name\" <sender@example.com>",
  "replyTo": "other@example.net",
  "subject": "Subject line",
  "bodyText": "Plain-text body",
  "bodyHtml": "<p>HTML body (optional)</p>",
  "links": [{ "url": "https://real-target.example", "text": "visible text" }],
  "receivedAt": "2026-11-18T10:15:00Z"
}
```

**Fields:**
- `source` is required. It must be `GMAIL` or `MANUAL`.
- At least one of `bodyText` or `bodyHtml` must contain text.

**Limits:**
- `from`: 512 characters
- `subject`: 1,000 characters
- `bodyText`: 100,000 characters
- `bodyHtml`: 400,000 characters
- `links`: 300 entries
- whole request: 600 KB

### Responses

| Status | Meaning |
|---|---|
| 200 | Analysis complete. See the response fields below. |
| 400 | Invalid input. The `errors[]` list names each bad field. |
| 401 | Missing or invalid token. |
| 413 | The request is too large. |
| 429 | More than the per-minute analysis limit. |

**Response fields (summary):**
- `riskLevel`: `LOW`, `MEDIUM`, `HIGH` or `UNKNOWN`
- `riskScore`: 0-100, or absent when the level is `UNKNOWN`
- `explanation` and `recommendedAction`
- `ai`: `status`, `model`, `riskLevel`, `riskScore`, `isPhishing`, `indicators[]`, `benignFactors[]` and `unavailableReason`
- `urlAnalysis`: `score`, `findings[]` (each has `host` and `indicators[]` with `code`, `description` and `weight`) and `unavailableChecks[]`
- `senderChecks`: `score` and `findings[]`
- `componentScores`: `ai`, `url` and `metadata`
- `evidence[]`: each item has a `source` (where it came from) and a `type`, which is either `OBSERVED` or `AI_INTERPRETATION`
- `limitations[]`
- `saved` and `id`

If the AI service fails, the response is still 200, but `riskLevel` is `UNKNOWN` (or `HIGH` if the link and sender checks alone are strong enough) and `ai.unavailableReason` says why.

## PUT /api/v1/analyses/{id}/feedback

```json
{ "verdict": "ACCURATE", "comment": "optional, up to 500 characters" }
```

`verdict` must be one of `ACCURATE`, `FALSE_POSITIVE`, `FALSE_NEGATIVE` or `UNSURE`.

## Quick test with curl (dev mode)

```powershell
curl.exe -s http://localhost:8080/api/v1/status
```

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/analyses -H "Content-Type: application/json" --data-binary "@docs/samples/phishing-bank.json"
```

```powershell
curl.exe -s http://localhost:8080/api/v1/analyses
```

The synthetic sample emails are in `docs/samples/`:
- `phishing-bank.json`
- `benign-meeting.json`
- `ambiguous-delivery.json`
- `prompt-injection.json`

They also work in Postman: send a raw JSON body.
