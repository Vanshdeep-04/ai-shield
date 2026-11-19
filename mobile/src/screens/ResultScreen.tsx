import React, { useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { FeedbackPanel } from '../components/FeedbackPanel';
import { AppButton, AppText, Banner, Card, Icon, LoadingBlock, RiskBadge, Screen, ScoreBar, SectionTitle, Tag } from '../components/ui';
import type { ScreenProps } from '../navigation/types';
import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';
import type { AnalysisResponse, Evidence } from '../types/api';
import { formatDateTime, humanizeCode, RISK_HEADLINE } from '../utils/format';

export default function ResultScreen({ route, navigation }: ScreenProps<'Result'>) {
  const { api, bumpHistory } = useApp();
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(route.params.analysis ?? null);
  const [error, setError] = useState<string | null>(null);
  const fromHistory = !route.params.analysis && !!route.params.analysisId;

  useEffect(() => {
    if (analysis || !route.params.analysisId) return;
    let active = true;
    api
      .getAnalysis(route.params.analysisId)
      .then((a) => active && setAnalysis(a))
      .catch((e: Error) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [api, analysis, route.params.analysisId]);

  if (error) {
    return (
      <Screen>
        <Banner kind="error" message={error} testID="result-error" />
        <AppButton label="Back" variant="secondary" icon="arrow-left" onPress={() => navigation.goBack()} />
      </Screen>
    );
  }
  if (!analysis) {
    return (
      <Screen>
        <LoadingBlock message="Loading the result…" />
      </Screen>
    );
  }

  const confirmDelete = () => {
    if (!analysis.id) return;
    Alert.alert('Delete this analysis?', 'It will be removed from your history together with your feedback.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.deleteAnalysis(analysis.id!);
            bumpHistory();
            navigation.goBack();
          } catch (e) {
            setError((e as Error).message);
          }
        },
      },
    ]);
  };

  return (
    <Screen testID="result-screen">
      <Summary analysis={analysis} />
      <ActionCard analysis={analysis} />
      <EvidenceCard analysis={analysis} />
      <LinksCard analysis={analysis} />
      <LimitationsCard analysis={analysis} />
      <FeedbackPanel analysisId={analysis.saved ? analysis.id : undefined} initial={analysis.feedback} />
      <View style={styles.buttons}>
        <AppButton label="Home" icon="home-outline" variant="secondary" style={{ flex: 1 }} onPress={() => navigation.popToTop()} testID="go-home" />
        <AppButton label="History" icon="history" variant="secondary" style={{ flex: 1 }} onPress={() => navigation.navigate('History')} testID="go-history" />
      </View>
      {fromHistory && analysis.id ? <AppButton label="Delete from history" icon="trash-can-outline" variant="danger" onPress={confirmDelete} /> : null}
    </Screen>
  );
}

function Summary({ analysis }: { analysis: AnalysisResponse }) {
  const { colors } = useTheme();
  return (
    <Card testID="result-summary">
      <RiskBadge level={analysis.riskLevel} testID="result-risk-badge" />
      <AppText variant="title" style={{ fontSize: 21 }}>
        {RISK_HEADLINE[analysis.riskLevel]}
      </AppText>
      {analysis.riskScore !== undefined && analysis.riskScore !== null ? (
        <View style={{ gap: 6 }}>
          <View style={styles.scoreRow}>
            <AppText style={{ fontSize: 28, fontWeight: '700' }} testID="result-score">
              {analysis.riskScore}
            </AppText>
            <AppText variant="secondary">/ 100 risk score</AppText>
          </View>
          <ScoreBar score={analysis.riskScore} level={analysis.riskLevel} />
        </View>
      ) : (
        <AppText variant="secondary" testID="result-no-score">
          No score: the assessment could not be completed reliably.
        </AppText>
      )}
      <AppText testID="result-explanation">{analysis.explanation}</AppText>
      {analysis.ai.status === 'UNAVAILABLE' && analysis.ai.unavailableReason ? (
        <Banner kind="warning" message={`AI analysis unavailable: ${analysis.ai.unavailableReason}`} testID="ai-unavailable" />
      ) : null}
      <View style={styles.metaRow}>
        <Icon name="email-outline" size={16} color={colors.textMuted} />
        <AppText variant="caption" style={{ flex: 1 }} numberOfLines={2}>
          {analysis.message.subjectPreview ?? '(no subject)'} · {analysis.message.senderDomain ?? 'unknown sender'} · {formatDateTime(analysis.createdAt)}
        </AppText>
      </View>
      {!analysis.saved ? <Banner kind="warning" message="This result was not saved to your history." /> : null}
    </Card>
  );
}

function ActionCard({ analysis }: { analysis: AnalysisResponse }) {
  const { colors } = useTheme();
  return (
    <Card style={{ backgroundColor: colors.accentBg, borderColor: colors.accentBg }} testID="result-action">
      <View style={styles.metaRow}>
        <Icon name="lightbulb-on-outline" size={20} color={colors.accentText} />
        <AppText variant="heading" style={{ color: colors.accentText }}>
          What should I do?
        </AppText>
      </View>
      <AppText style={{ color: colors.accentText }}>{analysis.recommendedAction}</AppText>
    </Card>
  );
}

