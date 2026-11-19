/** Runtime configuration from EXPO_PUBLIC_* variables (public, never secret). */

function trimSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

export const config = {
  apiBaseUrl: trimSlash(process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://10.0.2.2:8080'),
  googleWebClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ?? '',
  /** Narrowest Gmail scope that can read message bodies. Read-only: cannot send, delete or modify. */
  gmailScope: 'https://www.googleapis.com/auth/gmail.readonly',
  requestTimeoutMs: 60_000,
  gmailTimeoutMs: 20_000,
};
