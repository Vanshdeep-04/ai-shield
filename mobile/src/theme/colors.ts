/** Style A, "Clean light": white cards, blue accent. A matching dark palette for the dark mode toggle. */

export type RiskTone = { bg: string; fg: string; text: string };

export type Palette = {
  mode: 'light' | 'dark';
  background: string;
  surface: string;
  surfaceAlt: string;
  border: string;
  text: string;
  textSecondary: string;
  textMuted: string;
  accent: string;
  accentBg: string;
  accentText: string;
  onAccent: string;
  danger: string;
  dangerBg: string;
  success: string;
  risk: { HIGH: RiskTone; MEDIUM: RiskTone; LOW: RiskTone; UNKNOWN: RiskTone };
};

export const lightPalette: Palette = {
  mode: 'light',
  background: '#F5F7FA',
  surface: '#FFFFFF',
  surfaceAlt: '#F0F3F7',
  border: '#E1E5EA',
  text: '#1A1D21',
  textSecondary: '#535C68',
  textMuted: '#7A838F',
  accent: '#185FA5',
  accentBg: '#E6F1FB',
  accentText: '#0C447C',
  onAccent: '#FFFFFF',
  danger: '#A32D2D',
  dangerBg: '#FCEBEB',
  success: '#3B6D11',
  risk: {
    HIGH: { bg: '#FCEBEB', fg: '#C93C3B', text: '#791F1F' },
    MEDIUM: { bg: '#FAEEDA', fg: '#BA7517', text: '#633806' },
    LOW: { bg: '#EAF3DE', fg: '#3B6D11', text: '#27500A' },
    UNKNOWN: { bg: '#F1EFE8', fg: '#5F5E5A', text: '#444441' },
  },
};

export const darkPalette: Palette = {
  mode: 'dark',
  background: '#0E1116',
  surface: '#171B22',
  surfaceAlt: '#1F242D',
  border: '#2A303A',
  text: '#E8EAED',
  textSecondary: '#AEB6C1',
  textMuted: '#87909C',
  accent: '#6AA8E8',
  accentBg: '#10263F',
  accentText: '#B5D4F4',
  onAccent: '#0B1A2B',
  danger: '#F09595',
  dangerBg: '#3A1414',
  success: '#97C459',
  risk: {
    HIGH: { bg: '#3A1414', fg: '#F09595', text: '#F7C1C1' },
    MEDIUM: { bg: '#3A2A0E', fg: '#EF9F27', text: '#FAC775' },
    LOW: { bg: '#1C2E10', fg: '#97C459', text: '#C0DD97' },
    UNKNOWN: { bg: '#26262A', fg: '#B4B2A9', text: '#D3D1C7' },
  },
};
