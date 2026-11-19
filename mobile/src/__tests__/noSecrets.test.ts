import fs from 'fs';
import path from 'path';

/** The Gemini key must never be bundled into the mobile app. */
const ROOT = path.resolve(__dirname, '..', '..');

function sourceFiles(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : sourceFiles(p);
    return /\.(ts|tsx|js|json)$/.test(e.name) ? [p] : [];
  });
}

describe('mobile bundle secrets', () => {
  const files = [...sourceFiles(path.join(ROOT, 'src')), path.join(ROOT, 'App.tsx'), path.join(ROOT, 'app.config.ts')];

  it('never references the Gemini API key or Gemini endpoints', () => {
    for (const f of files) {
      const text = fs.readFileSync(f, 'utf8');
      expect({ file: f, hit: /GEMINI_API_KEY|generativelanguage\.googleapis\.com/.test(text) }).toEqual({ file: f, hit: false });
    }
  });

  it('contains no Google API keys or client secrets', () => {
    for (const f of files) {
      const text = fs.readFileSync(f, 'utf8');
      expect({ file: f, hit: /AIza[0-9A-Za-z_-]{30,}|GOCSPX-|client_secret/.test(text) }).toEqual({ file: f, hit: false });
    }
  });

  it('only exposes non-secret EXPO_PUBLIC variables', () => {
    const vars = new Set<string>();
    for (const f of files) {
      for (const m of fs.readFileSync(f, 'utf8').matchAll(/EXPO_PUBLIC_[A-Z_]+/g)) vars.add(m[0]);
    }
    expect([...vars].sort()).toEqual(['EXPO_PUBLIC_API_BASE_URL', 'EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID']);
  });
});

describe('AI provider is not disclosed in the app', () => {
  it('never names the AI model or provider in app code or screen text', () => {
    const appFiles = [...sourceFiles(path.join(ROOT, 'src')), path.join(ROOT, 'App.tsx'), path.join(ROOT, 'app.config.ts')];
    for (const f of appFiles) {
      const text = fs.readFileSync(f, 'utf8');
      expect({ file: f, hit: /gemini|flash-lite|\bai\.model\b|aiModel/i.test(text) }).toEqual({ file: f, hit: false });
    }
  });
});
