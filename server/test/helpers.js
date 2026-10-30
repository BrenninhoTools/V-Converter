import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { loadConfig } from '../src/config.js';
import { startServer } from '../src/index.js';

export async function boot(env = {}, options = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vcs-'));
  const config = loadConfig({ DATA_DIR: dir, PORT: '1', ADMIN_TOKEN: 'secret-admin', ...env });
  config.port = 0;
  config.host = '127.0.0.1';
  const running = await startServer(config, options);
  const base = `http://127.0.0.1:${running.port}`;
  return {
    ...running,
    base,
    dir,
    async close() {
      await running.stop();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  };
}

export function zip(content = 'hello') {
  const name = Buffer.from('mod.txt');
  const data = Buffer.from(content);
  const local = Buffer.alloc(30);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(10, 4);
  local.writeUInt32LE(data.length, 18);
  local.writeUInt32LE(data.length, 22);
  local.writeUInt16LE(name.length, 26);
  return Buffer.concat([local, name, data]);
}

export function form(fields, file, fileName = 'mod.zip') {
  const body = new FormData();
  for (const [key, value] of Object.entries(fields)) body.append(key, value);
  if (file) body.append('file', new Blob([file]), fileName);
  return body;
}
