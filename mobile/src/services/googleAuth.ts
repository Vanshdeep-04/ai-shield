import { config } from '../config';
import { singleFlight } from '../utils/singleFlight';

/**
 * Wrapper around @react-native-google-signin/google-signin (official Google Sign-In SDK).
 *
 * - Never asks for or sees the Gmail password; Google's own consent screen is used.
 * - Requests only the gmail.readonly scope (plus basic profile for the ID token).
 * - Tokens stay inside Google Play services; this app does not persist them.
 * The native module is absent in Expo Go, so the import is guarded and the app falls back to
 * manual analysis there.
 */

type GoogleSigninModule = typeof import('@react-native-google-signin/google-signin');

let mod: GoogleSigninModule | null = null;
try {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  mod = require('@react-native-google-signin/google-signin') as GoogleSigninModule;
} catch {
  mod = null;
}

export type GoogleUser = { email: string; name?: string | null; photo?: string | null };

export type SignInResult =
  | { type: 'success'; user: GoogleUser; hasGmailScope: boolean }
  | { type: 'cancelled' };

export class AuthError extends Error {
  constructor(
    public kind: 'unavailable' | 'notConfigured' | 'playServices' | 'inProgress' | 'failed',
    message: string,
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

let configured = false;

// The SDK permits one getTokens / signInSilently call at a time; share in-flight calls instead of
// starting overlapping ones (the inbox loads many messages in parallel).
const getTokensOnce = singleFlight(() => mod!.GoogleSignin.getTokens());
const signInSilentlyOnce = singleFlight(() => mod!.GoogleSignin.signInSilently());
const refreshAccessTokenOnce = singleFlight(async () => {
  const stale = await getTokensOnce();
  await mod!.GoogleSignin.clearCachedAccessToken(stale.accessToken);
  return getTokensOnce();
});

export const googleAuth = {
  isAvailable(): boolean {
    return mod !== null && typeof mod.GoogleSignin?.configure === 'function';
  },

  isConfigured(): boolean {
    return config.googleWebClientId.length > 0;
  },

  ensureConfigured(): GoogleSigninModule {
    if (!mod || !this.isAvailable()) {
      throw new AuthError('unavailable', 'Google sign-in needs the AI Shield development build (it is not available in Expo Go).');
    }
    if (!this.isConfigured()) {
      throw new AuthError('notConfigured', 'Google sign-in is not configured. Set EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID (see docs/GMAIL_OAUTH.md).');
    }
    if (!configured) {
      mod.GoogleSignin.configure({
        webClientId: config.googleWebClientId,
        scopes: [config.gmailScope],
        offlineAccess: false,
      });
      configured = true;
    }
    return mod;
  },

  async signIn(): Promise<SignInResult> {
    const m = this.ensureConfigured();
    try {
      await m.GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
      const response = await m.GoogleSignin.signIn();
      if (m.isSuccessResponse(response)) {
        return { type: 'success', user: toUser(response.data.user), hasGmailScope: response.data.scopes.includes(config.gmailScope) };
      }
      return { type: 'cancelled' };
    } catch (e) {
      throw mapError(m, e);
    }
  },

  /** Restores a previous session without UI. Returns null if the user has not signed in before. */
  async restore(): Promise<{ user: GoogleUser; hasGmailScope: boolean } | null> {
    const m = this.ensureConfigured();
    try {
      const response = await signInSilentlyOnce();
      if (response.type === 'success') {
        return { user: toUser(response.data.user), hasGmailScope: response.data.scopes.includes(config.gmailScope) };
      }
      return null;
    } catch (e) {
      throw mapError(m, e);
    }
  },

  /** Asks the user to grant gmail.readonly if it was not granted at sign-in. */
  async requestGmailScope(): Promise<boolean> {
    const m = this.ensureConfigured();
    try {
      const response = await m.GoogleSignin.addScopes({ scopes: [config.gmailScope] });
      return !!response && m.isSuccessResponse(response) && response.data.scopes.includes(config.gmailScope);
    } catch (e) {
      throw mapError(m, e);
    }
  },

  /** Returns a Gmail access token. With forceRefresh, the cached token is cleared first. */
  async getAccessToken(forceRefresh = false): Promise<string> {
    this.ensureConfigured();
    return (forceRefresh ? await refreshAccessTokenOnce() : await getTokensOnce()).accessToken;
  },

  /** Returns a Google ID token for the AI Shield backend. With forceRefresh, a silent sign-in renews it. */
  async getIdToken(forceRefresh = false): Promise<string | null> {
    if (!mod || !this.isAvailable() || !this.isConfigured()) return null;
    const m = this.ensureConfigured();
    if (m.GoogleSignin.getCurrentUser() === null && !m.GoogleSignin.hasPreviousSignIn()) {
      console.warn('[AI Shield] No Google user is signed in; backend requests will have no ID token.');
      return null;
    }
    try {
      if (forceRefresh || m.GoogleSignin.getCurrentUser() === null) {
        const silent = await signInSilentlyOnce();
        if (silent.type === 'success' && silent.data.idToken) return silent.data.idToken;
      }
      const token = (await getTokensOnce()).idToken;
      if (!token) console.warn('[AI Shield] Google returned no ID token (check the Web client ID).');
      return token;
    } catch (e) {
      // Diagnostic only: error code/message from the Google SDK, never a token.
      const code = m.isErrorWithCode(e) ? String(e.code) : 'unknown';
      console.warn(`[AI Shield] Could not get a Google ID token (code ${code}): ${(e as Error)?.message ?? e}`);
      return null;
    }
  },

  /** Signs out on this device. */
  async signOut(): Promise<void> {
    const m = this.ensureConfigured();
    await m.GoogleSignin.signOut();
  },

  /** Disconnects: revokes AI Shield's Gmail access at Google, then signs out. */
  async disconnect(): Promise<void> {
    const m = this.ensureConfigured();
    try {
      await m.GoogleSignin.revokeAccess();
    } finally {
      await m.GoogleSignin.signOut();
    }
  },
};

function toUser(u: { email: string; name: string | null; photo: string | null }): GoogleUser {
  return { email: u.email, name: u.name, photo: u.photo };
}

function mapError(m: GoogleSigninModule, e: unknown): AuthError {
  if (m.isErrorWithCode(e)) {
    if (e.code === m.statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
      return new AuthError('playServices', 'Google Play services is missing or out of date on this device.');
    }
    if (e.code === m.statusCodes.IN_PROGRESS) {
      return new AuthError('inProgress', 'Sign-in is already in progress.');
    }
    if (String(e.code) === '10' || /DEVELOPER_ERROR/i.test(String(e.message))) {
      return new AuthError('failed', 'Google sign-in is misconfigured (DEVELOPER_ERROR). Check the Android OAuth client and SHA-1 in docs/GMAIL_OAUTH.md.');
    }
  }
  return new AuthError('failed', 'Google sign-in failed. Check your connection and try again.');
}
