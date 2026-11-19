/** TypeScript mirror of the backend's v1 API contract (see docs/API.md). */

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
export type MessageSource = 'GMAIL' | 'MANUAL';
export type AiStatus = 'COMPLETED' | 'INCONCLUSIVE' | 'UNAVAILABLE';
export type FeedbackVerdict = 'ACCURATE' | 'FALSE_POSITIVE' | 'FALSE_NEGATIVE' | 'UNSURE';

export interface AnalysisRequest {
  source: MessageSource;
  from?: string;
  replyTo?: string;
  subject?: string;
  bodyText?: string;
  bodyHtml?: string;
  links?: { url: string; text?: string }[];
  receivedAt?: string;
}

export interface Indicator {
  code: string;
  description: string;
  weight: number;
}

export interface UrlFinding {
  url: string;
  host?: string;
  registrableDomain?: string;
  displayText?: string;
  score: number;
  indicators: Indicator[];
}

export interface AiIndicator {
  category: string;
  description: string;
  evidence?: string;
}

export interface Evidence {
  source: 'AI_CONTENT_ANALYSIS' | 'URL_ANALYSIS' | 'SENDER_METADATA';
  type: 'OBSERVED' | 'AI_INTERPRETATION';
  code: string;
  description: string;
  detail?: string;
}

export interface Feedback {
  verdict: FeedbackVerdict;
  comment?: string;
  updatedAt?: string;
}

export interface AnalysisResponse {
  id?: string;
  createdAt: string;
  saved: boolean;
  source: MessageSource;
  message: { senderDomain?: string; subjectPreview?: string };
  riskLevel: RiskLevel;
  riskScore?: number;
  explanation: string;
  recommendedAction: string;
  ai: {
    status: AiStatus;
    riskLevel?: RiskLevel;
    riskScore?: number;
    isPhishing?: boolean;
    explanation?: string;
    indicators?: AiIndicator[];
    benignFactors?: string[];
    recommendedAction?: string;
    unavailableReason?: string;
  };
  urlAnalysis: { score: number; linksChecked: number; findings: UrlFinding[]; unavailableChecks: string[] };
  senderChecks: { score: number; senderAvailable: boolean; findings: Indicator[] };
  componentScores: { ai?: number; url: number; metadata: number };
  evidence: Evidence[];
  limitations: string[];
  feedback?: Feedback;
}

export interface HistoryItem {
  id: string;
  createdAt: string;
  source: MessageSource;
  senderDomain?: string;
  subjectPreview?: string;
  riskLevel: RiskLevel;
  riskScore?: number;
  aiStatus: AiStatus;
  feedbackVerdict?: FeedbackVerdict;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ServiceStatus {
  service: string;
  apiVersion: string;
  aiConfigured: boolean;
  authMode: 'google' | 'dev';
}
