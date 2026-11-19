import { MaterialCommunityIcons } from '@expo/vector-icons';
import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Text,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useTheme } from '../theme/ThemeContext';
import type { RiskLevel } from '../types/api';
import { RISK_LABEL } from '../utils/format';

export type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

export function Icon({ name, size = 20, color }: { name: IconName; size?: number; color?: string }) {
  const { colors } = useTheme();
  return <MaterialCommunityIcons name={name} size={size} color={color ?? colors.text} accessibilityElementsHidden importantForAccessibility="no" />;
}

export function Screen({
  children,
  scroll = true,
  testID,
}: {
  children: React.ReactNode;
  scroll?: boolean;
  testID?: string;
}) {
  const { colors } = useTheme();
  return (
    <SafeAreaView edges={['bottom', 'left', 'right']} style={{ flex: 1, backgroundColor: colors.background }} testID={testID}>
      {scroll ? (
        <ScrollView contentContainerStyle={styles.screenContent} keyboardShouldPersistTaps="handled">
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.screenContent, { flex: 1 }]}>{children}</View>
      )}
    </SafeAreaView>
  );
}

export function Card({ children, style, testID }: { children: React.ReactNode; style?: StyleProp<ViewStyle>; testID?: string }) {
  const { colors } = useTheme();
  return (
    <View testID={testID} style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }, style]}>
      {children}
    </View>
  );
}

export function AppText({
  children,
  variant = 'body',
  style,
  numberOfLines,
  testID,
}: {
  children: React.ReactNode;
  variant?: 'title' | 'heading' | 'body' | 'secondary' | 'caption';
  style?: StyleProp<TextStyle>;
  numberOfLines?: number;
  testID?: string;
}) {
  const { colors } = useTheme();
  const v: Record<string, TextStyle> = {
    title: { fontSize: 24, fontWeight: '600', color: colors.text },
    heading: { fontSize: 17, fontWeight: '600', color: colors.text },
    body: { fontSize: 15, lineHeight: 22, color: colors.text },
    secondary: { fontSize: 14, lineHeight: 20, color: colors.textSecondary },
    caption: { fontSize: 12, lineHeight: 17, color: colors.textMuted },
  };
  return (
    <Text style={[v[variant], style]} numberOfLines={numberOfLines} testID={testID} accessibilityRole={variant === 'title' || variant === 'heading' ? 'header' : undefined}>
      {children}
    </Text>
  );
}

export function AppButton({
  label,
  onPress,
  variant = 'primary',
  icon,
  loading,
  disabled,
  testID,
  accessibilityHint,
  style,
}: {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  icon?: IconName;
  loading?: boolean;
  disabled?: boolean;
  testID?: string;
  accessibilityHint?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useTheme();
  const palette = {
    primary: { bg: colors.accent, fg: colors.onAccent, border: colors.accent },
    secondary: { bg: colors.surface, fg: colors.accent, border: colors.border },
    ghost: { bg: 'transparent', fg: colors.accent, border: 'transparent' },
    danger: { bg: colors.surface, fg: colors.danger, border: colors.border },
  }[variant];
  const inactive = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      testID={testID}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: palette.bg, borderColor: palette.border, opacity: inactive ? 0.55 : pressed ? 0.85 : 1 },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={palette.fg} size="small" /> : icon ? <Icon name={icon} size={18} color={palette.fg} /> : null}
      <Text style={{ color: palette.fg, fontSize: 15, fontWeight: '600' }}>{label}</Text>
    </Pressable>
  );
}

const RISK_ICON: Record<RiskLevel, IconName> = {
  HIGH: 'alert-octagon-outline',
  MEDIUM: 'alert-outline',
  LOW: 'check-circle-outline',
  UNKNOWN: 'help-circle-outline',
};

/** Risk is always shown as icon + text, never by colour alone. */
export function RiskBadge({ level, size = 'md', testID }: { level: RiskLevel; size?: 'sm' | 'md'; testID?: string }) {
  const { colors } = useTheme();
  const tone = colors.risk[level];
  const small = size === 'sm';
  return (
    <View
      testID={testID}
      accessible
      accessibilityLabel={`Risk level: ${RISK_LABEL[level]}`}
      style={[styles.badge, { backgroundColor: tone.bg, paddingVertical: small ? 2 : 4, paddingHorizontal: small ? 6 : 10 }]}
    >
      <Icon name={RISK_ICON[level]} size={small ? 14 : 18} color={tone.text} />
      <Text style={{ color: tone.text, fontWeight: '600', fontSize: small ? 12 : 14 }}>{RISK_LABEL[level]}</Text>
    </View>
  );
}

