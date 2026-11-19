import React, { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppButton, AppText, Banner, Card, Icon, LoadingBlock, Screen } from '../components/ui';
import { useAnalysis } from '../hooks/useAnalysis';
import type { ScreenProps } from '../navigation/types';
import { EmailDetail, parseFrom } from '../services/gmail';
import { buildGmailRequest } from '../services/requestBuilder';
import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';

const PREVIEW_CHARS = 700;

/**
 * Shows the selected email as plain text (no HTML rendering, no clickable links, no remote images)
 * and runs the analysis.
 */
export default function EmailPreviewScreen({ route, navigation }: ScreenProps<'EmailPreview'>) {
  const { colors } = useTheme();
  const { gmail } = useApp();
  const [email, setEmail] = useState<EmailDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { stage, error, run, cancel, progressMessage } = useAnalysis();

  useEffect(() => {
    let active = true;
    gmail
      .getMessage(route.params.messageId)
      .then((m) => active && setEmail(m))
      .catch((e: Error) => active && setLoadError(e.message));
    return () => {
      active = false;
    };
  }, [gmail, route.params.messageId]);

  const analyse = async () => {
    if (!email) return;
    const result = await run(buildGmailRequest(email));
    if (result) navigation.replace('Result', { analysis: result });
  };

  if (loadError) {
    return (
      <Screen>
        <Banner kind="error" message={loadError} testID="email-load-error" />
        <AppButton label="Back to inbox" variant="secondary" icon="arrow-left" onPress={() => navigation.goBack()} />
      </Screen>
    );
  }
  if (!email) {
    return (
      <Screen>
        <LoadingBlock message="Opening the email…" />
      </Screen>
    );
  }

  const sender = parseFrom(email.from);
  const preview = email.previewText.length > PREVIEW_CHARS ? `${email.previewText.slice(0, PREVIEW_CHARS)}…` : email.previewText;
  const noContent = !email.bodyText && !email.bodyHtml;

  return (
    <Screen testID="email-preview-screen">
      <Card>
        <AppText variant="heading">{email.subject}</AppText>
        <Row icon="account-outline" label="From" value={sender.name ? `${sender.name} <${sender.address ?? ''}>` : email.from} />
        {email.replyTo ? <Row icon="reply-outline" label="Reply-To" value={email.replyTo} /> : null}
        {email.date ? <Row icon="clock-outline" label="Date" value={email.date} /> : null}
        {email.attachmentCount > 0 ? (
          <Row icon="paperclip" label="Attachments" value={`${email.attachmentCount} (not downloaded or analysed)`} />
        ) : null}
      </Card>

      <Card>
        <AppText variant="caption">Message preview (plain text; links are not clickable here)</AppText>
        <AppText variant="secondary" testID="email-preview-text">
          {preview || '(This email has no readable text.)'}
        </AppText>
      </Card>

      {stage === 'running' ? (
        <Card testID="analysis-progress">
          <LoadingBlock message={progressMessage} />
          <AppButton label="Cancel" variant="secondary" icon="close" onPress={cancel} testID="cancel-analysis" />
        </Card>
      ) : (
        <>
          {error ? <Banner kind="error" message={error} testID="analysis-error" /> : null}
          {noContent ? <Banner kind="warning" message="This email has no text that AI Shield can analyse." /> : null}
          <AppButton
            label={error ? 'Try again' : 'Analyse this email'}
            icon="shield-search"
            onPress={analyse}
            disabled={noContent}
            testID="analyse-button"
            accessibilityHint="Sends this email to the AI Shield server for analysis"
          />
          <View style={styles.note}>
            <Icon name="information-outline" size={16} color={colors.textMuted} />
            <AppText variant="caption" style={{ flex: 1 }}>
              The sender, subject, text and links are sent to the AI Shield server and to an external AI service for analysis. Attachments are not sent.
            </AppText>
          </View>
        </>
      )}
    </Screen>
  );
}

function Row({ icon, label, value }: { icon: React.ComponentProps<typeof Icon>['name']; label: string; value: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.row}>
      <Icon name={icon} size={18} color={colors.textMuted} />
      <AppText variant="caption" style={{ width: 74 }}>
        {label}
      </AppText>
      <AppText variant="secondary" style={{ flex: 1 }} numberOfLines={2}>
        {value}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  note: { flexDirection: 'row', gap: 6, paddingHorizontal: 4 },
});
