import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';

import { darkPalette, lightPalette, Palette } from './colors';

/** "system" follows the phone's setting; "light"/"dark" are explicit choices made with the toggle. */
export type ThemePreference = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'aishield.themePreference';

type ThemeContextValue = {
  colors: Palette;
  isDark: boolean;
  preference: ThemePreference;
  setPreference: (p: ThemePreference) => void;
  /** Flips between light and dark (leaving "system" mode). */
  toggle: () => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children, initialPreference }: { children: React.ReactNode; initialPreference?: ThemePreference }) {
  const systemScheme = useColorScheme();
  const [preference, setPreferenceState] = useState<ThemePreference>(initialPreference ?? 'system');

  useEffect(() => {
    if (initialPreference) return;
    let active = true;
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (active && (stored === 'light' || stored === 'dark' || stored === 'system')) {
          setPreferenceState(stored);
        }
      })
      .catch(() => undefined); // preference is a convenience; ignore storage failures
    return () => {
      active = false;
    };
  }, [initialPreference]);

  const setPreference = useCallback((p: ThemePreference) => {
    setPreferenceState(p);
    AsyncStorage.setItem(STORAGE_KEY, p).catch(() => undefined);
  }, []);

  const isDark = preference === 'dark' || (preference === 'system' && systemScheme === 'dark');

  const toggle = useCallback(() => setPreference(isDark ? 'light' : 'dark'), [isDark, setPreference]);

  const value = useMemo<ThemeContextValue>(
    () => ({ colors: isDark ? darkPalette : lightPalette, isDark, preference, setPreference, toggle }),
    [isDark, preference, setPreference, toggle],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}
