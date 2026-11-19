import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton, AppText, Banner, EmptyState, Icon, LoadingBlock, RiskBadge } from '../components/ui';
import type { ScreenProps } from '../navigation/types';
import { ApiError } from '../services/api';
import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';
import type { HistoryItem } from '../types/api';
import { formatDateTime, humanizeCode } from '../utils/format';

const PAGE_SIZE = 20;

export default function HistoryScreen({ navigation }: ScreenProps<'History'>) {
  const { colors } = useTheme();
  const { api, historyVersion, bumpHistory } = useApp();
  const [items, setItems] = useState<HistoryItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const load = useCallback(
    async (mode: 'initial' | 'refresh' | 'more', pageToLoad: number) => {
      if (mode === 'refresh') setRefreshing(true);
      if (mode === 'more') setLoadingMore(true);
      try {
        const res = await api.history(pageToLoad, PAGE_SIZE);
        setItems((prev) => (mode === 'more' ? [...prev, ...res.items] : res.items));
        setPage(res.page);
        setTotalPages(res.totalPages);
        setError(null);
      } catch (e) {
        setError(e as Error);
      } finally {
        setLoading(false);
        setRefreshing(false);
        setLoadingMore(false);
      }
    },
    [api],
  );

  useEffect(() => {
    load('initial', 0);
  }, [load, historyVersion]);

  const deleteAll = () => {
    Alert.alert('Delete all history?', 'All saved analyses and your feedback will be permanently removed.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete all',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.deleteAllHistory();
            bumpHistory();
          } catch (e) {
            setError(e as Error);
          }
        },
      },
    ]);
  };

  if (loading) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['bottom']}>
        <LoadingBlock message="Loading your history…" />
      </SafeAreaView>
    );
  }

  if (error && items.length === 0) {
    const unauthorized = error instanceof ApiError && error.kind === 'unauthorized';
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background, padding: 16, gap: 12 }} edges={['bottom']}>
        <Banner kind="error" message={unauthorized ? 'Connect Gmail (Google sign-in) to see your history.' : error.message} testID="history-error" />
        <AppButton label="Try again" icon="refresh" variant="secondary" onPress={() => load('initial', 0)} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['bottom']} testID="history-screen">
      <FlatList
        data={items}
        keyExtractor={(i) => i.id}
        contentContainerStyle={{ padding: 16, gap: 8, flexGrow: 1 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => load('refresh', 0)} />}
        ListHeaderComponent={error ? <Banner kind="error" message={error.message} /> : undefined}
        ListEmptyComponent={
          <EmptyState
            icon="history"
            title="No analyses yet"
            message="When you analyse an email, a short summary is saved here so you can review it later."
            action={<AppButton label="Analyse an email" icon="shield-search" onPress={() => navigation.navigate('Home')} />}
          />
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => navigation.navigate('Result', { analysisId: item.id })}
            accessibilityRole="button"
            accessibilityLabel={`${item.subjectPreview ?? 'No subject'}, ${item.riskLevel} risk`}
            testID={`history-${item.id}`}
            style={({ pressed }) => [styles.row, { backgroundColor: colors.surface, borderColor: colors.border, opacity: pressed ? 0.8 : 1 }]}
          >
            <View style={{ flex: 1, gap: 4 }}>
              <AppText numberOfLines={1} style={{ fontWeight: '600' }}>
                {item.subjectPreview || '(no subject)'}
              </AppText>
              <AppText variant="caption">
                {item.senderDomain ?? 'unknown sender'} · {formatDateTime(item.createdAt)}
              </AppText>
              <View style={styles.tags}>
                <RiskBadge level={item.riskLevel} size="sm" />
                {item.riskScore !== undefined && item.riskScore !== null ? <AppText variant="caption">{item.riskScore}/100</AppText> : null}
                {item.feedbackVerdict ? <AppText variant="caption">· Feedback: {humanizeCode(item.feedbackVerdict)}</AppText> : null}
              </View>
            </View>
            <Icon name="chevron-right" size={20} color={colors.textMuted} />
          </Pressable>
        )}
        ListFooterComponent={
          <View style={{ gap: 8, paddingTop: 8 }}>
            {page + 1 < totalPages ? (
              loadingMore ? (
                <ActivityIndicator color={colors.accent} />
              ) : (
                <AppButton label="Load more" variant="secondary" onPress={() => load('more', page + 1)} />
              )
            ) : null}
            {items.length > 0 ? <AppButton label="Delete all history" icon="trash-can-outline" variant="danger" onPress={deleteAll} testID="delete-all" /> : null}
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 14, borderRadius: 12, borderWidth: 1 },
  tags: { flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' },
});
