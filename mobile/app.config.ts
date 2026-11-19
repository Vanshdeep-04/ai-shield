import type { ExpoConfig } from 'expo/config';

/**
 * AI Shield app configuration.
 * Public values come from mobile/.env (see mobile/.env.example). Nothing here is secret:
 * OAuth client IDs are public identifiers, and the AI provider key never reaches the app.
 */

// The Google Sign-In config plugin always requires an iOS URL scheme. AI Shield targets Android,
// so a placeholder is used unless a real iOS client is configured.
const iosUrlScheme =
  process.env.GOOGLE_IOS_URL_SCHEME || 'com.googleusercontent.apps.aishield-ios-not-configured';

const config: ExpoConfig = {
  name: 'AI Shield',
  slug: 'ai-shield',
  scheme: 'aishield',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  android: {
    package: 'com.aishield.app',
    adaptiveIcon: {
      backgroundColor: '#E6F1FB',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
    // Only internet access is needed: no SMS, contacts or storage permissions.
    permissions: ['android.permission.INTERNET'],
    blockedPermissions: [
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.READ_SMS',
      'android.permission.RECEIVE_SMS',
      'android.permission.READ_CONTACTS',
      'android.permission.SYSTEM_ALERT_WINDOW',
    ],
  },
  ios: {
    supportsTablet: false,
    bundleIdentifier: 'com.aishield.app',
  },
  web: {
    favicon: './assets/favicon.png',
  },
  plugins: [['@react-native-google-signin/google-signin', { iosUrlScheme }], 'expo-dev-client'],
  owner: 'vanshdeep_04',
  extra: {
    // EAS project ID (public identifier, not a secret).
    eas: { projectId: '4d48a866-e91a-4e3e-84d8-20ec71d64e91' },
  },
};

export default config;
