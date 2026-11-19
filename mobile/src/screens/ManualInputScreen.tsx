import React, { useState } from 'react';
import { TextInput, TextInputProps } from 'react-native';

import { AppButton, AppText, Banner, Card, LoadingBlock, Screen } from '../components/ui';
import { useAnalysis } from '../hooks/useAnalysis';
import type { ScreenProps } from '../navigation/types';
import { buildManualRequest } from '../services/requestBuilder';
import { useTheme } from '../theme/ThemeContext';

/** Secondary input path for testing the analysis engine without Gmail. */
export default function ManualInputScreen({ navigation }: ScreenProps<'ManualInput'>) {
  const [from, setFrom] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [validation, setValidation] = useState<string | null>(null);
  const { stage, error, run, cancel, progressMessage } = useAnalysis();

  const analyse = async () => {
    if (body.trim().length === 0) {
      setValidation('Paste the email text first.');
      return;
    }
    setValidation(null);
    const result = await run(buildManualRequest({ from, subject, body }));
    if (result) navigation.replace('Result', { analysis: result });
  };

  return (
    <Screen testID="manual-screen">
      <AppText variant="secondary">
        Paste an email you received to test the analysis. Don't paste confidential or personal information.
      </AppText>
      <Card>
        <Field label="Sender (optional)" value={from} onChangeText={setFrom} placeholder="Support <help@example.com>" autoCapitalize="none" testID="manual-from" />
        <Field label="Subject (optional)" value={subject} onChangeText={setSubject} placeholder="Your account has been suspended" testID="manual-subject" />
        <Field
          label="Email text"
          value={body}
          onChangeText={(t) => {
            setBody(t);
            if (validation) setValidation(null);
          }}
          placeholder="Paste the email body here"
          multiline
          testID="manual-body"
        />
        {validation ? <Banner kind="error" message={validation} testID="manual-validation" /> : null}
      </Card>
      {stage === 'running' ? (
        <Card testID="analysis-progress">
          <LoadingBlock message={progressMessage} />
          <AppButton label="Cancel" variant="secondary" icon="close" onPress={cancel} testID="cancel-analysis" />
        </Card>
      ) : (
        <>
          {error ? <Banner kind="error" message={error} testID="analysis-error" /> : null}
          <AppButton label="Analyse text" icon="shield-search" onPress={analyse} testID="manual-analyse" />
        </>
      )}
    </Screen>
  );
}

function Field({ label, multiline, ...props }: TextInputProps & { label: string }) {
  const { colors } = useTheme();
  return (
    <>
      <AppText variant="caption">{label}</AppText>
      <TextInput
        {...props}
        multiline={multiline}
        accessibilityLabel={label}
        placeholderTextColor={colors.textMuted}
        maxLength={multiline ? 100_000 : 1000}
        style={{
          borderWidth: 1,
          borderColor: colors.border,
          backgroundColor: colors.surfaceAlt,
          color: colors.text,
          borderRadius: 10,
          paddingHorizontal: 12,
          paddingVertical: 10,
          fontSize: 15,
          minHeight: multiline ? 180 : 44,
          textAlignVertical: multiline ? 'top' : 'center',
        }}
      />
    </>
  );
}
