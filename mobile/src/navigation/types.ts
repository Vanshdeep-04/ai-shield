import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { AnalysisResponse } from '../types/api';

export type RootStackParamList = {
  Home: undefined;
  Inbox: undefined;
  EmailPreview: { messageId: string };
  ManualInput: undefined;
  Result: { analysis?: AnalysisResponse; analysisId?: string };
  History: undefined;
  Privacy: undefined;
};

export type ScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, T>;