const SOURCE_LABEL: Record<Evidence['source'], string> = {
  AI_CONTENT_ANALYSIS: 'Email content',
  URL_ANALYSIS: 'Links',
  SENDER_METADATA: 'Sender',
};

function EvidenceCard({ analysis }: { analysis: AnalysisResponse }) {
  const { colors } = useTheme();
  const ai = analysis.evidence.filter((e) => e.type === 'AI_INTERPRETATION');
  const observed = analysis.evidence.filter((e) => e.type === 'OBSERVED');
  // One line per indicator code for observed URL evidence (the same check may hit several links).
  const seen = new Set<string>();
  const observedUnique = observed.filter((e) => {
    const key = `${e.source}:${e.code}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return (
    <Card testID="result-evidence">
      <SectionTitle icon="magnify-scan">Warning signs</SectionTitle>
      {ai.length === 0 && observedUnique.length === 0 ? <AppText variant="secondary">No specific warning signs were detected.</AppText> : null}

      {ai.length > 0 ? (
        <View style={{ gap: 8 }}>
          <View style={styles.metaRow}>
            <Tag label="AI interpretation" tone="ai" />
            <AppText variant="caption">may be wrong</AppText>
          </View>
          {ai.map((e, i) => (
            <View key={`ai-${i}`} style={[styles.evidence, { borderColor: colors.border }]}>
              <AppText style={{ fontWeight: '600' }}>{e.description}</AppText>
              <AppText variant="caption">{humanizeCode(e.code)}</AppText>
              {e.detail ? <AppText variant="secondary" style={{ fontStyle: 'italic' }}>“{e.detail}”</AppText> : null}
            </View>
          ))}
        </View>
      ) : null}

      {observedUnique.length > 0 ? (
        <View style={{ gap: 8 }}>
          <Tag label="Observed by AI Shield's checks" tone="accent" />
          {observedUnique.map((e, i) => (
            <View key={`obs-${i}`} style={[styles.evidence, { borderColor: colors.border }]}>
              <AppText style={{ fontWeight: '600' }}>{e.description}</AppText>
              <AppText variant="caption">
                {SOURCE_LABEL[e.source]}
                {e.detail ? ` · ${e.detail}` : ''}
              </AppText>
            </View>
          ))}
        </View>
      ) : null}

      {analysis.ai.benignFactors && analysis.ai.benignFactors.length > 0 ? (
        <View style={{ gap: 4 }}>
          <AppText variant="caption">Possible legitimate explanations (AI):</AppText>
          {analysis.ai.benignFactors.map((b, i) => (
            <AppText key={i} variant="secondary">
              • {b}
            </AppText>
          ))}
        </View>
      ) : null}
    </Card>
  );
}

function LinksCard({ analysis }: { analysis: AnalysisResponse }) {
  const { colors } = useTheme();
  const { urlAnalysis } = analysis;
  return (
    <Card testID="result-links">
      <SectionTitle icon="link-variant">Links checked ({urlAnalysis.linksChecked})</SectionTitle>
      {urlAnalysis.findings.length === 0 ? <AppText variant="secondary">No web links were found in this email.</AppText> : null}
      {urlAnalysis.findings.slice(0, 10).map((f, i) => (
        <View key={i} style={[styles.evidence, { borderColor: colors.border }]}>
          <View style={styles.metaRow}>
            <Icon name={f.score >= 30 ? 'link-variant-off' : 'link-variant'} size={16} color={f.score >= 30 ? colors.danger : colors.textMuted} />
            <AppText style={{ fontWeight: '600', flex: 1 }} numberOfLines={1}>
              {f.host ?? f.url}
            </AppText>
            <AppText variant="caption">{f.indicators.length === 0 ? 'No issues' : `${f.score}/100`}</AppText>
          </View>
          {f.displayText ? (
            <AppText variant="caption" numberOfLines={1}>
              Shown as: {f.displayText}
            </AppText>
          ) : null}
          {f.indicators.map((ind) => (
            <AppText key={ind.code} variant="secondary">
              • {ind.description}
            </AppText>
          ))}
        </View>
      ))}
      {urlAnalysis.findings.length > 10 ? <AppText variant="caption">and {urlAnalysis.findings.length - 10} more links</AppText> : null}
      <AppText variant="caption">Links are never opened by AI Shield. Don't open them to "check".</AppText>
    </Card>
  );
}

function LimitationsCard({ analysis }: { analysis: AnalysisResponse }) {
  return (
    <Card testID="result-limitations">
      <SectionTitle icon="information-outline">Limitations of this result</SectionTitle>
      {analysis.limitations.map((l, i) => (
        <AppText key={i} variant="secondary">
          • {l}
        </AppText>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  scoreRow: { flexDirection: 'row', alignItems: 'baseline', gap: 6 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  evidence: { borderLeftWidth: 3, paddingLeft: 10, gap: 2 },
  buttons: { flexDirection: 'row', gap: 10 },
});
