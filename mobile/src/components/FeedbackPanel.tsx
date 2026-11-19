import React, { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';
import type { Feedback, FeedbackVerdict } from '../types/api';
import { AppButton, AppText, Banner, Card, Icon, IconName, SectionTitle } from './ui';

const OPTIONS: { verdict: FeedbackVerdict; label: string; icon: IconName }[] = [
  { verdict: 'ACCURATE', label: 'Looks right', icon: 'thumb-up-outline' },
  { verdict: 'FALSE_POSITIVE', label: 'False alarm', icon: 'shield-off-outline' },
  { verdict: 'FALSE_NEGATIVE', label: 'Missed a scam', icon: 'alert-outline' },
  { verdict: 'UNSURE', label: 'Not sure', icon: 'help-circle-outline' },
];

export function FeedbackPanel({ analysisId, initial }: { analysisId?: string; initial?: Feedback }) {
  const { colors } = useTheme();
  const { api, bumpHistory } = useApp();
  const [verdict, setVerdict] = useState<FeedbackVerdict | null>(initial?.verdict ?? null);
  const [comment, setComment] = useState(initial?.comment ?? '');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<boolean>(!!initial);
  const [error, setError] = useState<string | null>(null);

  if (!analysisId) {
    return (
      <Card>
        <SectionTitle icon="comment-quote-outline">Feedback</SectionTitle>
        <AppText variant="secondary">This result wasn't saved to history, so feedback can't be recorded.</AppText>
      </Card>
    );
  }

  const submit = async () => {
    if (!verdict) {
      setError('Choose an option first.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api.submitFeedback(analysisId, verdict, comment.trim() || undefined);
      setSaved(true);
      bumpHistory();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card testID="feedback-panel">
      <SectionTitle icon="comment-quote-outline">Was this assessment right?</SectionTitle>
      <View style={styles.options} accessibilityRole="radiogroup">
        {OPTIONS.map((o) => {
          const selected = verdict === o.verdict;
          return (
            <Pressable
              key={o.verdict}
              onPress={() => {
                setVerdict(o.verdict);
                setSaved(false);
                setError(null);
              }}
              accessibilityRole="radio"
              accessibilityState={{ checked: selected }}
              accessibilityLabel={o.label}
              testID={`feedback-${o.verdict}`}
              style={[
                styles.option,
                { borderColor: selected ? colors.accent : colors.border, backgroundColor: selected ? colors.accentBg : colors.surface },
              ]}
            >
              <Icon name={o.icon} size={18} color={selected ? colors.accentText : colors.textSecondary} />
              <AppText style={{ color: selected ? colors.accentText : colors.text, fontWeight: selected ? '600' : '400', fontSize: 14 }}>{o.label}</AppText>
            </Pressable>
          );
        })}
      </View>
      <TextInput
        value={comment}
        onChangeText={(t) => {
          setComment(t);
          setSaved(false);
        }}
        placeholder="Optional comment (max 500 characters)"
        placeholderTextColor={colors.textMuted}
        maxLength={500}
        multiline
        accessibilityLabel="Feedback comment"
        style={[styles.input, { borderColor: colors.border, color: colors.text, backgroundColor: colors.surfaceAlt }]}
      />
      {error ? <Banner kind="error" message={error} /> : null}
      {saved ? <Banner kind="info" message="Thanks, your feedback was saved." testID="feedback-saved" /> : null}
      <AppButton label="Send feedback" variant="secondary" icon="send-outline" onPress={submit} loading={saving} testID="feedback-submit" />
    </Card>
  );
}

const styles = StyleSheet.create({
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  option: { flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, minHeight: 44 },
  input: { borderWidth: 1, borderRadius: 10, padding: 10, minHeight: 60, fontSize: 14, textAlignVertical: 'top' },
});
