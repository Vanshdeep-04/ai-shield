import { DarkTheme, DefaultTheme, NavigationContainer, Theme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import React, { useMemo } from 'react';

import { ThemeToggleButton } from '../components/ui';
import EmailPreviewScreen from '../screens/EmailPreviewScreen';
import HistoryScreen from '../screens/HistoryScreen';
import HomeScreen from '../screens/HomeScreen';
import InboxScreen from '../screens/InboxScreen';
import ManualInputScreen from '../screens/ManualInputScreen';
import PrivacyScreen from '../screens/PrivacyScreen';
import ResultScreen from '../screens/ResultScreen';
import { useTheme } from '../theme/ThemeContext';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function RootNavigator() {
  const { colors, isDark } = useTheme();

  const navTheme: Theme = useMemo(() => {
    const base = isDark ? DarkTheme : DefaultTheme;
    return {
      ...base,
      colors: { ...base.colors, primary: colors.accent, background: colors.background, card: colors.surface, text: colors.text, border: colors.border },
    };
  }, [isDark, colors]);

  return (
    <NavigationContainer theme={navTheme}>
      <Stack.Navigator
        screenOptions={{
          headerRight: () => <ThemeToggleButton />,
          headerTitleStyle: { fontWeight: '600' },
          headerTintColor: colors.accent,
          headerStyle: { backgroundColor: colors.surface },
          contentStyle: { backgroundColor: colors.background },
        }}
      >
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'AI Shield' }} />
        <Stack.Screen name="Inbox" component={InboxScreen} options={{ title: 'Choose an email' }} />
        <Stack.Screen name="EmailPreview" component={EmailPreviewScreen} options={{ title: 'Review email' }} />
        <Stack.Screen name="ManualInput" component={ManualInputScreen} options={{ title: 'Paste an email' }} />
        <Stack.Screen name="Result" component={ResultScreen} options={{ title: 'Result' }} />
        <Stack.Screen name="History" component={HistoryScreen} options={{ title: 'History' }} />
        <Stack.Screen name="Privacy" component={PrivacyScreen} options={{ title: 'Privacy and settings' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
