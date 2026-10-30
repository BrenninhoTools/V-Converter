import fs from 'node:fs';
import express from 'express';
import { HttpError, safeEqual } from './util.js';
import { modsRouter } from './routes/mods.js';
import { updatesRouter, createReleaseSource } from './routes/updates.js';
import { reportsRouter } from './routes/reports.js';
import { roomsRouter } from './collab.js';

export function createApp({ config, db, rooms, latest = createReleaseSource(config) }) {
  fs.mkdirSync(config.uploadDir, { recursive: true });
  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', true);

  app.use((req, res, next) => {
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Delete-Token');
    res.set('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
    res.set('X-Content-Type-Options', 'nosniff');
    if (req.method === 'OPTIONS') return res.status(204).end();
    const auth = req.get('Authorization') || '';
    req.isAdmin = Boolean(config.adminToken) && auth.startsWith('Bearer ') && safeEqual(auth.slice(7), config.adminToken);
    next();
  });
  app.use(express.json({ limit: '256kb' }));

  app.get('/', (req, res) => res.json({ name: 'V-Converter server', version: '0.0.1', status: 'ok' }));
  app.get('/api/health', (req, res) => res.json({ status: 'ok', time: new Date().toISOString() }));

  const mods = modsRouter({ db, config });
  const reports = reportsRouter({ db, config });
  const roomRoutes = roomsRouter({ rooms, config });
  app.use('/api/mods', mods);
  app.use('/api', updatesRouter({ db, latest }));
  app.use('/api/reports', reports);
  app.use('/api/rooms', roomRoutes);
  app.limits = [mods.uploadLimit, reports.limit, roomRoutes.limit, roomRoutes.assetLimit];

  app.use((req, res, next) => next(new HttpError(404, 'Not found.')));
  app.use((error, req, res, next) => {
    let status = error.status || error.statusCode || 500;
    let message = error.message || 'Server error.';
    if (error.code === 'LIMIT_FILE_SIZE') {
      status = 413;
      message = `The file is larger than ${config.maxUploadMb} MB.`;
    } else if (error.name === 'MulterError') {
      status = 400;
    } else if (error.type === 'entity.too.large') {
      status = 413;
      message = 'The request is too large.';
    } else if (error.type === 'entity.parse.failed') {
      status = 400;
      message = 'The body is not valid JSON.';
    }
    if (status >= 500) console.error(error);
    res.status(status).json({ error: status >= 500 ? 'Server error.' : message, ...(error.extra || {}) });
  });
  return app;
}
