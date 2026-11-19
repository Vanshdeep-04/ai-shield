import { config } from '../config';
import { extractBodies, GmailPart, header, htmlToPreviewText } from './mime';

/**
 * Minimal Gmail API (v1) client running on the phone with the user's own OAuth access token.
 * The token is supplied per call by the auth layer and is never stored by this module or sent
 * to the AI Shield backend.
 */

const GMAIL_BASE = 'https://gmail.googleapis.com/gmail/v1/users/me';

export type GmailErrorKind = 'unauthorized' | 'forbidden' | 'notFound' | 'rateLimited' | 'network' | 'timeout' | 'server';

export class GmailError extends Error {
  constructor(public kind: GmailErrorKind, message: string) {
    super(message);
    this.name = 'GmailError';
  }
}

export interface EmailSummary {
  id: string;
  from: string;
  subject: string;
  date?: string;
  unread: boolean;
}

export interface EmailDetail {
  id: string;
  from: string;
  replyTo?: string;
  subject: string;
  date?: string;
  receivedAt?: string;
  bodyText?: string;
  bodyHtml?: string;
  previewText: string;
  attachmentCount: number;
}

export type TokenProvider = (forceRefresh: boolean) => Promise<string>;

type Fetch = typeof fetch;

export class GmailClient {
  constructor(private getAccessToken: TokenProvider, private fetchImpl: Fetch = fetch) {}

  /** Lists recent inbox messages with headers only (no bodies). */
  async listMessages(options: { query?: string; pageToken?: string; pageSize?: number } = {}): Promise<{
    messages: EmailSummary[];
    nextPageToken?: string;
  }> {
    const params = new URLSearchParams({ maxResults: String(options.pageSize ?? 15), labelIds: 'INBOX' });
    if (options.query) params.set('q', options.query);
    if (options.pageToken) params.set('pageToken', options.pageToken);
    const list = await this.request<{ messages?: { id: string }[]; nextPageToken?: string }>(`/messages?${params}`);
    const ids = (list.messages ?? []).map((m) => m.id);
    const summaries = await Promise.all(ids.map((id) => this.getSummary(id)));
    return { messages: summaries, nextPageToken: list.nextPageToken };
  }

  private async getSummary(id: string): Promise<EmailSummary> {
    const params = new URLSearchParams({ format: 'metadata' });
    ['From', 'Subject', 'Date'].forEach((h) => params.append('metadataHeaders', h));
    const msg = await this.request<{ id: string; labelIds?: string[]; payload?: GmailPart }>(
      `/messages/${encodeURIComponent(id)}?${params}`,
    );
    return {
      id: msg.id,
      from: header(msg.payload?.headers, 'From') ?? '(unknown sender)',
      subject: header(msg.payload?.headers, 'Subject') ?? '(no subject)',
      date: header(msg.payload?.headers, 'Date'),
      unread: msg.labelIds?.includes('UNREAD') ?? false,
    };
  }

  /** Retrieves only the selected message. Attachments are not downloaded. */
  async getMessage(id: string): Promise<EmailDetail> {
    const msg = await this.request<{ id: string; internalDate?: string; payload?: GmailPart }>(
      `/messages/${encodeURIComponent(id)}?format=full`,
    );
    const headers = msg.payload?.headers;
    const bodies = extractBodies(msg.payload);
    const internal = msg.internalDate ? Number(msg.internalDate) : NaN;
    const preview = bodies.text?.trim() || (bodies.html ? htmlToPreviewText(bodies.html) : '');
    return {
      id: msg.id,
      from: header(headers, 'From') ?? '(unknown sender)',
      replyTo: header(headers, 'Reply-To'),
      subject: header(headers, 'Subject') ?? '(no subject)',
      date: header(headers, 'Date'),
      receivedAt: Number.isFinite(internal) ? new Date(internal).toISOString() : undefined,
      bodyText: bodies.text,
      bodyHtml: bodies.html,
      previewText: preview,
      attachmentCount: bodies.attachmentCount,
    };
  }

  /** Performs a GET; on 401 refreshes the token once and retries. */
  private async request<T>(path: string, retried = false): Promise<T> {
    const token = await this.getAccessToken(retried);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.gmailTimeoutMs);
    let res: Response;
    try {
      res = await this.fetchImpl(`${GMAIL_BASE}${path}`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
        signal: controller.signal,
      });
    } catch (e) {
      const aborted = (e as Error)?.name === 'AbortError';
      throw new GmailError(aborted ? 'timeout' : 'network', aborted ? 'Gmail took too long to respond.' : 'Could not reach Gmail. Check your connection.');
    } finally {
      clearTimeout(timer);
    }
    if (res.ok) return (await res.json()) as T;
    if (res.status === 401 && !retried) return this.request<T>(path, true);
    throw this.toError(res.status);
  }

  private toError(status: number): GmailError {
    switch (status) {
      case 401:
        return new GmailError('unauthorized', 'Your Gmail session expired or access was revoked. Connect Gmail again.');
      case 403:
        return new GmailError('forbidden', 'AI Shield does not have permission to read Gmail. Connect Gmail again and allow read access.');
      case 404:
        return new GmailError('notFound', 'This email no longer exists.');
      case 429:
        return new GmailError('rateLimited', 'Gmail is limiting requests right now. Try again in a minute.');
      default:
        return new GmailError('server', 'Gmail returned an error. Try again later.');
    }
  }
}

/** "\"Jane Doe\" <jane@x.com>" → { name: "Jane Doe", address: "jane@x.com" } */
export function parseFrom(from: string): { name?: string; address?: string } {
  const m = /^\s*"?([^"<]*?)"?\s*<([^>]+)>\s*$/.exec(from);
  if (m) return { name: m[1].trim() || undefined, address: m[2].trim() };
  return from.includes('@') ? { address: from.trim() } : { name: from.trim() };
}
