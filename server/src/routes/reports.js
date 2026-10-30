import express from 'express';
import { HttpError, sha256, text } from '../util.js';
import { rateLimit } from '../limits.js';

const kinds = new Set(['crash', 'conversion', 'other']);

export function reportsRouter({ db, config }) {
  const router = express.Router();
  const limit = rateLimit({ limit: config.reportsPerHour, name: 'reports' });

  router.post('/', limit, (req, res) => {
    const body = req.body || {};
    const message = text(body.message, 2000);
    if (!message) throw new HttpError(400, 'A message is required.');
    const kind = kinds.has(body.kind) ? body.kind : 'other';
    const context = typeof body.context === 'object' && body.context !== null ? JSON.stringify(body.context).slice(0, 8000) : text(body.context, 8000);
    const result = db.prepare('INSERT INTO reports (kind, platform, app_version, message, stack, context, client, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
      .run(kind, text(body.platform, 20).toLowerCase(), text(body.appVersion, 40), message, text(body.stack, 16000), context, sha256(`${req.ip}`).slice(0, 12), new Date().toISOString());
    res.status(201).json({ id: Number(result.lastInsertRowid) });
  });

  router.get('/', (req, res) => {
    if (!req.isAdmin) throw new HttpError(403, 'Admin token required.');
    const size = Math.min(200, Math.max(1, Number.parseInt(req.query.size, 10) || 50));
    const where = [];
    const params = [];
    if (kinds.has(req.query.kind)) {
      where.push('kind = ?');
      params.push(req.query.kind);
    }
    const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const rows = db.prepare(`SELECT * FROM reports ${clause} ORDER BY id DESC LIMIT ?`).all(...params, size);
    res.json({
      reports: rows.map((row) => ({
        id: row.id, kind: row.kind, platform: row.platform, appVersion: row.app_version, message: row.message,
        stack: row.stack, context: row.context, client: row.client, createdAt: row.created_at
      }))
    });
  });

  router.delete('/:id', (req, res) => {
    if (!req.isAdmin) throw new HttpError(403, 'Admin token required.');
    db.prepare('DELETE FROM reports WHERE id = ?').run(Number(req.params.id));
    res.status(204).end();
  });

  router.limit = limit;
  return router;
}
