import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';

import { ApiError } from '../services/api';
import { fakeApi, fakeAuth, fakeGmail, highRisk, historyItem, page, renderApp, unknownRisk } from '../testing/testUtils';

describe('Home screen', () => {
  it('shows branding, a disconnected Gmail state and the manual option', async () => {
    await renderApp({ auth: fakeAuth('disconnected') });
    expect(await screen.findByText('Check whether an email is phishing or a scam before you act on it.')).toBeTruthy();
    expect(await screen.findByTestId('connect-gmail')).toBeTruthy();
    expect(screen.getByTestId('manual-input')).toBeTruthy();
    expect(screen.queryByTestId('choose-email')).toBeNull();
  });

  it('connects Gmail through Google sign-in', async () => {
    const { auth } = await renderApp({ auth: fakeAuth('disconnected') });
    await fireEvent.press(await screen.findByTestId('connect-gmail'));
    expect(auth.signIn).toHaveBeenCalled();
    expect(await screen.findByTestId('choose-email')).toBeTruthy();
    expect(screen.getByTestId('gmail-status').props.children.join('')).toContain('tester@example.com');
  });

  it('explains when Gmail is unavailable but keeps manual analysis', async () => {
    await renderApp({ auth: fakeAuth('unavailable') });
    expect(await screen.findByTestId('auth-message')).toBeTruthy();
    expect(screen.queryByTestId('connect-gmail')).toBeNull();
    expect(screen.getByTestId('manual-input')).toBeTruthy();
  });

  it('shows an empty recent-analyses state instead of fabricated data', async () => {
    await renderApp();
    expect(await screen.findByTestId('recent-empty')).toBeTruthy();
  });

  it('lists real recent analyses from the backend', async () => {
    await renderApp({ api: fakeApi({ history: jest.fn().mockResolvedValue(page([historyItem])) }) });
    expect(await screen.findByText('URGENT: verify your account')).toBeTruthy();
    expect(screen.getByLabelText('Risk level: High risk')).toBeTruthy();
  });

  it('reports an unreachable backend', async () => {
    await renderApp({ api: fakeApi({ status: jest.fn().mockRejectedValue(new ApiError('network', "Can't reach the AI Shield server")) }) });
    expect(await screen.findByTestId('service-error')).toBeTruthy();
  });
});

describe('Manual analysis and results', () => {
  it('validates empty input', async () => {
    const { api } = await renderApp();
    await fireEvent.press(await screen.findByTestId('manual-input'));
    await fireEvent.press(await screen.findByTestId('manual-analyse'));
    expect(await screen.findByTestId('manual-validation')).toBeTruthy();
    expect(api.analyze).not.toHaveBeenCalled();
  });

  it('submits text and renders a HIGH result with evidence, links and action', async () => {
    const { api } = await renderApp();
    await fireEvent.press(await screen.findByTestId('manual-input'));
    await fireEvent.changeText(await screen.findByTestId('manual-body'), 'Enter your OTP now');
    await fireEvent.press(screen.getByTestId('manual-analyse'));

    expect(await screen.findByTestId('result-screen')).toBeTruthy();
    expect(api.analyze).toHaveBeenCalledWith({ source: 'MANUAL', bodyText: 'Enter your OTP now' }, expect.anything());
    expect(screen.getByLabelText('Risk level: High risk')).toBeTruthy();
    expect(screen.getByTestId('result-score').props.children).toBe(86);
    expect(screen.getByText(highRisk.explanation)).toBeTruthy();
    expect(screen.getByText(highRisk.recommendedAction)).toBeTruthy();
    expect(screen.getByText('AI interpretation')).toBeTruthy();
    expect(screen.getByText("Observed by AI Shield's checks")).toBeTruthy();
    expect(screen.getAllByText('192.168.4.20').length).toBeGreaterThan(0);
  });

  it('records feedback', async () => {
    const { api } = await renderApp();
    await fireEvent.press(await screen.findByTestId('manual-input'));
    await fireEvent.changeText(await screen.findByTestId('manual-body'), 'text');
    await fireEvent.press(screen.getByTestId('manual-analyse'));
    await fireEvent.press(await screen.findByTestId('feedback-FALSE_POSITIVE'));
    await fireEvent.press(screen.getByTestId('feedback-submit'));
    expect(await screen.findByTestId('feedback-saved')).toBeTruthy();
    expect(api.submitFeedback).toHaveBeenCalledWith('a-1', 'FALSE_POSITIVE', undefined);
  });

  it('renders UNKNOWN results without a score and with the AI failure reason', async () => {
    await renderApp({ api: fakeApi({ analyze: jest.fn().mockResolvedValue(unknownRisk) }) });
    await fireEvent.press(await screen.findByTestId('manual-input'));
    await fireEvent.changeText(await screen.findByTestId('manual-body'), 'text');
    await fireEvent.press(screen.getByTestId('manual-analyse'));
    expect(await screen.findByLabelText('Risk level: Unknown')).toBeTruthy();
    expect(screen.getByTestId('result-no-score')).toBeTruthy();
    expect(screen.queryByTestId('result-score')).toBeNull();
    expect(screen.getByTestId('ai-unavailable')).toBeTruthy();
    expect(screen.getByText(/free-tier quota is used up/)).toBeTruthy();
  });

  it('shows server errors and allows retry', async () => {
    const analyze = jest.fn().mockRejectedValueOnce(new ApiError('rateLimited', 'Too many analyses in a short time.')).mockResolvedValueOnce(highRisk);
    await renderApp({ api: fakeApi({ analyze }) });
    await fireEvent.press(await screen.findByTestId('manual-input'));
    await fireEvent.changeText(await screen.findByTestId('manual-body'), 'text');
    await fireEvent.press(screen.getByTestId('manual-analyse'));
    expect(await screen.findByText('Too many analyses in a short time.')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('manual-analyse'));
    expect(await screen.findByTestId('result-screen')).toBeTruthy();
  });
});

