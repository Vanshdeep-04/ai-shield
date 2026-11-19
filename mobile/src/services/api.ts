import { config } from '../config';
import type {
  AnalysisRequest,
  AnalysisResponse,
  Feedback,
  FeedbackVerdict,
  HistoryItem,
  Page,
  ServiceStatus,
} from '../types/api';

/**
 * Client for the AI Shield backend. Authenticates with a Google ID token (never the Gmail
 * access token). The app holds no AI provider key: all AI calls happen on the server.
 */

export type ApiErrorKind =
  | 'network'
  | 'timeout'
  | 'cancelled'
  | 'unauthorized'
  | 'validation'
  | 'notFound'
  | 'tooLarge'
  | 'rateLimited'
  | 'unavailable'
  | 'server';

export class ApiError extends Error {
  constructor(public kind: ApiErrorKind, message: string, public status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

export type IdTokenProvider = (forceRefresh: boolean) => Promise<string | null>;

type Fetch = typeof fetch;

export class ApiClient {
  constructor(
    private getIdToken: IdTokenProvider,
    private baseUrl: string = config.apiBaseUrl,
    private fetchImpl: Fetch = fetch,
    private timeoutMs: number = config.requestTimeoutMs,
  ) {}

  status(signal?: AbortSignal): Promise<ServiceStatus> {
    return this.request('GET', '/api/v1/status', undefined, signal, false);
  }

  analyze(body: AnalysisRequest, signal?: AbortSignal): Promise<AnalysisResponse> {
    return this.request('POST', '/api/v1/analyses', body, signal);
  }

  history(page = 0, size = 20, signal?: AbortSignal): Promise<Page<HistoryItem>> {
    return this.request('GET', `/api/v1/analyses?page=${page}&size=${size}`, undefined, signal);
  }

  getAnalysis(id: string, signal?: AbortSignal): Promise<AnalysisResponse> {
    return this.request('GET', `/api/v1/analyses/${encodeURIComponent(id)}`, undefined, signal);
  }

  deleteAnalysis(id: string): Promise<void> {
    return this.request('DELETE', `/api/v1/analyses/${encodeURIComponent(id)}`);
  }

  deleteAllHistory(): Promise<{ deleted: number }> {
    return this.request('DELETE', '/api/v1/analyses');
  }

  submitFeedback(id: string, verdict: FeedbackVerdict, comment?: string): Promise<Feedback> {
    return this.request('PUT', `/api/v1/analyses/${encodeURIComponent(id)}/feedback`, { verdict, comment: comment || undefined });
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    externalSignal?: AbortSignal,
    authenticated = true,
    retried = false,
  ): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (authenticated) {
      const token = await this.getIdToken(retried);
      if (token) headers.Authorization = `Bearer ${token}`;
    }
    if (externalSignal?.aborted) {
      throw new ApiError('cancelled', 'Cancelled.');
    }

    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, this.timeoutMs);
    const onExternalAbort = () => controller.abort();
    externalSignal?.addEventListener('abort', onExternalAbort);

    let res: Response;
    try {
      res = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (e) {
      if ((e as Error)?.name === 'AbortError' || controller.signal.aborted) {
        if (timedOut) throw new ApiError('timeout', 'The AI Shield server took too long to respond. Try again.');
        throw new ApiError('cancelled', 'Cancelled.');
      }
      throw new ApiError('network', `Can't reach the AI Shield server at ${this.baseUrl}. Check that it is running and that your phone is on the same network.`);
    } finally {
      clearTimeout(timer);
      externalSignal?.removeEventListener('abort', onExternalAbort);
    }

    if (res.status === 401 && authenticated && !retried) {
      return this.request<T>(method, path, body, externalSignal, authenticated, true);
    }
    if (res.status === 204) return undefined as T;
    if (res.ok) return (await res.json()) as T;
    throw await toApiError(res, !!headers.Authorization);
  }
}

async function toApiError(res: Response, sentToken = false): Promise<ApiError> {
  let detail: string | undefined;
  let fieldErrors: string[] = [];
  try {
    const problem = (await res.json()) as { detail?: string; errors?: { message?: string }[] };
    detail = problem.detail;
    fieldErrors = (problem.errors ?? []).map((e) => e.message).filter((m): m is string => !!m);
  } catch {
    // non-JSON error body
  }
  switch (res.status) {
    case 400:
      return new ApiError('validation', fieldErrors.length ? fieldErrors.join('\n') : detail ?? 'The request was invalid.', 400);
    case 401:
      return new ApiError(
        'unauthorized',
        sentToken
          ? 'The AI Shield server did not accept your Google sign-in. Check that the app and server use the same OAuth Web client ID, then sign out and connect Gmail again.'
          : 'Sign in with Google (Connect Gmail on the Home screen) to use AI Shield.',
        401,
      );
    case 404:
      return new ApiError('notFound', 'That analysis was not found. It may have been deleted.', 404);
    case 411:
    case 413:
      return new ApiError('tooLarge', 'This email is too large to analyse.', res.status);
    case 429:
      return new ApiError('rateLimited', detail ?? 'Too many analyses in a short time. Wait a minute and try again.', 429);
    case 503:
      return new ApiError('unavailable', detail ?? 'The service is temporarily unavailable.', 503);
    default:
      return new ApiError('server', 'The AI Shield server had a problem. Try again later.', res.status);
  }
}
