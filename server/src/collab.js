import { WebSocketServer } from 'ws';
import express from 'express';
import { HttpError, roomCode, text } from './util.js';
import { rateLimit } from './limits.js';

const MAX_LOG = 5000;

export class Rooms {
  constructor(config) {
    this.config = config;
    this.rooms = new Map();
    this.sweeper = setInterval(() => this.sweep(), 60 * 1000);
    this.sweeper.unref();
  }

  create(title) {
    let code = roomCode();
    while (this.rooms.has(code)) code = roomCode();
    const room = { code, title: text(title, 80), peers: new Map(), snapshot: null, ops: [], seq: 0, touched: Date.now(), nextPeer: 1 };
    this.rooms.set(code, room);
    return room;
  }

  get(code) {
    return this.rooms.get(String(code || '').toUpperCase()) || null;
  }

  info(room) {
    return { code: room.code, title: room.title, peers: [...room.peers.values()].map((p) => ({ id: p.id, name: p.name })), hasSnapshot: room.snapshot !== null, seq: room.seq };
  }

  sweep() {
    const limit = this.config.roomIdleMinutes * 60 * 1000;
    for (const [code, room] of this.rooms) {
      if (room.peers.size === 0 && Date.now() - room.touched > limit) this.rooms.delete(code);
    }
  }

  close() {
    clearInterval(this.sweeper);
  }
}

const send = (socket, message) => {
  if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(message));
};

const broadcast = (room, message, except) => {
  const payload = JSON.stringify(message);
  for (const peer of room.peers.values()) {
    if (peer.socket !== except && peer.socket.readyState === peer.socket.OPEN) peer.socket.send(payload);
  }
};

export function roomsRouter({ rooms, config }) {
  const router = express.Router();
  const limit = rateLimit({ limit: config.roomsPerHour, name: 'rooms' });

  router.post('/', limit, (req, res) => {
    const room = rooms.create(req.body?.title);
    res.status(201).json(rooms.info(room));
  });

  router.get('/:code', (req, res) => {
    const room = rooms.get(req.params.code);
    if (!room) throw new HttpError(404, 'Room not found.');
    res.json(rooms.info(room));
  });

  router.limit = limit;
  return router;
}

export function attachCollab(server, { rooms, config }) {
  const wss = new WebSocketServer({ noServer: true, maxPayload: config.maxMessageMb * 1024 * 1024 });

  server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname !== '/ws') {
      socket.destroy();
      return;
    }
    const room = rooms.get(url.searchParams.get('room'));
    if (!room) {
      socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    if (room.peers.size >= config.maxPeersPerRoom) {
      socket.write('HTTP/1.1 409 Conflict\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => join(ws, room, text(url.searchParams.get('name'), 32) || 'Guest'));
  });

  function join(ws, room, name) {
    const peer = { id: room.nextPeer++, name, socket: ws, alive: true };
    room.peers.set(peer.id, peer);
    room.touched = Date.now();
    send(ws, {
      type: 'welcome',
      id: peer.id,
      room: room.code,
      title: room.title,
      peers: [...room.peers.values()].filter((p) => p !== peer).map((p) => ({ id: p.id, name: p.name })),
      snapshot: room.snapshot,
      ops: room.ops,
      seq: room.seq
    });
    broadcast(room, { type: 'join', peer: { id: peer.id, name: peer.name } }, ws);

    ws.on('pong', () => { peer.alive = true; });
    ws.on('message', (data, binary) => {
      if (binary) return;
      let message;
      try {
        message = JSON.parse(data.toString('utf8'));
      } catch {
        send(ws, { type: 'error', message: 'Messages must be JSON.' });
        return;
      }
      room.touched = Date.now();
      switch (message?.type) {
        case 'op': {
          if (message.op === undefined) return;
          room.seq += 1;
          const entry = { seq: room.seq, from: peer.id, op: message.op };
          room.ops.push(entry);
          if (room.ops.length > MAX_LOG) room.ops.splice(0, room.ops.length - MAX_LOG);
          broadcast(room, { type: 'op', ...entry }, ws);
          send(ws, { type: 'ack', seq: room.seq, ref: message.ref ?? null });
          break;
        }
        case 'snapshot': {
          if (message.snapshot === undefined || message.snapshot === null) return;
          room.seq += 1;
          room.snapshot = message.snapshot;
          room.ops = [];
          broadcast(room, { type: 'snapshot', seq: room.seq, from: peer.id, snapshot: message.snapshot }, ws);
          send(ws, { type: 'ack', seq: room.seq, ref: message.ref ?? null });
          break;
        }
        case 'cursor':
          broadcast(room, { type: 'cursor', from: peer.id, time: Number(message.time) || 0, difficulty: text(message.difficulty, 40) }, ws);
          break;
        case 'chat': {
          const body = text(message.text, 500);
          if (body) broadcast(room, { type: 'chat', from: peer.id, name: peer.name, text: body }, null);
          break;
        }
        case 'ping':
          send(ws, { type: 'pong' });
          break;
        default:
          send(ws, { type: 'error', message: 'Unknown message type.' });
      }
    });
    ws.on('close', () => {
      room.peers.delete(peer.id);
      room.touched = Date.now();
      broadcast(room, { type: 'leave', id: peer.id }, null);
    });
    ws.on('error', () => ws.terminate());
  }

  const heartbeat = setInterval(() => {
    for (const room of rooms.rooms.values()) {
      for (const peer of room.peers.values()) {
        if (!peer.alive) {
          peer.socket.terminate();
          continue;
        }
        peer.alive = false;
        peer.socket.ping();
      }
    }
  }, 30 * 1000);
  heartbeat.unref();
  wss.on('close', () => clearInterval(heartbeat));
  return wss;
}
