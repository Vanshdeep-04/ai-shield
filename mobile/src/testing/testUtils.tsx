import { render } from '@testing-library/react-native';
import React from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import RootNavigator from '../navigation/RootNavigator';
import type { ApiClient } from '../services/api';
import type { EmailDetail, EmailSummary, GmailClient } from '../services/gmail';
import { AppProvider, AuthAdapter } from '../state/AppContext';
import { ThemeProvider } from '../theme/ThemeContext';
import type { AnalysisResponse, HistoryItem, Page } from '../types/api';

export const highRisk: AnalysisResponse = {
  id: 'a-1',
  createdAt: '2026-11-18T10:15:00Z',
  saved: true,
  source: 'GMAIL',
  message: { senderDomain: 'secure-mailer.example', subjectPreview: 'URGENT: verify your account' },
  riskLevel: 'HIGH',
  riskScore: 86,
  explanation: 'AI Shield rates this email HIGH risk (score 86/100). It asks for an OTP.',
  recommendedAction: 'Do not click any links. Report it as phishing.',
  ai: {
    status: 'COMPLETED',
    riskLevel: 'HIGH',
    riskScore: 80,
    isPhishing: true,
    indicators: [{ category: 'SENSITIVE_DATA_REQUEST', description: 'Asks for your OTP', evidence: 'enter your OTP' }],
    benignFactors: [],
  },
  urlAnalysis: {
    score: 75,
    linksChecked: 1,
    findings: [
      {
        url: 'http://192.168.4.20/login',
        host: '192.168.4.20',
        displayText: 'https://www.hdfcbank.com',
        score: 75,
        indicators: [{ code: 'IP_ADDRESS_HOST', description: 'Link points to a raw IP address instead of a domain name', weight: 35 }],
      },
    ],
    unavailableChecks: ['Domain reputation / blocklist lookup: no threat-intelligence service is configured.'],
  },
  senderChecks: { score: 20, senderAvailable: true, findings: [{ code: 'REPLY_TO_DOMAIN_MISMATCH', description: "Replies would go to a different domain than the sender's", weight: 20 }] },
  componentScores: { ai: 80, url: 75, metadata: 20 },
  evidence: [
    { source: 'AI_CONTENT_ANALYSIS', type: 'AI_INTERPRETATION', code: 'SENSITIVE_DATA_REQUEST', description: 'Asks for your OTP', detail: 'enter your OTP' },
    { source: 'URL_ANALYSIS', type: 'OBSERVED', code: 'IP_ADDRESS_HOST', description: 'Link points to a raw IP address instead of a domain name', detail: '192.168.4.20' },
    { source: 'SENDER_METADATA', type: 'OBSERVED', code: 'REPLY_TO_DOMAIN_MISMATCH', description: "Replies would go to a different domain than the sender's" },
  ],
  limitations: ['The score is a heuristic risk indicator produced by AI Shield, not a guaranteed probability of phishing.'],
};

export const unknownRisk: AnalysisResponse = {
  ...highRisk,
  id: 'a-2',
  riskLevel: 'UNKNOWN',
  riskScore: undefined,
  explanation: 'AI Shield could not complete a reliable assessment of this email.',
  recommendedAction: 'Treat the email with caution.',
  ai: { status: 'UNAVAILABLE', unavailableReason: "The AI service's free-tier quota is used up. Please try again later." },
  evidence: [],
  urlAnalysis: { score: 0, linksChecked: 0, findings: [], unavailableChecks: [] },
  senderChecks: { score: 0, senderAvailable: true, findings: [] },
  componentScores: { url: 0, metadata: 0 },
};

export function page<T>(items: T[]): Page<T> {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

export const historyItem: HistoryItem = {
  id: 'a-1',
  createdAt: '2026-11-18T10:15:00Z',
  source: 'GMAIL',
  senderDomain: 'secure-mailer.example',
  subjectPreview: 'URGENT: verify your account',
  riskLevel: 'HIGH',
  riskScore: 86,
  aiStatus: 'COMPLETED',
};

export function fakeApi(overrides: Partial<Record<keyof ApiClient, jest.Mock>> = {}) {
  return {
    status: jest.fn().mockResolvedValue({ service: 'ai-shield', apiVersion: 'v1', aiConfigured: true, authMode: 'dev' }),
    analyze: jest.fn().mockResolvedValue(highRisk),
    history: jest.fn().mockResolvedValue(page([])),
    getAnalysis: jest.fn().mockResolvedValue(highRisk),
    deleteAnalysis: jest.fn().mockResolvedValue(undefined),
    deleteAllHistory: jest.fn().mockResolvedValue({ deleted: 1 }),
    submitFeedback: jest.fn().mockResolvedValue({ verdict: 'ACCURATE' }),
    ...overrides,
  };
}

export const inboxMessage: EmailSummary = { id: 'm1', from: '"HDFC Alerts" <alerts@secure-mailer.example>', subject: 'URGENT: verify your account', date: 'Tue, 18 Nov 2026 10:15:00 +0000', unread: true };

export const emailDetail: EmailDetail = {
  id: 'm1',
  from: inboxMessage.from,
  replyTo: 'verify@collect-desk.example',
  subject: inboxMessage.subject,
  date: inboxMessage.date,
  receivedAt: '2026-11-18T10:15:00.000Z',
  bodyText: 'Dear customer, enter your OTP at the link below.',
  previewText: 'Dear customer, enter your OTP at the link below.',
  attachmentCount: 0,
};

export function fakeGmail() {
  return {
    listMessages: jest.fn().mockResolvedValue({ messages: [inboxMessage] }),
    getMessage: jest.fn().mockResolvedValue(emailDetail),
  };
}

export function fakeAuth(state: 'connected' | 'disconnected' | 'unavailable' = 'disconnected') {
  const user = { email: 'tester@example.com', name: 'Tester', photo: null };
  return {
    isAvailable: jest.fn(() => state !== 'unavailable'),
    isConfigured: jest.fn(() => true),
    ensureConfigured: jest.fn(),
    signIn: jest.fn().mockResolvedValue({ type: 'success', user, hasGmailScope: true }),
    restore: jest.fn().mockResolvedValue(state === 'connected' ? { user, hasGmailScope: true } : null),
    requestGmailScope: jest.fn().mockResolvedValue(true),
    getAccessToken: jest.fn().mockResolvedValue('access'),
    getIdToken: jest.fn().mockResolvedValue('id'),
    signOut: jest.fn().mockResolvedValue(undefined),
    disconnect: jest.fn().mockResolvedValue(undefined),
  };
}

export async function renderApp(opts: {
  api?: ReturnType<typeof fakeApi>;
  gmail?: ReturnType<typeof fakeGmail>;
  auth?: ReturnType<typeof fakeAuth>;
  theme?: 'light' | 'dark';
} = {}) {
  const api = opts.api ?? fakeApi();
  const gmail = opts.gmail ?? fakeGmail();
  const auth = opts.auth ?? fakeAuth();
  const utils = await render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
      <ThemeProvider initialPreference={opts.theme ?? 'light'}>
        <AppProvider auth={auth as unknown as AuthAdapter} api={api as unknown as ApiClient} gmail={gmail as unknown as GmailClient}>
          <RootNavigator />
        </AppProvider>
      </ThemeProvider>
    </SafeAreaProvider>,
  );
  return { ...utils, api, gmail, auth };
}
