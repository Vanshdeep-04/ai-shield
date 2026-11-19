import { singleFlight } from '../utils/singleFlight';

describe('singleFlight', () => {
  it('shares one in-flight call between concurrent callers', async () => {
    let calls = 0;
    let release!: (v: string) => void;
    const once = singleFlight(() => {
      calls += 1;
      return new Promise<string>((r) => (release = r));
    });
    const results = Promise.all(Array.from({ length: 15 }, () => once()));
    expect(calls).toBe(1);
    release('token');
    expect(await results).toEqual(Array(15).fill('token'));
  });

  it('starts a fresh call after the previous one settles, including after errors', async () => {
    let n = 0;
    const once = singleFlight(async () => {
      n += 1;
      if (n === 1) throw new Error('boom');
      return n;
    });
    await expect(once()).rejects.toThrow('boom');
    await expect(once()).resolves.toBe(2);
    await expect(once()).resolves.toBe(3);
  });
});
