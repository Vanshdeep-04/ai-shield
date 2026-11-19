import type { AnalysisRequest } from '../types/api';
import type { EmailDetail } from './gmail';

/** Backend limits (see docs/API.md); kept below the server's 600 KB request limit. */
export const LIMITS = {
  subject: 1000,
  from: 512,
  bodyText: 100_000,
  bodyHtml: 400_000,
  requestBytes: 550_000,
};

/** UTF-8 byte length without allocating a buffer. */
export function utf8Length(s: string): number {
  let bytes = 0;
  for (const ch of s) {
    const cp = ch.codePointAt(0) ?? 0;
    bytes += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
  }
  return bytes;
}

function cut(s: string | undefined, max: number): string | undefined {
  if (!s) return undefined;
  return s.length > max ? s.slice(0, max) : s;
}

/**
 * Builds the analysis request from a Gmail message. Only the fields the analysis needs are sent:
 * no attachments, no labels, no Gmail IDs, no tokens. Oversized bodies are shortened (HTML first)
 * so the request always fits the server limit.
 */
export function buildGmailRequest(email: EmailDetail): AnalysisRequest {
  const request: AnalysisRequest = {
    source: 'GMAIL',
    from: cut(email.from, LIMITS.from),
    replyTo: cut(email.replyTo, LIMITS.from),
    subject: cut(email.subject, LIMITS.subject),
    bodyText: cut(email.bodyText, LIMITS.bodyText),
    bodyHtml: cut(email.bodyHtml, LIMITS.bodyHtml),
    receivedAt: email.receivedAt,
  };
  return fitToLimit(request);
}

export function buildManualRequest(input: { from?: string; subject?: string; body: string }): AnalysisRequest {
  return fitToLimit({
    source: 'MANUAL',
    from: cut(input.from?.trim() || undefined, LIMITS.from),
    subject: cut(input.subject?.trim() || undefined, LIMITS.subject),
    bodyText: cut(input.body, LIMITS.bodyText),
  });
}

function fitToLimit(request: AnalysisRequest): AnalysisRequest {
  let r = { ...request };
  for (let i = 0; i < 12 && utf8Length(JSON.stringify(r)) > LIMITS.requestBytes; i++) {
    if (r.bodyHtml && r.bodyHtml.length > 1000) {
      r = { ...r, bodyHtml: r.bodyHtml.slice(0, Math.floor(r.bodyHtml.length / 2)) };
    } else if (r.bodyHtml && r.bodyText) {
      r = { ...r, bodyHtml: undefined };
    } else if (r.bodyText && r.bodyText.length > 1000) {
      r = { ...r, bodyText: r.bodyText.slice(0, Math.floor(r.bodyText.length / 2)) };
    } else {
      break;
    }
  }
  return r;
}
