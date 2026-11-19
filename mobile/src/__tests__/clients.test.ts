import { ApiClient, ApiError } from '../services/api';
import { GmailClient, GmailError, parseFrom } from '../services/gmail';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

describe('ApiClient', () => {
  const base = 'http://backend.test';

  it('sends the Google ID token (not a Gmail token) and parses the result', async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(200, { riskLevel: 'LOW' }));
    const api = new ApiClient(async () => 'id-token-1', base, fetchMock as unknown as typeof fetch);
    const result = await api.analyze({ source: 'MANUAL', bodyText: 'hi' });
    expect(result).toEqual({ riskLevel: 'LOW' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${base}/api/v1/analyses`);
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer id-token-1');
    expect(JSON.parse(init.body)).toEqual({ source: 'MANUAL', bodyText: 'hi' });
  });

  it('refreshes the ID token once on 401, then gives up', async () => {
    const getToken = jest.fn(async (force: boolean) => (force ? 'fresh' : 'stale'));
    const fetchMock = jest.fn().mockResolvedValueOnce(jsonResponse(401, {})).mockResolvedValueOnce(jsonResponse(200, { items: [] }));
    const api = new ApiClient(getToken, base, fetchMock as unknown as typeof fetch);
    await api.history();
    expect(getToken).toHaveBeenNthCalledWith(1, false);
    expect(getToken).toHaveBeenNthCalledWith(2, true);
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer fresh');

    const always401 = jest.fn().mockResolvedValue(jsonResponse(401, {}));
    const api2 = new ApiClient(async () => 't', base, always401 as unknown as typeof fetch);
    await expect(api2.history()).rejects.toMatchObject({ kind: 'unauthorized' });
    expect(always401).toHaveBeenCalledTimes(2);
  });

  it('omits the Authorization header when not signed in (dev mode)', async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(200, {}));
    const api = new ApiClient(async () => null, base, fetchMock as unknown as typeof fetch);
    await api.history();
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('maps validation, rate-limit, size and server errors to readable messages', async () => {
    const cases: [number, unknown, string][] = [
      [400, { errors: [{ field: 'bodyText', message: 'bodyText or bodyHtml must contain message content' }] }, 'validation'],
      [429, { detail: 'Too many analyses' }, 'rateLimited'],
      [413, {}, 'tooLarge'],
      [503, {}, 'unavailable'],
      [500, {}, 'server'],
      [404, {}, 'notFound'],
    ];
    for (const [status, body, kind] of cases) {
      const api = new ApiClient(async () => null, base, jest.fn().mockResolvedValue(jsonResponse(status, body)) as unknown as typeof fetch);
      const err = await api.analyze({ source: 'MANUAL', bodyText: 'x' }).catch((e) => e);
      expect(err).toBeInstanceOf(ApiError);
      expect(err.kind).toBe(kind);
    }
    const api = new ApiClient(async () => null, base, jest.fn().mockResolvedValue(jsonResponse(400, cases[0][1])) as unknown as typeof fetch);
    await expect(api.analyze({ source: 'MANUAL' })).rejects.toThrow('bodyText or bodyHtml must contain message content');
  });

  it('reports network failures with the server address', async () => {
    const api = new ApiClient(async () => null, base, jest.fn().mockRejectedValue(new TypeError('Network request failed')) as unknown as typeof fetch);
    await expect(api.status()).rejects.toMatchObject({ kind: 'network', message: expect.stringContaining(base) });
  });

  it('times out and supports cancellation', async () => {
    const hanging = jest.fn(
      (_url: string, init: RequestInit) =>
        new Promise<Response>((_, reject) => {
          init.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
        }),
    );
    const timeoutApi = new ApiClient(async () => null, base, hanging as unknown as typeof fetch, 20);
    await expect(timeoutApi.analyze({ source: 'MANUAL', bodyText: 'x' })).rejects.toMatchObject({ kind: 'timeout' });

    const controller = new AbortController();
    const api = new ApiClient(async () => null, base, hanging as unknown as typeof fetch, 10_000);
    const promise = api.analyze({ source: 'MANUAL', bodyText: 'x' }, controller.signal);
    controller.abort();
    await expect(promise).rejects.toMatchObject({ kind: 'cancelled' });
  });
});

describe('GmailClient', () => {
  it('lists messages using metadata only (no bodies)', async () => {
    const fetchMock = jest.fn(async (url: string) => {
      if (url.includes('/messages?')) return jsonResponse(200, { messages: [{ id: 'm1' }], nextPageToken: 'p2' });
      return jsonResponse(200, {
        id: 'm1',
        labelIds: ['INBOX', 'UNREAD'],
        payload: { headers: [{ name: 'From', value: 'A <a@x.com>' }, { name: 'Subject', value: 'Hello' }, { name: 'Date', value: 'Tue, 18 Nov 2026 10:00:00 +0000' }] },
      });
    });
    const gmail = new GmailClient(async () => 'access', fetchMock as unknown as typeof fetch);
    const res = await gmail.listMessages({ query: 'from:bank' });
    expect(res.messages).toEqual([{ id: 'm1', from: 'A <a@x.com>', subject: 'Hello', date: 'Tue, 18 Nov 2026 10:00:00 +0000', unread: true }]);
    expect(res.nextPageToken).toBe('p2');
    const urls = fetchMock.mock.calls.map((c) => c[0] as string);
    expect(urls[0]).toContain('q=from%3Abank');
    expect(urls[1]).toContain('format=metadata');
    expect(urls.some((u) => u.includes('format=full'))).toBe(false);
    expect((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].headers).toMatchObject({ Authorization: 'Bearer access' });
  });

  it('fetches the full selected message and parses it', async () => {
    const data = Buffer.from('Body text').toString('base64');
    const fetchMock = jest.fn().mockResolvedValue(
      jsonResponse(200, {
        id: 'm1',
        internalDate: '1763460000000',
        payload: {
          mimeType: 'text/plain',
          headers: [{ name: 'From', value: 'x@y.com' }, { name: 'Reply-To', value: 'r@z.com' }, { name: 'Subject', value: 'S' }],
          body: { data },
        },
      }),
    );
    const gmail = new GmailClient(async () => 'access', fetchMock as unknown as typeof fetch);
    const m = await gmail.getMessage('m1');
    expect(m).toMatchObject({ from: 'x@y.com', replyTo: 'r@z.com', subject: 'S', bodyText: 'Body text', attachmentCount: 0 });
    expect(m.receivedAt).toBe(new Date(1763460000000).toISOString());
  });

  it('refreshes an expired access token once, and reports revoked access', async () => {
    const getToken = jest.fn(async (force: boolean) => (force ? 'new' : 'old'));
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(401, {}));
    const gmail = new GmailClient(getToken, fetchMock as unknown as typeof fetch);
    const err = await gmail.getMessage('m1').catch((e) => e);
    expect(err).toBeInstanceOf(GmailError);
    expect(err.kind).toBe('unauthorized');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(getToken).toHaveBeenLastCalledWith(true);
  });

  it('maps missing permission and rate limits', async () => {
    for (const [status, kind] of [[403, 'forbidden'], [429, 'rateLimited'], [404, 'notFound'], [500, 'server']] as const) {
      const gmail = new GmailClient(async () => 't', jest.fn().mockResolvedValue(jsonResponse(status, {})) as unknown as typeof fetch);
      await expect(gmail.getMessage('m')).rejects.toMatchObject({ kind });
    }
  });
});

describe('parseFrom', () => {
  it('splits display name and address', () => {
    expect(parseFrom('"Jane Doe" <jane@x.com>')).toEqual({ name: 'Jane Doe', address: 'jane@x.com' });
    expect(parseFrom('jane@x.com')).toEqual({ address: 'jane@x.com' });
    expect(parseFrom('<jane@x.com>')).toEqual({ name: undefined, address: 'jane@x.com' });
  });
});

describe('ApiClient 401 messages', () => {
  it('distinguishes "not signed in" from "sign-in rejected by the server"', async () => {
    const noToken = new ApiClient(async () => null, 'http://b', jest.fn().mockResolvedValue(jsonResponse(401, {})) as unknown as typeof fetch);
    await expect(noToken.history()).rejects.toThrow(/Connect Gmail on the Home screen/);
    const rejected = new ApiClient(async () => 'tok', 'http://b', jest.fn().mockResolvedValue(jsonResponse(401, {})) as unknown as typeof fetch);
    await expect(rejected.history()).rejects.toThrow(/did not accept your Google sign-in/);
  });
});
