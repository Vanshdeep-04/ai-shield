/**
 * Safe parsing of Gmail API message payloads (format=full).
 * Only text/plain and text/html bodies are decoded; attachments are skipped and never downloaded.
 */

export interface GmailHeader {
  name: string;
  value: string;
}

export interface GmailPart {
  partId?: string;
  mimeType?: string;
  filename?: string;
  headers?: GmailHeader[];
  body?: { size?: number; data?: string; attachmentId?: string };
  parts?: GmailPart[];
}

export interface ParsedBodies {
  text?: string;
  html?: string;
  attachmentCount: number;
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const B64_LOOKUP: Record<string, number> = Object.fromEntries([...B64].map((c, i) => [c, i]));

/** Decodes base64url (Gmail's encoding) to bytes. Invalid characters are ignored. */
export function base64UrlToBytes(input: string): Uint8Array {
  const clean = input.replace(/-/g, '+').replace(/_/g, '/').replace(/[^A-Za-z0-9+/]/g, '');
  const out = new Uint8Array(Math.floor((clean.length * 3) / 4));
  let buffer = 0;
  let bits = 0;
  let o = 0;
  for (const ch of clean) {
    buffer = (buffer << 6) | B64_LOOKUP[ch];
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[o++] = (buffer >> bits) & 0xff;
    }
  }
  return out.subarray(0, o);
}

/** Lenient UTF-8 decoder (invalid sequences become U+FFFD). */
export function utf8Decode(bytes: Uint8Array): string {
  let out = '';
  let i = 0;
  while (i < bytes.length) {
    const b = bytes[i];
    let cp = 0xfffd;
    let len = 1;
    if (b < 0x80) {
      cp = b;
    } else if (b >= 0xc2 && b <= 0xdf && isCont(bytes[i + 1])) {
      cp = ((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f);
      len = 2;
    } else if (b >= 0xe0 && b <= 0xef && isCont(bytes[i + 1]) && isCont(bytes[i + 2])) {
      cp = ((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f);
      len = 3;
      if (cp < 0x800 || (cp >= 0xd800 && cp <= 0xdfff)) cp = 0xfffd;
    } else if (b >= 0xf0 && b <= 0xf4 && isCont(bytes[i + 1]) && isCont(bytes[i + 2]) && isCont(bytes[i + 3])) {
      cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3f) << 12) | ((bytes[i + 2] & 0x3f) << 6) | (bytes[i + 3] & 0x3f);
      len = 4;
      if (cp < 0x10000 || cp > 0x10ffff) cp = 0xfffd;
    }
    out += String.fromCodePoint(cp);
    i += len;
  }
  return out;
}

function isCont(b: number | undefined): boolean {
  return b !== undefined && (b & 0xc0) === 0x80;
}

function latin1Decode(bytes: Uint8Array): string {
  let out = '';
  for (const b of bytes) out += String.fromCharCode(b);
  return out;
}

function charsetOf(part: GmailPart): string {
  const ct = header(part.headers, 'Content-Type') ?? '';
  const m = /charset="?([^";\s]+)"?/i.exec(ct);
  return (m?.[1] ?? 'utf-8').toLowerCase();
}

export function decodePartData(part: GmailPart): string {
  const data = part.body?.data;
  if (!data) return '';
  const bytes = base64UrlToBytes(data);
  const cs = charsetOf(part);
  return cs === 'iso-8859-1' || cs === 'latin1' || cs === 'us-ascii' || cs === 'windows-1252'
    ? latin1Decode(bytes)
    : utf8Decode(bytes);
}

export function header(headers: GmailHeader[] | undefined, name: string): string | undefined {
  const lower = name.toLowerCase();
  return headers?.find((h) => h.name.toLowerCase() === lower)?.value;
}

const MAX_DEPTH = 20;

/**
 * Walks the MIME tree and returns the first text/plain and text/html bodies.
 * Parts with a filename or an attachmentId are attachments: counted, never decoded or fetched.
 */
export function extractBodies(payload: GmailPart | undefined): ParsedBodies {
  const result: ParsedBodies = { attachmentCount: 0 };
  const walk = (part: GmailPart | undefined, depth: number) => {
    if (!part || depth > MAX_DEPTH) return;
    const isAttachment = !!part.filename || !!part.body?.attachmentId;
    const type = (part.mimeType ?? '').toLowerCase();
    if (isAttachment) {
      result.attachmentCount += 1;
      return;
    }
    if (type === 'text/plain' && result.text === undefined && part.body?.data) {
      result.text = decodePartData(part);
    } else if (type === 'text/html' && result.html === undefined && part.body?.data) {
      result.html = decodePartData(part);
    }
    part.parts?.forEach((p) => walk(p, depth + 1));
  };
  walk(payload, 0);
  return result;
}

/** Removes markup for a short, readable preview (the backend does the authoritative HTML parsing). */
export function htmlToPreviewText(html: string): string {
  return html
    .replace(/<(script|style|head)[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|tr|h\d)>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/[ \t]+/g, ' ')
    .replace(/\n\s*\n\s*\n+/g, '\n\n')
    .trim();
}
