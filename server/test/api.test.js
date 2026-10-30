import test from 'node:test';
import assert from 'node:assert/strict';
import { boot, zip, form } from './helpers.js';

const admin = { Authorization: 'Bearer secret-admin' };

test('mods can be uploaded, listed, searched, downloaded and deleted', async () => {
  const server = await boot();
  try {
    const created = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Dude Mod', description: 'A week', author: 'Breno', engine: 'psych', tags: 'Week, Dude' }, zip('one')) });
    assert.equal(created.status, 201);
    const mod = await created.json();
    assert.equal(mod.title, 'Dude Mod');
    assert.equal(mod.engine, 'psych');
    assert.deepEqual(mod.tags, ['week', 'dude']);
    assert.ok(mod.deleteToken);

    const duplicate = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Again' }, zip('one')) });
    assert.equal(duplicate.status, 409);
    assert.equal((await duplicate.json()).id, mod.id);

    await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Other', engine: 'vslice' }, zip('two')) });
    const all = await (await fetch(`${server.base}/api/mods`)).json();
    assert.equal(all.total, 2);
    const searched = await (await fetch(`${server.base}/api/mods?q=dude`)).json();
    assert.deepEqual(searched.mods.map((m) => m.id), [mod.id]);
    const byEngine = await (await fetch(`${server.base}/api/mods?engine=vslice`)).json();
    assert.equal(byEngine.total, 1);

    const download = await fetch(`${server.base}/api/mods/${mod.id}/download`);
    assert.equal(download.status, 200);
    assert.deepEqual(Buffer.from(await download.arrayBuffer()), zip('one'));
    const after = await (await fetch(`${server.base}/api/mods/${mod.id}`)).json();
    assert.equal(after.downloads, 1);

    const wrong = await fetch(`${server.base}/api/mods/${mod.id}`, { method: 'DELETE', headers: { 'X-Delete-Token': 'nope' } });
    assert.equal(wrong.status, 403);
    const removed = await fetch(`${server.base}/api/mods/${mod.id}`, { method: 'DELETE', headers: { 'X-Delete-Token': mod.deleteToken } });
    assert.equal(removed.status, 204);
    assert.equal((await fetch(`${server.base}/api/mods/${mod.id}`)).status, 404);
  } finally {
    await server.close();
  }
});

test('uploads are validated and rate limited', async () => {
  const server = await boot({ UPLOADS_PER_HOUR: '3', MAX_UPLOAD_MB: '1' });
  try {
    const notZip = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Bad' }, Buffer.from('plain text')) });
    assert.equal(notZip.status, 400);
    const noTitle = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({}, zip('x')) });
    assert.equal(noTitle.status, 400);
    const big = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Big' }, Buffer.concat([zip('y'), Buffer.alloc(1024 * 1024 + 10)])) });
    assert.equal(big.status, 413);
    const limited = await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Late' }, zip('z')) });
    assert.equal(limited.status, 429);
  } finally {
    await server.close();
  }
});

