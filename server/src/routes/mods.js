import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import express from 'express';
import multer from 'multer';
import { HttpError, asyncRoute, randomId, sha256, text } from '../util.js';
import { rateLimit } from '../limits.js';

const engines = new Set(['vslice', 'psych', 'codename', 'other']);
const sorts = {
  new: 'created_at DESC',
  old: 'created_at ASC',
  downloads: 'downloads DESC, created_at DESC',
  title: 'title COLLATE NOCASE ASC'
};

const hashFile = (file) => new Promise((resolve, reject) => {
  const hash = crypto.createHash('sha256');
  fs.createReadStream(file).on('error', reject).on('data', (chunk) => hash.update(chunk)).on('end', () => resolve(hash.digest('hex')));
});

const isZip = (file) => {
  const fd = fs.openSync(file, 'r');
  try {
    const head = Buffer.alloc(4);
    fs.readSync(fd, head, 0, 4, 0);
    return head[0] === 0x50 && head[1] === 0x4b && (head[2] === 0x03 || head[2] === 0x05) && (head[3] === 0x04 || head[3] === 0x06);
  } finally {
    fs.closeSync(fd);
  }
};

const view = (row, publicUrl) => ({
  id: row.id,
  title: row.title,
  description: row.description,
  author: row.author,
  engine: row.engine,
  tags: row.tags ? row.tags.split(',') : [],
  fileName: row.file_name,
  size: row.size,
  sha256: row.sha256,
  downloads: row.downloads,
  hidden: Boolean(row.hidden),
  createdAt: row.created_at,
  downloadUrl: `${publicUrl}/api/mods/${row.id}/download`
});

export function modsRouter({ db, config }) {
  const router = express.Router();
  const tmp = path.join(config.uploadDir, 'tmp');
  fs.mkdirSync(tmp, { recursive: true });
  const upload = multer({
    dest: tmp,
    limits: { fileSize: config.maxUploadMb * 1024 * 1024, files: 1, fields: 10, fieldSize: 8 * 1024 }
  });
  const uploadLimit = rateLimit({ limit: config.uploadsPerHour, name: 'uploads' });
  const base = (req) => config.publicUrl || `${req.protocol}://${req.get('host')}`;

  router.get('/', (req, res) => {
    const page = Math.max(1, Number.parseInt(req.query.page, 10) || 1);
    const size = Math.min(50, Math.max(1, Number.parseInt(req.query.size, 10) || 20));
    const where = [];
    const params = [];
    if (!(req.isAdmin && req.query.hidden === 'true')) where.push('hidden = 0');
    const query = text(req.query.q, 100);
    if (query) {
      where.push("(title LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')");
      const like = `%${query.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
      params.push(like, like, like, like);
    }
    const engine = text(req.query.engine, 20).toLowerCase();
    if (engine) {
      if (!engines.has(engine)) throw new HttpError(400, 'Unknown engine.');
      where.push('engine = ?');
      params.push(engine);
    }
    const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const order = sorts[req.query.sort] || sorts.new;
    const total = db.prepare(`SELECT COUNT(*) AS n FROM mods ${clause}`).get(...params).n;
    const rows = db.prepare(`SELECT * FROM mods ${clause} ORDER BY ${order} LIMIT ? OFFSET ?`).all(...params, size, (page - 1) * size);
    res.json({ total, page, size, mods: rows.map((row) => view(row, base(req))) });
  });

  router.get('/:id', (req, res) => {
    const row = db.prepare('SELECT * FROM mods WHERE id = ?').get(req.params.id);
    if (!row || (row.hidden && !req.isAdmin)) throw new HttpError(404, 'Mod not found.');
    res.json(view(row, base(req)));
  });

  router.get('/:id/download', (req, res) => {
    const row = db.prepare('SELECT * FROM mods WHERE id = ?').get(req.params.id);
    if (!row || (row.hidden && !req.isAdmin)) throw new HttpError(404, 'Mod not found.');
    const file = path.join(config.uploadDir, `${row.id}.zip`);
    if (!fs.existsSync(file)) throw new HttpError(410, 'The file is no longer available.');
    db.prepare('UPDATE mods SET downloads = downloads + 1 WHERE id = ?').run(row.id);
    res.download(file, row.file_name);
  });

  router.post('/', uploadLimit, upload.single('file'), asyncRoute(async (req, res) => {
    const file = req.file;
    try {
      if (!file) throw new HttpError(400, 'Send the mod as a .zip in the "file" field.');
      if (!isZip(file.path)) throw new HttpError(400, 'Only .zip files can be uploaded.');
      const title = text(req.body.title, 80);
      if (!title) throw new HttpError(400, 'A title is required.');
      const engine = text(req.body.engine, 20, 'vslice').toLowerCase() || 'vslice';
      if (!engines.has(engine)) throw new HttpError(400, 'Unknown engine.');
      const hash = await hashFile(file.path);
      const existing = db.prepare('SELECT id FROM mods WHERE sha256 = ?').get(hash);
      if (existing) throw Object.assign(new HttpError(409, 'This exact file was already uploaded.'), { extra: { id: existing.id } });
      const id = randomId();
      const deleteToken = crypto.randomBytes(24).toString('base64url');
      const tags = text(req.body.tags, 200).split(',').map((t) => t.trim().toLowerCase()).filter(Boolean).slice(0, 10).join(',');
      const fileName = (text(file.originalname, 120) || `${id}.zip`).replace(/[\\/:*?"<>|]+/g, '_');
      fs.renameSync(file.path, path.join(config.uploadDir, `${id}.zip`));
      db.prepare(`INSERT INTO mods (id, title, description, author, engine, tags, file_name, size, sha256, delete_hash, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`).run(
        id, title, text(req.body.description, 2000), text(req.body.author, 60), engine, tags,
        fileName.toLowerCase().endsWith('.zip') ? fileName : `${fileName}.zip`, file.size, hash, sha256(deleteToken), new Date().toISOString()
      );
      const row = db.prepare('SELECT * FROM mods WHERE id = ?').get(id);
      res.status(201).json({ ...view(row, base(req)), deleteToken });
    } finally {
      if (file && fs.existsSync(file.path)) fs.rmSync(file.path, { force: true });
    }
  }));

  router.patch('/:id', (req, res) => {
    if (!req.isAdmin) throw new HttpError(403, 'Admin token required.');
    const row = db.prepare('SELECT * FROM mods WHERE id = ?').get(req.params.id);
    if (!row) throw new HttpError(404, 'Mod not found.');
    const body = req.body || {};
    db.prepare('UPDATE mods SET title = ?, description = ?, hidden = ? WHERE id = ?').run(
      text(body.title, 80) || row.title,
      body.description === undefined ? row.description : text(body.description, 2000),
      body.hidden === undefined ? row.hidden : (body.hidden ? 1 : 0),
      row.id
    );
    res.json(view(db.prepare('SELECT * FROM mods WHERE id = ?').get(row.id), base(req)));
  });

  router.delete('/:id', (req, res) => {
    const row = db.prepare('SELECT * FROM mods WHERE id = ?').get(req.params.id);
    if (!row) throw new HttpError(404, 'Mod not found.');
    const token = req.get('X-Delete-Token') || '';
    if (!req.isAdmin && (!token || sha256(token) !== row.delete_hash)) throw new HttpError(403, 'Wrong delete token.');
    db.prepare('DELETE FROM mods WHERE id = ?').run(row.id);
    fs.rmSync(path.join(config.uploadDir, `${row.id}.zip`), { force: true });
    res.status(204).end();
  });

  router.uploadLimit = uploadLimit;
  return router;
}
