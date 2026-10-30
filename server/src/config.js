import path from 'node:path';

const number = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

export function loadConfig(env = process.env) {
  const dataDir = path.resolve(env.DATA_DIR || './data');
  return {
    port: number(env.PORT, 3000),
    host: env.HOST || '0.0.0.0',
    dataDir,
    uploadDir: path.join(dataDir, 'mods'),
    databaseFile: env.DATABASE_FILE ? path.resolve(env.DATABASE_FILE) : path.join(dataDir, 'v-converter.db'),
    adminToken: env.ADMIN_TOKEN || '',
    publicUrl: (env.PUBLIC_URL || '').replace(/\/+$/, ''),
    trustProxy: env.TRUST_PROXY === 'true' || env.TRUST_PROXY === '1',
    maxUploadMb: number(env.MAX_UPLOAD_MB, 100),
    uploadsPerHour: number(env.UPLOADS_PER_HOUR, 10),
    reportsPerHour: number(env.REPORTS_PER_HOUR, 60),
    roomsPerHour: number(env.ROOMS_PER_HOUR, 30),
    githubRepo: env.GITHUB_REPO || 'BrenninhoTools/V-Converter',
    githubToken: env.GITHUB_TOKEN || '',
    latestVersion: env.LATEST_VERSION || '',
    releaseCacheMinutes: number(env.RELEASE_CACHE_MINUTES, 10),
    roomIdleMinutes: number(env.ROOM_IDLE_MINUTES, 120),
    maxPeersPerRoom: number(env.MAX_PEERS_PER_ROOM, 16),
    maxMessageMb: number(env.MAX_MESSAGE_MB, 8)
  };
}
