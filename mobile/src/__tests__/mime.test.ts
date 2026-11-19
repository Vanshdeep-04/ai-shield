import { base64UrlToBytes, extractBodies, GmailPart, htmlToPreviewText, utf8Decode } from '../services/mime';

function b64url(s: string): string {
  return Buffer.from(s, 'utf8').toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

describe('base64url and UTF-8 decoding', () => {
  it('round-trips ASCII and multi-byte text', () => {
    const text = 'Hello ₹100 — café 😀 नमस्ते';
    expect(utf8Decode(base64UrlToBytes(b64url(text)))).toBe(text);
  });

  it('replaces invalid UTF-8 with the replacement character instead of throwing', () => {
    expect(utf8Decode(new Uint8Array([0x61, 0xff, 0x62]))).toBe('a�b');
  });
});

describe('extractBodies', () => {
  const message: GmailPart = {
    mimeType: 'multipart/mixed',
    parts: [
      {
        mimeType: 'multipart/alternative',
        parts: [
          { mimeType: 'text/plain', headers: [{ name: 'Content-Type', value: 'text/plain; charset="UTF-8"' }], body: { data: b64url('Plain body') } },
          { mimeType: 'text/html', body: { data: b64url('<p>HTML <b>body</b></p>') } },
        ],
      },
      { mimeType: 'application/pdf', filename: 'invoice.pdf', body: { attachmentId: 'att-1', size: 1234 } },
      { mimeType: 'text/plain', filename: 'notes.txt', body: { attachmentId: 'att-2' } },
    ],
  };

  it('finds text and HTML bodies in nested multipart messages', () => {
    const r = extractBodies(message);
    expect(r.text).toBe('Plain body');
    expect(r.html).toBe('<p>HTML <b>body</b></p>');
  });

  it('counts attachments without decoding them', () => {
    expect(extractBodies(message).attachmentCount).toBe(2);
  });

  it('decodes latin-1 parts', () => {
    const part: GmailPart = {
      mimeType: 'text/plain',
      headers: [{ name: 'Content-Type', value: 'text/plain; charset=ISO-8859-1' }],
      body: { data: Buffer.from([0x63, 0x61, 0x66, 0xe9]).toString('base64') },
    };
    expect(extractBodies(part).text).toBe('café');
  });

  it('handles empty or missing payloads', () => {
    expect(extractBodies(undefined)).toEqual({ attachmentCount: 0 });
    expect(extractBodies({ mimeType: 'text/plain', body: { size: 0 } })).toEqual({ attachmentCount: 0 });
  });
});

describe('htmlToPreviewText', () => {
  it('strips scripts, styles and tags, and decodes basic entities', () => {
    const html = '<style>.x{}</style><p>Dear&nbsp;user</p><script>alert(1)</script><a href="https://evil">Click &amp; verify</a>';
    const text = htmlToPreviewText(html);
    expect(text).toContain('Dear user');
    expect(text).toContain('Click & verify');
    expect(text).not.toContain('alert');
    expect(text).not.toContain('<');
  });
});
