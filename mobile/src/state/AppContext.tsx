import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { ApiClient } from '../services/api';
import { GmailClient } from '../services/gmail';
import { AuthError, GoogleUser, googleAuth } from '../services/googleAuth';
import type { ServiceStatus } from '../types/api';

/**
 * Gmail connection state:
 * - checking: restoring a previous session
 * - unavailable: running in Expo Go or sign-in not configured (manual analysis still works)
 * - disconnected: not signed in
 * - missingScope: signed in, but Gmail read access was not granted
 * - connected: ready to list and read the selected email
 */
export type GmailStatus = 'checking' | 'unavailable' | 'disconnected' | 'missingScope' | 'connected';

export type AuthAdapter = typeof googleAuth;

type AppContextValue = {
  gmailStatus: GmailStatus;
  user: GoogleUser | null;
  authMessage: string | null;
  busy: boolean;
  connect: () => Promise<void>;
  grantGmailAccess: () => Promise<void>;
  disconnect: () => Promise<void>;
  api: ApiClient;
  gmail: GmailClient;
  serviceStatus: ServiceStatus | null;
  serviceError: string | null;
  refreshServiceStatus: () => Promise<void>;
  /** Incremented whenever history changes, so screens can reload. */
  historyVersion: number;
  bumpHistory: () => void;
};

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({
  children,
  auth = googleAuth,
  api: injectedApi,
  gmail: injectedGmail,
}: {
  children: React.ReactNode;
  auth?: AuthAdapter;
  api?: ApiClient;
  gmail?: GmailClient;
}) {
  const [gmailStatus, setGmailStatus] = useState<GmailStatus>('checking');
  const [user, setUser] = useState<GoogleUser | null>(null);
  const [authMessage, setAuthMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus | null>(null);
  const [serviceError, setServiceError] = useState<string | null>(null);
  const [historyVersion, setHistoryVersion] = useState(0);

  const api = useMemo(() => injectedApi ?? new ApiClient((force) => auth.getIdToken(force)), [injectedApi, auth]);
  const gmail = useMemo(() => injectedGmail ?? new GmailClient((force) => auth.getAccessToken(force)), [injectedGmail, auth]);

  const applySession = useCallback((u: GoogleUser, hasScope: boolean) => {
    setUser(u);
    setGmailStatus(hasScope ? 'connected' : 'missingScope');
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      if (!auth.isAvailable() || !auth.isConfigured()) {
        setGmailStatus('unavailable');
        setAuthMessage(
          !auth.isAvailable()
            ? 'Gmail connection needs the AI Shield development build. You can still paste an email manually.'
            : 'Google sign-in is not configured yet (EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID).',
        );
        return;
      }
      try {
        const session = await auth.restore();
        if (!active) return;
        if (session) applySession(session.user, session.hasGmailScope);
        else setGmailStatus('disconnected');
      } catch {
        if (active) setGmailStatus('disconnected');
      }
    })();
    return () => {
      active = false;
    };
  }, [auth, applySession]);

  const run = useCallback(async (fn: () => Promise<void>) => {
    setBusy(true);
    setAuthMessage(null);
    try {
      await fn();
    } catch (e) {
      setAuthMessage(e instanceof AuthError ? e.message : 'Something went wrong. Try again.');
    } finally {
      setBusy(false);
    }
  }, []);

  const connect = useCallback(
    () =>
      run(async () => {
        const result = await auth.signIn();
        if (result.type === 'success') applySession(result.user, result.hasGmailScope);
      }),
    [run, auth, applySession],
  );

  const grantGmailAccess = useCallback(
    () =>
      run(async () => {
        const granted = await auth.requestGmailScope();
        setGmailStatus(granted ? 'connected' : 'missingScope');
        if (!granted) setAuthMessage('Gmail read access was not granted, so emails cannot be listed.');
      }),
    [run, auth],
  );

  const disconnect = useCallback(
    () =>
      run(async () => {
        await auth.disconnect();
        setUser(null);
        setGmailStatus('disconnected');
      }),
    [run, auth],
  );

  const refreshServiceStatus = useCallback(async () => {
    try {
      setServiceStatus(await api.status());
      setServiceError(null);
    } catch (e) {
      setServiceStatus(null);
      setServiceError((e as Error).message);
    }
  }, [api]);

  useEffect(() => {
    refreshServiceStatus();
  }, [refreshServiceStatus]);

  const bumpHistory = useCallback(() => setHistoryVersion((v) => v + 1), []);

  const value = useMemo<AppContextValue>(
    () => ({
      gmailStatus,
      user,
      authMessage,
      busy,
      connect,
      grantGmailAccess,
      disconnect,
      api,
      gmail,
      serviceStatus,
      serviceError,
      refreshServiceStatus,
      historyVersion,
      bumpHistory,
    }),
    [gmailStatus, user, authMessage, busy, connect, grantGmailAccess, disconnect, api, gmail, serviceStatus, serviceError, refreshServiceStatus, historyVersion, bumpHistory],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