export function ScoreBar({ score, level }: { score: number; level: RiskLevel }) {
  const { colors } = useTheme();
  const clamped = Math.max(0, Math.min(100, score));
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`Risk score ${clamped} out of 100`}
      accessibilityValue={{ min: 0, max: 100, now: clamped }}
      style={[styles.track, { backgroundColor: colors.surfaceAlt }]}
    >
      <View style={{ width: `${clamped}%`, height: '100%', borderRadius: 4, backgroundColor: colors.risk[level].fg }} />
    </View>
  );
}

export function Tag({ label, tone = 'accent' }: { label: string; tone?: 'accent' | 'neutral' | 'ai' }) {
  const { colors } = useTheme();
  const t =
    tone === 'accent'
      ? { bg: colors.accentBg, fg: colors.accentText }
      : tone === 'ai'
        ? { bg: colors.risk.MEDIUM.bg, fg: colors.risk.MEDIUM.text }
        : { bg: colors.surfaceAlt, fg: colors.textSecondary };
  return (
    <View style={[styles.tag, { backgroundColor: t.bg }]}>
      <Text style={{ color: t.fg, fontSize: 12, fontWeight: '500' }}>{label}</Text>
    </View>
  );
}

export function Banner({ kind = 'info', message, testID }: { kind?: 'info' | 'error' | 'warning'; message: string; testID?: string }) {
  const { colors } = useTheme();
  const t =
    kind === 'error'
      ? { bg: colors.dangerBg, fg: colors.danger, icon: 'alert-circle-outline' as IconName }
      : kind === 'warning'
        ? { bg: colors.risk.MEDIUM.bg, fg: colors.risk.MEDIUM.text, icon: 'alert-outline' as IconName }
        : { bg: colors.accentBg, fg: colors.accentText, icon: 'information-outline' as IconName };
  return (
    <View testID={testID} accessibilityRole={kind === 'error' ? 'alert' : undefined} style={[styles.banner, { backgroundColor: t.bg }]}>
      <Icon name={t.icon} size={18} color={t.fg} />
      <Text style={{ color: t.fg, flex: 1, fontSize: 14, lineHeight: 20 }}>{message}</Text>
    </View>
  );
}

export function EmptyState({ icon, title, message, action }: { icon: IconName; title: string; message: string; action?: React.ReactNode }) {
  const { colors } = useTheme();
  return (
    <View style={styles.empty}>
      <Icon name={icon} size={40} color={colors.textMuted} />
      <AppText variant="heading" style={{ textAlign: 'center' }}>{title}</AppText>
      <AppText variant="secondary" style={{ textAlign: 'center' }}>{message}</AppText>
      {action}
    </View>
  );
}

export function LoadingBlock({ message }: { message: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.empty} accessibilityLiveRegion="polite">
      <ActivityIndicator size="large" color={colors.accent} />
      <AppText variant="secondary">{message}</AppText>
    </View>
  );
}

export function SectionTitle({ children, icon }: { children: React.ReactNode; icon?: IconName }) {
  const { colors } = useTheme();
  return (
    <View style={styles.sectionTitle}>
      {icon ? <Icon name={icon} size={18} color={colors.accent} /> : null}
      <AppText variant="heading">{children}</AppText>
    </View>
  );
}

/** Header button that switches between light and dark mode. */
export function ThemeToggleButton() {
  const { isDark, toggle, colors } = useTheme();
  return (
    <Pressable
      onPress={toggle}
      testID="theme-toggle"
      accessibilityRole="switch"
      accessibilityState={{ checked: isDark }}
      accessibilityLabel="Dark mode"
      accessibilityHint={isDark ? 'Switches to light mode' : 'Switches to dark mode'}
      hitSlop={10}
      style={{ padding: 6 }}
    >
      <Icon name={isDark ? 'white-balance-sunny' : 'weather-night'} size={22} color={colors.accent} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screenContent: { padding: 16, gap: 16, paddingBottom: 32 },
  card: { borderWidth: 1, borderRadius: 14, padding: 16, gap: 10 },
  button: {
    minHeight: 48,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 5, borderRadius: 8, alignSelf: 'flex-start' },
  track: { height: 8, borderRadius: 4, overflow: 'hidden' },
  tag: { borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3, alignSelf: 'flex-start' },
  banner: { flexDirection: 'row', gap: 8, padding: 12, borderRadius: 10, alignItems: 'flex-start' },
  empty: { alignItems: 'center', gap: 8, paddingVertical: 32, paddingHorizontal: 16 },
  sectionTitle: { flexDirection: 'row', alignItems: 'center', gap: 8 },
});
