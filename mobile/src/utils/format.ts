import type { RiskLevel } from '../types/api';

export function formatDateTime(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  try {
    return d.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  } catch {
    return d.toISOString().replace('T', ' ').slice(0, 16);
  }
}

/** Gmail "Date" header → short label: time for today, otherwise day and month. */
export function formatEmailDate(header: string | undefined, now: Date = new Date()): string {
  if (!header) return '';
  const d = new Date(header);
  if (Number.isNaN(d.getTime())) return '';
  const sameDay = d.toDateString() === now.toDateString();
  try {
    return sameDay
      ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
      : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  } catch {
    return d.toISOString().slice(0, 10);
  }
}

export const RISK_LABEL: Record<RiskLevel, string> = {
  HIGH: 'High risk',
  MEDIUM: 'Medium risk',
  LOW: 'Low risk',
  UNKNOWN: 'Unknown',
};

export const RISK_HEADLINE: Record<RiskLevel, string> = {
  HIGH: 'This email shows strong signs of phishing',
  MEDIUM: 'This email has some warning signs',
  LOW: 'No strong warning signs found',
  UNKNOWN: "AI Shield couldn't finish the check",
};

export function humanizeCode(code: string): string {
  const s = code.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}
