import { HttpError } from './util.js';

export function rateLimit({ limit, windowMs = 60 * 60 * 1000, name }) {
  const hits = new Map();
  const sweep = setInterval(() => {
    const now = Date.now();
    for (const [key, list] of hits) {
      const fresh = list.filter((t) => now - t < windowMs);
      if (fresh.length === 0) hits.delete(key);
      else hits.set(key, fresh);
    }
  }, Math.min(windowMs, 10 * 60 * 1000));
  sweep.unref();
  const middleware = (req, res, next) => {
    if (req.isAdmin) return next();
    const now = Date.now();
    const key = req.ip || 'unknown';
    const list = (hits.get(key) || []).filter((t) => now - t < windowMs);
    if (list.length >= limit) {
      res.set('Retry-After', String(Math.ceil((windowMs - (now - list[0])) / 1000)));
      return next(new HttpError(429, `Too many ${name}; try again later.`));
    }
    list.push(now);
    hits.set(key, list);
    next();
  };
  middleware.reset = () => hits.clear();
  return middleware;
}
