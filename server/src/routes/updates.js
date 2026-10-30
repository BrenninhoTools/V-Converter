import express from 'express';
import { HttpError, compareVersions, text } from '../util.js';

const platformAssets = {
  android: /android|\.apk$/i,
  windows: /windows|\.exe$|\.msi$/i,
  linux: /linux|\.deb$|\.tar\.gz$|\.appimage$/i
};

export function createReleaseSource(config, fetcher = globalThis.fetch) {
  let cached = null;
  let cachedAt = 0;
  return async function latest() {
    if (cached && Date.now() - cachedAt < config.releaseCacheMinutes * 60 * 1000) return cached;
    try {
      const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'v-converter-server' };
      if (config.githubToken) headers.Authorization = `Bearer ${config.githubToken}`;
      const response = await fetcher(`https://api.github.com/repos/${config.githubRepo}/releases/latest`, { headers, signal: AbortSignal.timeout(8000) });
      if (!response.ok) throw new Error(`GitHub answered ${response.status}`);
      const release = await response.json();
      cached = {
        version: String(release.tag_name || '').replace(/^v/i, ''),
        name: release.name || release.tag_name || '',
        notes: release.body || '',
        url: release.html_url || '',
        publishedAt: release.published_at || '',
        assets: (release.assets || []).map((a) => ({ name: a.name, url: a.browser_download_url, size: a.size }))
      };
      cachedAt = Date.now();
      return cached;
    } catch (error) {
      if (cached) return cached;
      if (config.latestVersion) return { version: config.latestVersion, name: config.latestVersion, notes: '', url: `https://github.com/${config.githubRepo}/releases`, publishedAt: '', assets: [] };
      return null;
    }
  };
}

export function updatesRouter({ db, latest }) {
  const router = express.Router();

  router.get('/version', async (req, res, next) => {
    try {
      const platform = text(req.query.platform, 20).toLowerCase();
      const current = text(req.query.current, 40);
      const release = await latest();
      if (!release || !release.version) return res.json({ latest: null, current, updateAvailable: false });
      const pattern = platformAssets[platform];
      const asset = pattern ? release.assets.find((a) => pattern.test(a.name)) : null;
      res.json({
        latest: release.version,
        current,
        updateAvailable: current ? compareVersions(release.version, current) > 0 : false,
        name: release.name,
        notes: release.notes,
        url: asset ? asset.url : release.url,
        page: release.url,
        publishedAt: release.publishedAt
      });
    } catch (error) {
      next(error);
    }
  });

  router.get('/announcements', (req, res) => {
    const platform = text(req.query.platform, 20).toLowerCase();
    const now = new Date().toISOString();
    const rows = db.prepare('SELECT * FROM announcements WHERE expires_at IS NULL OR expires_at > ? ORDER BY created_at DESC LIMIT 20').all(now);
    res.json({
      announcements: rows
        .filter((row) => !row.platforms || !platform || row.platforms.split(',').includes(platform))
        .map((row) => ({ id: row.id, title: row.title, body: row.body, level: row.level, url: row.url, createdAt: row.created_at, expiresAt: row.expires_at }))
    });
  });

  router.post('/announcements', (req, res) => {
    if (!req.isAdmin) throw new HttpError(403, 'Admin token required.');
    const body = req.body || {};
    const title = text(body.title, 120);
    if (!title) throw new HttpError(400, 'A title is required.');
    const level = ['info', 'warning', 'update'].includes(body.level) ? body.level : 'info';
    const platforms = Array.isArray(body.platforms) ? body.platforms.map((p) => text(p, 20).toLowerCase()).filter(Boolean).join(',') : '';
    let expires = null;
    if (body.expiresAt) {
      const date = new Date(body.expiresAt);
      if (Number.isNaN(date.getTime())) throw new HttpError(400, 'expiresAt is not a date.');
      expires = date.toISOString();
    }
    const result = db.prepare('INSERT INTO announcements (title, body, level, url, platforms, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
      .run(title, text(body.body, 2000), level, text(body.url, 300), platforms, new Date().toISOString(), expires);
    res.status(201).json({ id: Number(result.lastInsertRowid) });
  });

  router.delete('/announcements/:id', (req, res) => {
    if (!req.isAdmin) throw new HttpError(403, 'Admin token required.');
    const result = db.prepare('DELETE FROM announcements WHERE id = ?').run(Number(req.params.id));
    if (result.changes === 0) throw new HttpError(404, 'Announcement not found.');
    res.status(204).end();
  });

  return router;
}