describe('Gmail selection flow', () => {
  it('lists emails, previews the selected one and analyses it', async () => {
    const { api, gmail } = await renderApp({ auth: fakeAuth('connected') });
    await fireEvent.press(await screen.findByTestId('choose-email'));
    await fireEvent.press(await screen.findByTestId('email-m1'));
    expect(gmail.getMessage).toHaveBeenCalledWith('m1');
    expect(await screen.findByTestId('email-preview-text')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('analyse-button'));
    expect(await screen.findByTestId('result-screen')).toBeTruthy();
    const request = api.analyze.mock.calls[0][0];
    expect(request.source).toBe('GMAIL');
    expect(request.replyTo).toBe('verify@collect-desk.example');
    expect(JSON.stringify(request)).not.toContain('access'); // no Gmail token in the payload
  });

  it('shows loading progress and lets the user cancel', async () => {
    const analyze = jest.fn(
      (_req: unknown, signal: AbortSignal) =>
        new Promise((_, reject) => signal.addEventListener('abort', () => reject(new ApiError('cancelled', 'Cancelled.')))),
    );
    await renderApp({ auth: fakeAuth('connected'), api: fakeApi({ analyze }) });
    await fireEvent.press(await screen.findByTestId('choose-email'));
    await fireEvent.press(await screen.findByTestId('email-m1'));
    const analyseButton = await screen.findByTestId('analyse-button');
    // Not awaited: the press starts an analysis that only ends when it is cancelled.
    void fireEvent.press(analyseButton);
    expect(await screen.findByTestId('analysis-progress')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('cancel-analysis'));
    expect(await screen.findByTestId('analyse-button')).toBeTruthy();
    expect(screen.queryByTestId('result-screen')).toBeNull();
  });

  it('shows Gmail errors with a reconnect option', async () => {
    const gmail = fakeGmail();
    const { GmailError } = jest.requireActual('../services/gmail');
    gmail.listMessages.mockRejectedValue(new GmailError('unauthorized', 'Your Gmail session expired or access was revoked. Connect Gmail again.'));
    await renderApp({ auth: fakeAuth('connected'), gmail });
    await fireEvent.press(await screen.findByTestId('choose-email'));
    expect(await screen.findByTestId('inbox-error')).toBeTruthy();
    expect(screen.getByText('Connect Gmail again')).toBeTruthy();
  });
});

describe('History', () => {
  it('shows a helpful empty state', async () => {
    await renderApp();
    await fireEvent.press(await screen.findByText('See all'));
    expect(await screen.findByText('No analyses yet')).toBeTruthy();
  });

  it('lists items and opens a stored result', async () => {
    const api = fakeApi({ history: jest.fn().mockResolvedValue(page([historyItem])) });
    await renderApp({ api });
    await fireEvent.press(await screen.findByText('See all'));
    await fireEvent.press(await screen.findByTestId('history-a-1'));
    expect(await screen.findByTestId('result-screen')).toBeTruthy();
    expect(api.getAnalysis).toHaveBeenCalledWith('a-1');
  });

  it('shows database errors', async () => {
    const history = jest.fn().mockRejectedValue(new ApiError('unavailable', 'Analysis history is temporarily unavailable.'));
    await renderApp({ api: fakeApi({ history }) });
    await fireEvent.press(await screen.findByText('See all'));
    expect(await screen.findByTestId('history-error')).toBeTruthy();
  });

  it('deletes all history after confirmation', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation((_t, _m, buttons) => {
      buttons?.find((b) => b.style === 'destructive')?.onPress?.();
    });
    const api = fakeApi({ history: jest.fn().mockResolvedValue(page([historyItem])) });
    await renderApp({ api });
    await fireEvent.press(await screen.findByText('See all'));
    await fireEvent.press(await screen.findByTestId('delete-all'));
    await waitFor(() => expect(api.deleteAllHistory).toHaveBeenCalled());
    alertSpy.mockRestore();
  });
});

describe('Dark mode', () => {
  it('toggles between light and dark from the header', async () => {
    await renderApp({ theme: 'light' });
    const toggle = (await screen.findAllByTestId('theme-toggle'))[0];
    expect(toggle.props.accessibilityState.checked).toBe(false);
    await act(async () => {
      fireEvent.press(toggle);
    });
    await waitFor(() => expect(screen.getAllByTestId('theme-toggle')[0].props.accessibilityState.checked).toBe(true));
  });

  it('offers System, Light and Dark in Privacy and settings', async () => {
    await renderApp({ theme: 'light' });
    await fireEvent.press(await screen.findByTestId('privacy-link'));
    await fireEvent.press(await screen.findByTestId('appearance-dark'));
    await waitFor(() => expect(screen.getByTestId('appearance-dark').props.accessibilityState.checked).toBe(true));
    expect(screen.getAllByTestId('theme-toggle')[0].props.accessibilityState.checked).toBe(true);
  });
});
