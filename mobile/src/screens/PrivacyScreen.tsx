import React from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';

import { AppButton, AppText, Card, Icon, IconName, Screen, SectionTitle } from '../components/ui';
import type { ScreenProps } from '../navigation/types';
import { useApp } from '../state/AppContext';
import { ThemePreference, useTheme } from '../theme/ThemeContext';

const APPEARANCE: { value: ThemePreference; label: string; icon: IconName }[] = [
  { value: 'system', label: 'System', icon: 'cellphone' },
  { value: 'light', label: 'Light', icon: 'white-balance-sunny' },
  { value: 'dark', label: 'Dark', icon: 'weather-night' },
];

export default function PrivacyScreen({ navigation }: ScreenProps<'Privacy'>) {
  const { colors, preference, setPreference } = useTheme();
  const { gmailStatus, disconnect, user, busy } = useApp();

  const confirmDisconnect = () =>
    Alert.alert('Disconnect Gmail?', 'AI Shield will lose access to your Gmail and you will be signed out. Your saved history is not deleted.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Disconnect', style: 'destructive', onPress: () => disconnect() },
    ]);

  return (
    <Screen testID="privacy-screen">
      <Card>
        <SectionTitle icon="palette-outline">Appearance</SectionTitle>
        <View style={styles.segment} accessibilityRole="radiogroup">
          {APPEARANCE.map((o) => {
            const selected = preference === o.value;
            return (
              <Pressable
                key={o.value}
                onPress={() => setPreference(o.value)}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected }}
                accessibilityLabel={`${o.label} theme`}
                testID={`appearance-${o.value}`}
                style={[styles.segmentItem, { borderColor: selected ? colors.accent : colors.border, backgroundColor: selected ? colors.accentBg : colors.surface }]}
              >
                <Icon name={o.icon} size={18} color={selected ? colors.accentText : colors.textSecondary} />
                <AppText style={{ fontSize: 14, color: selected ? colors.accentText : colors.text, fontWeight: selected ? '600' : '400' }}>{o.label}</AppText>
              </Pressable>
            );
          })}
        </View>
      </Card>

      <Section icon="robot-outline" title="What is sent for AI analysis">
        When you analyse an email, its sender, Reply-To address, subject, text (up to 15,000 characters) and links are sent from the AI Shield
        server to an external AI service for analysis. Attachments and images are never sent. On the free tier of this service, the provider may use
        submitted content to improve its products and human reviewers may read it, so avoid analysing confidential or personal emails. A test Gmail
        account is recommended for demos.
      </Section>

      <Section icon="gmail" title="Why Gmail access is requested">
        AI Shield asks only for read-only Gmail access (gmail.readonly). It cannot send, delete or change your email. It lists sender, subject and date
        so you can pick an email, and reads the full content only of the email you choose. Your Gmail token stays on this phone and is never sent to
        the AI Shield server. AI Shield never asks for your Google password.
      </Section>

      <Section icon="database-outline" title="What is stored">
        For each analysis, the server stores the risk level and score, the explanation and warning signs (including short quotes and link addresses),
        the sender's domain, the first 120 characters of the subject, a timestamp, and your feedback. Full email bodies, attachments and tokens are
        never stored. History is linked to your Google account ID and only you can see it.
      </Section>

      <Section icon="account-cog-outline" title="Your controls">
        Delete individual analyses or your whole history from the History screen. Disconnect Gmail below at any time, or remove access at
        myaccount.google.com/permissions.
      </Section>

      <Section icon="alert-outline" title="Limitations">
        AI Shield can make mistakes: it may flag a legitimate email (false positive) or miss a phishing email (false negative). The score is a heuristic
        indicator, not a probability. Links are never opened, so final destinations of shortened links are unknown. Sender authentication (SPF/DKIM) is
        not verified, and there is no domain-reputation lookup. Always verify important requests through official channels.
      </Section>

      <Card>
        <SectionTitle icon="link-variant-off">Gmail connection</SectionTitle>
        {gmailStatus === 'connected' || gmailStatus === 'missingScope' ? (
          <>
            <AppText variant="secondary">Connected as {user?.email}.</AppText>
            <AppButton label="Disconnect Gmail" icon="logout" variant="danger" onPress={confirmDisconnect} loading={busy} testID="disconnect-gmail" />
          </>
        ) : (
          <AppText variant="secondary">Gmail is not connected.</AppText>
        )}
      </Card>
      <AppButton label="Back" variant="ghost" icon="arrow-left" onPress={() => navigation.goBack()} />
    </Screen>
  );
}

function Section({ icon, title, children }: { icon: IconName; title: string; children: React.ReactNode }) {
  return (
    <Card>
      <SectionTitle icon={icon}>{title}</SectionTitle>
      <AppText variant="secondary">{children}</AppText>
    </Card>
  );
}

const styles = StyleSheet.create({
  segment: { flexDirection: 'row', gap: 8 },
  segmentItem: { flex: 1, flexDirection: 'row', gap: 6, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderRadius: 10, minHeight: 44 },
});
