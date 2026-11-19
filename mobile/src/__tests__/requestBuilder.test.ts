import type { EmailDetail } from '../services/gmail';
import { buildGmailRequest, buildManualRequest, LIMITS, utf8Length } from '../services/requestBuilder';

const email: EmailDetail = {
  id: 'gmail-id-123',
  from: '"Bank" <alerts@bank.example>',
  replyTo: 'reply@other.example',
  subject: 'Verify now',
  date: 'Tue, 18 Nov 2026 10:15:00 +0000',
  receivedAt: '2026-11-18T10:15:00.000Z',
  bodyText: 'Please verify',
  bodyHtml: '<p>Please verify</p>',
  previewText: 'Please verify',
  attachmentCount: 1,
};

describe('buildGmailRequest', () => {
  it('sends only the fields needed for analysis', () => {
    const r = buildGmailRequest(email);
    expect(r).toEqual({
      source: 'GMAIL',
      from: email.from,
      replyTo: email.replyTo,
      subject: email.subject,
      bodyText: email.bodyText,
      bodyHtml: email.bodyHtml,
      receivedAt: email.receivedAt,
    });
    // no Gmail IDs, attachment info or tokens
    expect(JSON.stringify(r)).not.toContain('gmail-id-123');
    expect(JSON.stringify(r)).not.toMatch(/token|attachment/i);
  });

  it('keeps oversized emails under the server request limit', () => {
    const big = { ...email, bodyText: 'é'.repeat(100_000), bodyHtml: '<p>' + 'ü'.repeat(399_000) + '</p>' };
    const r = buildGmailRequest(big);
    expect(utf8Length(JSON.stringify(r))).toBeLessThanOrEqual(LIMITS.requestBytes);
    expect(r.bodyText!.length).toBeGreaterThan(0);
  });

  it('truncates long subjects', () => {
    expect(buildGmailRequest({ ...email, subject: 's'.repeat(5000) }).subject).toHaveLength(LIMITS.subject);
  });
});

describe('buildManualRequest', () => {
  it('omits blank optional fields', () => {
    expect(buildManualRequest({ from: '  ', subject: '', body: 'Body text' })).toEqual({ source: 'MANUAL', bodyText: 'Body text' });
  });
});

describe('utf8Length', () => {
  it('counts bytes, not characters', () => {
    expect(utf8Length('a')).toBe(1);
    expect(utf8Length('é')).toBe(2);
    expect(utf8Length('₹')).toBe(3);
    expect(utf8Length('😀')).toBe(4);
  });
});
