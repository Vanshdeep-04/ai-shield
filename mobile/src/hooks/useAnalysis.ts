import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../services/api';
import { useApp } from '../state/AppContext';
import type { AnalysisRequest, AnalysisResponse } from '../types/api';

export type AnalysisStage = 'idle' | 'running' | 'error';

/** Runs one analysis request with cancellation, and reports progress for the loading UI. */
export function useAnalysis() {
  const { api, bumpHistory } = useApp();
  const [stage, setStage] = useState<AnalysisStage>('idle');
  const [error, setError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const controller = useRef<AbortController | null>(null);

  useEffect(() => () => controller.current?.abort(), []);

  useEffect(() => {
    if (stage !== 'running') return;
    setElapsed(0);
    const t = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [stage]);

  const run = useCallback(
    async (request: AnalysisRequest): Promise<AnalysisResponse | null> => {
      controller.current?.abort();
      const c = new AbortController();
      controller.current = c;
      setStage('running');
      setError(null);
      try {
        const result = await api.analyze(request, c.signal);
        setStage('idle');
        bumpHistory();
        return result;
      } catch (e) {
        if (e instanceof ApiError && e.kind === 'cancelled') {
          setStage('idle');
          return null;
        }
        setError(e instanceof Error ? e.message : 'Analysis failed.');
        setStage('error');
        return null;
      }
    },
    [api, bumpHistory],
  );

  const cancel = useCallback(() => controller.current?.abort(), []);

  const progressMessage =
    elapsed < 2 ? 'Sending the email to AI Shield…' : elapsed < 5 ? 'Checking links and sender details…' : 'AI content analysis in progress…';

  return { stage, error, run, cancel, progressMessage };
}
