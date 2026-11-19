/* eslint-env jest */
// In-memory AsyncStorage for tests (used only for the theme preference).
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);
