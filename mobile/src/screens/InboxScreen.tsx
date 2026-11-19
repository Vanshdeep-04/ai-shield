import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton, AppText, Banner, EmptyState, Icon, LoadingBlock } from '../components/ui';
import type { ScreenProps } from '../navigation/types';
import { EmailSummary, GmailError, parseFrom } from '../services/gmail';
import { useApp } from '../state/AppContext';
import { useTheme } from '../theme/ThemeContext';
import { formatEmailDate } from '../utils/format';

/** Lists recent inbox emails by sender, subject and date only (no message bodies are fetched here). */
export default function InboxScreen({ navigation }: ScreenProps<'Inbox'>) {
  const { colors } = useTheme();
  const { gmail, gmailStatus, connect } = useApp();
  const [messages, setMessages] = useState<EmailSummary[]>([]);
  const [nextPage, setNextPage] = useState<string | undefined>();
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<GmailError | Error | null>(null);
  const [query, setQuery] = useState('');
  const [activeQuery, setActiveQuery] = useState('');

  const load = useCallback(
    async (mode: 'initial' | 'refresh' | 'more', q: string, pageToken?: string) => {
      if (mode === 'initial') setLoading(true);
      if (mode === 'refresh') setRefreshing(true);
      if (mode === 'more') setLoadingMore(true);
      try {
        const res = await gmail.listMessages({ query: q || undefined, pageToken });
        setMessages((prev) => (mode === 'more' ? [...prev, ...res.messages] : res.messages));
        setNextPage(res.nextPageToken);
        setError(null);
      } catch (e) {
        setError(e as Error);
      } finally {
        setLoading(false);
        setRefreshing(false);
        setLoadingMore(false);
      }
    },
    [gmail],
  );

  useEffect(() => {
    if (gmailStatus === 'connected') load('initial', activeQuery);
  }, [gmailStatus, activeQuery, load]);

  if (gmailStatus !== 'connected') {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['bottom']}>
        <EmptyState
          icon="gmail"
          title="Gmail isn't connected"
          message="Connect Gmail with read-only access to choose an email."
          action={gmailStatus === 'disconnected' ? <AppButton label="Connect Gmail" icon="google" onPress={connect} /> : undefined}
        />
      </SafeAreaView>
    );
  }

  const authProblem = error instanceof GmailError && (error.kind === 'unauthorized' || error.kind === 'forbidden');

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['bottom']} testID="inbox-screen">
      <View style={[styles.searchRow, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <Icon name="magnify" size={20} color={colors.textMuted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={() => setActiveQuery(query.trim())}
          placeholder="Search Gmail (e.g. from:bank)"
          placeholderTextColor={colors.textMuted}
          returnKeyType="search"
          accessibilityLabel="Search Gmail"
          style={{ flex: 1, color: colors.text, fontSize: 15, paddingVertical: 10 }}
        />
      </View>

      {loading ? (
        <LoadingBlock message="Loading your inbox…" />
      ) : error && messages.length === 0 ? (
        <View style={{ padding: 16, gap: 12 }}>
          <Banner kind="error" message={error.message} testID="inbox-error" />
          <AppButton label={authProblem ? 'Connect Gmail again' : 'Try again'} icon="refresh" onPress={() => (authProblem ? connect() : load('initial', activeQuery))} />
        </View>
      ) : (
        <FlatList
          data={messages}
          keyExtractor={(m) => m.id}
          contentContainerStyle={{ padding: 16, gap: 8 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => load('refresh', activeQuery)} />}
          ListEmptyComponent={<EmptyState icon="email-outline" title="No emails found" message={activeQuery ? 'Try a different search.' : 'Your inbox is empty.'} />}
          ListHeaderComponent={<AppText variant="caption">Only sender, subject and date are shown. Tap an email to review it before analysis.</AppText>}
          renderItem={({ item }) => {
            const sender = parseFrom(item.from);
            return (
              <Pressable
                onPress={() => navigation.navigate('EmailPreview', { messageId: item.id })}
                accessibilityRole="button"
                accessibilityLabel={`Email from ${sender.name ?? sender.address}, subject ${item.subject}`}
                testID={`email-${item.id}`}
                style={({ pressed }) => [styles.row, { backgroundColor: colors.surface, borderColor: colors.border, opacity: pressed ? 0.8 : 1 }]}
              >
                <View style={{ flex: 1, gap: 2 }}>
                  <View style={styles.rowTop}>
                    <AppText numberOfLines={1} style={{ flex: 1, fontWeight: item.unread ? '700' : '500' }}>
                      {sender.name ?? sender.address ?? item.from}
                    </AppText>
                    <AppText variant="caption">{formatEmailDate(item.date)}</AppText>
                  </View>
                  <AppText variant="secondary" numberOfLines={1}>
                    {item.subject}
                  </AppText>
                </View>
                <Icon name="chevron-right" size={20} color={colors.textMuted} />
              </Pressable>
            );
          }}
          ListFooterComponent={
            nextPage ? (
              loadingMore ? (
                <ActivityIndicator color={colors.accent} style={{ padding: 12 }} />
              ) : (
                <AppButton label="Load more" variant="secondary" onPress={() => load('more', activeQuery, nextPage)} />
              )
            ) : undefined
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  searchRow: { flexDirection: 'row', alignItems: 'center', gap: 8, margin: 16, marginBottom: 0, paddingHorizontal: 12, borderRadius: 10, borderWidth: 1 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 14, borderRadius: 12, borderWidth: 1 },
  rowTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
});