test('admins can hide mods', async () => {
  const server = await boot();
  try {
    const mod = await (await fetch(`${server.base}/api/mods`, { method: 'POST', body: form({ title: 'Hidden' }, zip('h')) })).json();
    const denied = await fetch(`${server.base}/api/mods/${mod.id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ hidden: true }) });
    assert.equal(denied.status, 403);
    const hidden = await fetch(`${server.base}/api/mods/${mod.id}`, { method: 'PATCH', headers: { ...admin, 'Content-Type': 'application/json' }, body: JSON.stringify({ hidden: true }) });
    assert.equal(hidden.status, 200);
    assert.equal((await (await fetch(`${server.base}/api/mods`)).json()).total, 0);
    assert.equal((await fetch(`${server.base}/api/mods/${mod.id}/download`)).status, 404);
  } finally {
    await server.close();
  }
});

test('version comes from the latest GitHub release', async () => {
  const release = {
    tag_name: 'v0.0.2', name: 'V-Converter 0.0.2', body: 'Changes', html_url: 'https://github.com/x/releases/tag/v0.0.2', published_at: '2026-10-30T00:00:00Z',
    assets: [
      { name: 'V Converter-Android.apk', browser_download_url: 'https://dl/android.apk', size: 1 },
      { name: 'V Converter-Windows.exe', browser_download_url: 'https://dl/windows.exe', size: 1 }
    ]
  };
  let calls = 0;
  const latest = (await import('../src/routes/updates.js')).createReleaseSource({ releaseCacheMinutes: 10, githubRepo: 'x/y', githubToken: '', latestVersion: '' }, async () => {
    calls += 1;
    return { ok: true, json: async () => release };
  });
  const server = await boot({}, { latest });
  try {
    const android = await (await fetch(`${server.base}/api/version?platform=android&current=0.0.1`)).json();
    assert.equal(android.latest, '0.0.2');
    assert.equal(android.updateAvailable, true);
    assert.equal(android.url, 'https://dl/android.apk');
    const windows = await (await fetch(`${server.base}/api/version?platform=windows&current=0.0.2`)).json();
    assert.equal(windows.updateAvailable, false);
    assert.equal(windows.url, 'https://dl/windows.exe');
    const linux = await (await fetch(`${server.base}/api/version?platform=linux&current=0.0.1`)).json();
    assert.equal(linux.url, release.html_url);
    assert.equal(calls, 1);
  } finally {
    await server.close();
  }
});

test('announcements are managed by admins and filtered by platform', async () => {
  const server = await boot();
  try {
    const json = { 'Content-Type': 'application/json' };
    assert.equal((await fetch(`${server.base}/api/announcements`, { method: 'POST', headers: json, body: JSON.stringify({ title: 'Hi' }) })).status, 403);
    const one = await (await fetch(`${server.base}/api/announcements`, { method: 'POST', headers: { ...admin, ...json }, body: JSON.stringify({ title: 'Everyone', body: 'Hello' }) })).json();
    await fetch(`${server.base}/api/announcements`, { method: 'POST', headers: { ...admin, ...json }, body: JSON.stringify({ title: 'Android only', platforms: ['android'] }) });
    await fetch(`${server.base}/api/announcements`, { method: 'POST', headers: { ...admin, ...json }, body: JSON.stringify({ title: 'Old', expiresAt: '2000-01-01' }) });
    const android = await (await fetch(`${server.base}/api/announcements?platform=android`)).json();
    assert.deepEqual(android.announcements.map((a) => a.title).sort(), ['Android only', 'Everyone']);
    const windows = await (await fetch(`${server.base}/api/announcements?platform=windows`)).json();
    assert.deepEqual(windows.announcements.map((a) => a.title), ['Everyone']);
    assert.equal((await fetch(`${server.base}/api/announcements/${one.id}`, { method: 'DELETE', headers: admin })).status, 204);
  } finally {
    await server.close();
  }
});

test('error reports are stored and only admins can read them', async () => {
  const server = await boot({ REPORTS_PER_HOUR: '2' });
  try {
    const json = { 'Content-Type': 'application/json' };
    const sent = await fetch(`${server.base}/api/reports`, { method: 'POST', headers: json, body: JSON.stringify({ kind: 'conversion', platform: 'Android', appVersion: '0.0.1', message: 'Chart failed', stack: 'at x', context: { tab: 'Charts' } }) });
    assert.equal(sent.status, 201);
    assert.equal((await fetch(`${server.base}/api/reports`, { method: 'POST', headers: json, body: JSON.stringify({}) })).status, 400);
    assert.equal((await fetch(`${server.base}/api/reports`, { method: 'POST', headers: json, body: JSON.stringify({ message: 'x' }) })).status, 429);
    assert.equal((await fetch(`${server.base}/api/reports`)).status, 403);
    const list = await (await fetch(`${server.base}/api/reports`, { headers: admin })).json();
    assert.equal(list.reports.length, 1);
    assert.equal(list.reports[0].platform, 'android');
    assert.equal(JSON.parse(list.reports[0].context).tab, 'Charts');
  } finally {
    await server.close();
  }
});
