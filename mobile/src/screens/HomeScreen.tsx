import { useFocusEffect } from '@react-navigation/native';
import React, { useCallback, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { AppButton, AppText, Banner, Card, Icon, RiskBadge, Screen, SectionTitle } from '../components/ui';
import type { ScreenProps } from '../navigation/types';
import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';
import type { HistoryItem } from '../types/api';
import { formatDateTime } from '../utils/format';

export default function HomeScreen({ navigation }: ScreenProps<'Home'>) {
  const { colors } = useTheme();
  const { gmailStatus, user, authMessage, busy, connect, grantGmailAccess, api, serviceStatus, serviceError, refreshServiceStatus, historyVersion } = useApp();
  const [recent, setRecent] = useState<HistoryItem[] | null>(null);
  const [recentError, setRecentError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      let active = true;
      api
        .history(0, 3)
        .then((page) => {
          if (active) {
            setRecent(page.items);
            setRecentError(null);
          }
        })
        .catch((e: Error) => {
          if (active) {
            setRecent(null);
            setRecentError(e.message);
          }
        });
      return () => {
        active = false;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [api, historyVersion, gmailStatus]),
  );

  const connected = gmailStatus === 'connected';

  return (
    <Screen testID="home-screen">
      <View style={styles.hero}>
        <View style={[styles.logo, { backgroundColor: colors.accentBg }]}>
          <Icon name="shield-check-outline" size={30} color={colors.accent} />
        </View>
        <View style={{ flex: 1 }}>
          <AppText variant="title">AI Shield</AppText>
          <AppText variant="secondary">Check whether an email is phishing or a scam before you act on it.</AppText>
        </View>
      </View>

      <Card testID="gmail-card">
        <SectionTitle icon="gmail">Gmail</SectionTitle>
        {gmailStatus === 'checking' && <AppText variant="secondary">Checking your Gmail connection…</AppText>}
        {gmailStatus === 'connected' && (
          <AppText variant="secondary" testID="gmail-status">
            Connected as {user?.email}. AI Shield can read the emails you choose (read-only).
          </AppText>
        )}
        {gmailStatus === 'disconnected' && (
          <AppText variant="secondary" testID="gmail-status">
            Not connected. Connect your Gmail to pick an email to analyse. AI Shield only gets read-only access.
          </AppText>
        )}
        {gmailStatus === 'missingScope' && (
          <AppText variant="secondary" testID="gmail-status">
            Signed in as {user?.email}, but Gmail read access wasn't granted.
          </AppText>
        )}
        {gmailStatus === 'unavailable' && (
          <AppText variant="secondary" testID="gmail-status">
            Gmail connection is not available in this build.
          </AppText>
        )}
        {authMessage ? <Banner kind="warning" message={authMessage} testID="auth-message" /> : null}
        {gmailStatus === 'disconnected' && (
          <AppButton label="Connect Gmail" icon="google" onPress={connect} loading={busy} testID="connect-gmail" />
        )}
        {gmailStatus === 'missingScope' && (
          <AppButton label="Allow Gmail read access" icon="lock-open-outline" onPress={grantGmailAccess} loading={busy} testID="grant-gmail" />
        )}
        {connected && (
          <AppButton label="Choose an email to analyse" icon="email-search-outline" onPress={() => navigation.navigate('Inbox')} testID="choose-email" />
        )}
        <AppButton
          label="Paste an email manually"
          icon="text-box-outline"
          variant="secondary"
          onPress={() => navigation.navigate('ManualInput')}
          testID="manual-input"
        />
      </Card>

      {serviceError ? (
        <Card>
          <Banner kind="error" message={serviceError} testID="service-error" />
          <AppButton label="Retry connection" variant="secondary" icon="refresh" onPress={refreshServiceStatus} />
        </Card>
      ) : serviceStatus && !serviceStatus.aiConfigured ? (
        <Banner kind="warning" message="AI analysis is not configured on the server, so results will be based on link and sender checks only." />
      ) : null}

      <Card>
        <View style={styles.rowBetween}>
          <SectionTitle icon="history">Recent analyses</SectionTitle>
          <Pressable onPress={() => navigation.navigate('History')} accessibilityRole="link" hitSlop={8}>
            <AppText style={{ color: colors.accent, fontWeight: '600' }}>See all</AppText>
          </Pressable>
        </View>
        {recentError ? (
          <AppText variant="secondary">History isn't available right now.</AppText>
        ) : recent === null ? (
          <AppText variant="secondary">Loading…</AppText>
        ) : recent.length === 0 ? (
          <AppText variant="secondary" testID="recent-empty">
            No analyses yet. Your results will appear here.
          </AppText>
        ) : (
          recent.map((item) => (
            <Pressable
              key={item.id}
              onPress={() => navigation.navigate('Result', { analysisId: item.id })}
              accessibilityRole="button"
              style={[styles.recentRow, { borderTopColor: colors.border }]}
            >
              <View style={{ flex: 1, gap: 4 }}>
                <AppText numberOfLines={1} style={{ fontWeight: '500' }}>
                  {item.subjectPreview || '(no subject)'}
                </AppText>
                <AppText variant="caption">
                  {item.senderDomain ?? 'unknown sender'} · {formatDateTime(item.createdAt)}
                </AppText>
              </View>
              <RiskBadge level={item.riskLevel} size="sm" />
            </Pressable>
          ))
        )}
      </Card>

      <AppButton label="Privacy and limitations" icon="shield-lock-outline" variant="ghost" onPress={() => navigation.navigate('Privacy')} testID="privacy-link" />
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { flexDirection: 'row', gap: 14, alignItems: 'center', paddingVertical: 4 },
  logo: { width: 56, height: 56, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  recentRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingTop: 10, borderTopWidth: 1 },
});
