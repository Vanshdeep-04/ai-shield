/**
 * Wraps an async function so that concurrent callers share ONE in-flight call.
 * Needed because the Google Sign-In SDK allows only one getTokens/signInSilently call at a time:
 * overlapping calls overwrite each other and the earlier promises never settle.
 */
export function singleFlight<T>(fn: () => Promise<T>): () => Promise<T> {
  let inFlight: Promise<T> | null = null;
  return () => {
    if (!inFlight) {
      inFlight = fn().finally(() => {
        inFlight = null;
      });
    }
    return inFlight;
  };
}
