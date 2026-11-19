# AI Shield risk scoring

AI Shield combines three **independent** evidence sources into one result. The score (0-100) is a
**heuristic risk indicator, not a calibrated probability**. A HIGH score does not prove an email is phishing,
and a LOW score does not prove it is safe.

## 1. Components

| Component | Implemented in | Kind of evidence | Reliability weight |
|---|---|---|---|
| AI content analysis (Gemini) | `GeminiAnalysisService` | AI interpretation of the wording: credential requests, urgency, impersonation, financial lures, inconsistencies | **1.0** |
| URL structure | `UrlAnalysisService` | Deterministic observations about each link's text (the link is never opened) | **0.8** |
| Sender metadata | `MetadataAnalysisService` | Deterministic observations about the From and Reply-To headers | **0.6** |

**Why these weights?**
- The AI reads the whole message and is the primary signal.
- URL checks are reliable observations. However, unusual structure alone does not prove intent: legitimate emails also use shorteners and long tracking links.
- Header checks are weighted lowest for two reasons. The headers come from the client, and AI Shield does not verify SPF, DKIM or DMARC.

### URL indicators (`UrlIndicator`)

Each link's score is the sum of its matched weights, capped at 100. The **URL component is the worst single link**, not a sum over links, so repeated links are not counted twice.

| Indicator | Weight | Rationale |
|---|---|---|
| LINK_TEXT_MISMATCH | 40 | The visible text shows one domain but the link goes to another. This is a classic deception. |
| IP_ADDRESS_HOST | 35 | Legitimate services rarely link to raw IP addresses. |
| PUNYCODE_OR_UNICODE_HOST | 35 | Look-alike (homograph) domains. |
| USERINFO_IN_URL | 30 | `https://bank.com@evil.example` actually goes to evil.example. |
| DECEPTIVE_HOSTNAME | 25 | A brand name appears in a domain that brand does not own, such as `paypal-secure.example`. |
| MALFORMED_URL | 20 | The link can't be parsed safely. |
| URL_SHORTENER | 15 | Hides the destination, but is common in legitimate mail. |
| EXCESSIVE_SUBDOMAINS | 15 | Long subdomain chains can hide the real domain. |
| NON_STANDARD_PORT | 15 | Unusual for consumer web links. |
| EMBEDDED_REDIRECT | 15 | Another URL appears inside the query string, a common open-redirect trick. |
| SUSPICIOUS_CHARACTERS | 10 | The host has 3 or more hyphens, or the URL has 6 or more percent-encoded characters. |
| LONG_URL | 10 | The URL is longer than 100 characters. This is weak evidence. |
| UNCOMMON_TLD | 10 | The top-level domain appears disproportionately in abuse reports. This is weak evidence. |
| INSECURE_HTTP | 5 | The link doesn't use HTTPS. This is very weak evidence. |

Weak indicators are weighted so that **no single weak indicator can reach the MEDIUM band (40) on its own**.

### Sender indicators (`MetadataIndicator`)

| Indicator | Weight |
|---|---|
| DISPLAY_NAME_ADDRESS_MISMATCH: the display name contains a different email address | 30 |
| DISPLAY_NAME_BRAND_MISMATCH: the display name names a brand, but the sender's domain belongs to someone else | 30 |
| REPLY_TO_DOMAIN_MISMATCH: replies go to a different organisation | 20 |

## 2. Combination formula

Each component score `S` is scaled by its weight `w`. The scaled scores are then combined like independent alarms (a "noisy-OR"):

```
final = 100 × (1 − (1 − 1.0·A/100) × (1 − 0.8·U/100) × (1 − 0.6·M/100))
```

**Properties:**
- One strong signal can raise the result on its own. For example, AI = 10 and URL = 90 gives a final score of **75 (HIGH)**. This means suspicious links matter even when the wording looks harmless.
- Several moderate signals reinforce each other, but the result never exceeds 100.
- A component with score 0 never lowers the result.

**Avoiding double counting:**
- Gemini is told explicitly *not* to score URL structure.
- The URL component uses the worst link rather than adding up all links.
- The three components look at different evidence.

## 3. Bands

| Score | Level |
|---|---|
| 0-39 | LOW |
| 40-69 | MEDIUM |
| 70-100 | HIGH |
| no score | UNKNOWN |

The level always comes from the final score. If Gemini's own label disagrees with its score, AI Shield uses the score and lists the disagreement under *limitations*.

## 4. Missing or conflicting evidence

| Situation | Result |
|---|---|
| Gemini is unavailable (not configured, timeout, quota used up, invalid reply) and the URL and sender checks alone reach 70 or more | **HIGH**, noted as "based on link and sender checks only" |
| Gemini is unavailable and the deterministic checks are below 70 | **UNKNOWN**. The result is **never silently LOW**. |
| Gemini returns UNKNOWN (for example, empty or unreadable content) | Treated the same way as unavailable |
| Gemini's verdict and score are inconsistent | The score is used and a limitation is shown |

## 5. Evidence labelling

Every evidence item in the API response has two labels:
- **source:** `AI_CONTENT_ANALYSIS`, `URL_ANALYSIS` or `SENDER_METADATA`
- **type:** `OBSERVED` for deterministic facts, or `AI_INTERPRETATION` for the model's reading

The app shows these labels, so users can tell facts apart from AI opinion.
