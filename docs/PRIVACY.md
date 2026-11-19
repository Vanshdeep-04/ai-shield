# Privacy, data handling and limitations

## Where each kind of data goes

| Data | Where it goes | Stored? |
|---|---|---|
| Gmail access token | Stays on the phone in secure storage. It is never sent to the AI Shield server. | Only on the device |
| Google ID token (proves who you are) | Sent to the AI Shield server with each request and verified there | No |
| List of your recent emails (sender, subject, date) | Fetched from Gmail directly to the phone | No |
| The email you choose to analyse (sender, Reply-To, subject, body text and links) | Phone → AI Shield server → Gemini API | **The server does not keep the body.** See below for Gemini. |
| Analysis result | Server → MySQL | Yes, until you delete it |
| Your feedback | Server → MySQL | Yes, until you delete it |

## What the history database stores

Each analysis in the history stores the following:
- your user ID, a pseudonymous value `google:<sub>` taken from Google's ID token
- the source (`GMAIL` or `MANUAL`)
- the **sender's domain only**, for example `example.com`, not the full address
- the first 120 characters of the subject
- the risk level, score, AI status and model name
- the result details: the explanation, indicators with **short quotes** chosen by the AI as evidence, and the link findings (URLs and hostnames)
- a timestamp
- your feedback, if you give any

**Never stored:**
- full email bodies
- attachments
- Gmail or OAuth tokens
- the Gemini API key

**Deleting your data:**
- In the app's History screen, you can delete one entry or all of them.
- Through the API, use `DELETE /api/v1/analyses/{id}` for one entry or `DELETE /api/v1/analyses` for all.

## Data sent to Gemini (important)

For each analysis, the following is sent to Google's Gemini Developer API:
- the sender's name and address
- the Reply-To address
- the subject
- up to 15,000 characters of body text
- up to 25 links

Requests use `"store": false`.

Google's terms for the **free (unpaid) tier** say that Google may use submitted content and responses to improve its products. Human reviewers may also read them, although Google separates the data from your account and API key first. Google advises against submitting sensitive or personal information. On the paid tier, Google says it does not use prompts for product improvement. Read the current terms here: https://ai.google.dev/gemini-api/terms.

**Recommendation:** for demonstrations, use a **dedicated test Gmail account** that contains synthetic emails. Don't analyse emails that contain confidential or personal data while using the free tier.

## Gmail permissions

AI Shield requests only `https://www.googleapis.com/auth/gmail.readonly`. This is the narrowest Gmail scope that can read message bodies. With it, AI Shield cannot send, delete or modify email.
- It reads only the email you select, plus the header list shown in the inbox picker.
- It never downloads attachments.
- You can disconnect at any time in the app, or at https://myaccount.google.com/permissions.

`gmail.readonly` is a **restricted** scope. An app in Google's "Testing" publishing status can use it for up to 100 listed test users. A public release would require Google's OAuth verification and a security assessment.

## Limitations

- AI Shield **may produce false positives** (flagging a legitimate email) **and false negatives** (missing a phishing email).
- The score is a heuristic indicator, not a probability.
- Links are never opened, so AI Shield can't see where a shortened link or redirect really leads.
- There is no domain-reputation, blocklist or WHOIS lookup.
- Sender authentication (SPF, DKIM, DMARC) is not verified. The From address can be forged.
- Free-tier Gemini limits are not published as fixed numbers and can change. When they are exceeded, results show **UNKNOWN**.
- Only text is analysed. Images and attachments are not.
