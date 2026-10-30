import http from 'node:http';
import { loadConfig } from './config.js';
import { openDatabase } from './db.js';
import { createApp } from './app.js';
import { Rooms, attachCollab } from './collab.js';

export function startServer(config = loadConfig(), options = {}) {
  const db = openDatabase(config.databaseFile);
  const rooms = new Rooms(config);
  const app = createApp({ config, db, rooms, ...options });
  const server = http.createServer(app);
  const wss = attachCollab(server, { rooms, config });
  return new Promise((resolve) => {
    server.listen(config.port, config.host, () => {
      const stop = () => new Promise((done) => {
        rooms.close();
        for (const client of wss.clients) client.terminate();
        wss.close();
        server.close(() => {
          db.close();
          done();
        });
      });
      resolve({ server, app, db, rooms, port: server.address().port, stop });
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const config = loadConfig();
  startServer(config).then(({ port, stop }) => {
    console.log(`V-Converter server listening on ${config.host}:${port}`);
    if (!config.adminToken) console.log('ADMIN_TOKEN is not set: moderation, announcements and report reading are disabled.');
    const shutdown = () => stop().then(() => process.exit(0));
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  });
}
