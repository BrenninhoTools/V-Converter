import test from 'node:test';
import assert from 'node:assert/strict';
import WebSocket from 'ws';
import { boot } from './helpers.js';

const connect = (base, room, name) => new Promise((resolve, reject) => {
  const ws = new WebSocket(`${base.replace('http', 'ws')}/ws?room=${room}&name=${encodeURIComponent(name)}`);
  const inbox = [];
  const waiters = [];
  ws.on('message', (data) => {
    const message = JSON.parse(data.toString());
    const index = waiters.findIndex((w) => w.type === message.type);
    if (index >= 0) waiters.splice(index, 1)[0].resolve(message);
    else inbox.push(message);
  });
  ws.next = (type) => {
    const index = inbox.findIndex((m) => m.type === type);
    if (index >= 0) return Promise.resolve(inbox.splice(index, 1)[0]);
    return new Promise((done) => waiters.push({ type, resolve: done }));
  };
  ws.json = (message) => ws.send(JSON.stringify(message));
  ws.on('open', () => resolve(ws));
  ws.on('error', reject);
});

test('rooms share snapshots, ops, cursors and presence', async () => {
  const server = await boot();
  try {
    const room = await (await fetch(`${server.base}/api/rooms`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: 'Bopeebo' }) })).json();
    assert.match(room.code, /^[A-Z0-9]{6}$/);
    const host = await connect(server.base, room.code, 'Host');
    const welcome = await host.next('welcome');
    assert.equal(welcome.snapshot, null);
    host.json({ type: 'snapshot', snapshot: { chart: 1 } });
    await host.next('ack');
    host.json({ type: 'op', op: { add: 1 } });
    await host.next('ack');

    const guest = await connect(server.base, room.code.toLowerCase(), 'Guest');
    const joined = await guest.next('welcome');
    assert.deepEqual(joined.snapshot, { chart: 1 });
    assert.deepEqual(joined.ops.map((o) => o.op), [{ add: 1 }]);
    assert.deepEqual(joined.peers.map((p) => p.name), ['Host']);
    assert.equal((await host.next('join')).peer.name, 'Guest');

    guest.json({ type: 'op', op: { add: 2 } });
    const relayed = await host.next('op');
    assert.deepEqual(relayed.op, { add: 2 });
    assert.equal(relayed.from, joined.id);
    host.json({ type: 'cursor', time: 1500, difficulty: 'hard' });
    assert.equal((await guest.next('cursor')).time, 1500);

    const info = await (await fetch(`${server.base}/api/rooms/${room.code}`)).json();
    assert.equal(info.peers.length, 2);
    guest.close();
    assert.equal((await host.next('leave')).id, joined.id);
    host.close();
  } finally {
    await server.close();
  }
});

test('unknown rooms are refused', async () => {
  const server = await boot();
  try {
    await assert.rejects(connect(server.base, 'NOPE00', 'x'));
  } finally {
    await server.close();
  }
});

test('public rooms are listed by kind and share files', async () => {
  const server = await boot();
  try {
    const json = { 'Content-Type': 'application/json' };
    const open = await (await fetch(`${server.base}/api/rooms`, { method: 'POST', headers: json, body: JSON.stringify({ title: 'Open stage', kind: 'stage', public: true, host: 'Breno' }) })).json();
    assert.equal(open.kind, 'stage');
    assert.equal(open.public, true);
    await fetch(`${server.base}/api/rooms`, { method: 'POST', headers: json, body: JSON.stringify({ title: 'Secret', kind: 'stage' }) });
    await fetch(`${server.base}/api/rooms`, { method: 'POST', headers: json, body: JSON.stringify({ title: 'Chart', kind: 'chart', public: true }) });
    const stages = await (await fetch(`${server.base}/api/rooms?kind=stage`)).json();
    assert.deepEqual(stages.rooms.map((r) => r.title), ['Open stage']);
    const all = await (await fetch(`${server.base}/api/rooms`)).json();
    assert.equal(all.rooms.length, 2);

    const peer = await connect(server.base, open.code, 'Peer');
    const welcome = await peer.next('welcome');
    assert.equal(welcome.kind, 'stage');
    assert.deepEqual(welcome.assets, []);
    const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 1, 2, 3]);
    const put = await fetch(`${server.base}/api/rooms/${open.code}/assets/stages/test/bg.png`, { method: 'PUT', headers: { 'Content-Type': 'application/octet-stream' }, body: png });
    assert.equal(put.status, 201);
    const note = await peer.next('asset');
    assert.equal(note.name, 'stages/test/bg.png');
    const back = Buffer.from(await (await fetch(`${server.base}/api/rooms/${open.code}/assets/stages/test/bg.png`)).arrayBuffer());
    assert.deepEqual(back, png);
    const list = await (await fetch(`${server.base}/api/rooms/${open.code}/assets`)).json();
    assert.deepEqual(list.assets, [{ name: 'stages/test/bg.png', size: png.length }]);
    assert.equal((await fetch(`${server.base}/api/rooms/${open.code}/assets/../x`, { method: 'PUT', body: png })).status, 404);
    const late = await connect(server.base, open.code, 'Late');
    assert.deepEqual((await late.next('welcome')).assets, ['stages/test/bg.png']);
    late.close();
    peer.close();
  } finally {
    await server.close();
  }
});
