import test from 'node:test';
import assert from 'node:assert/strict';
import { boot } from './helpers.js';
import { avatarUrl } from '../src/routes/discord.js';

const fakeDiscord = (calls) => async (url, init = {}) => {
  calls.push({ url, init });
  if (url.endsWith('/oauth2/token')) {
    const body = new URLSearchParams(init.body);
    if (body.get('code') !== 'good-code') return new Response('{}', { status: 400 });
    return Response.json({ access_token: 'token-1', token_type: 'Bearer' });
  }
  if (url.endsWith('/users/@me')) return Response.json({ id: '80351110224678912', username: 'brenninho', global_name: 'Brenninho', avatar: 'abc123' });
  return new Response('', { status: 200 });
};

test('discord login is off without a client secret', async () => {
  const server = await boot();
  try {
    const info = await (await fetch(`${server.base}/api/discord`)).json();
    assert.equal(info.enabled, false);
    assert.equal(info.clientId, '1536017631579078738');
    const login = await fetch(`${server.base}/api/discord/login`, { method: 'POST' });
    assert.equal(login.status, 503);
  } finally {
    await server.close();
  }
});

test('discord login goes through the server and hands the profile to the app once', async () => {
  const calls = [];
  const server = await boot({ DISCORD_CLIENT_SECRET: 'shh', PUBLIC_URL: 'https://vc.example.com' }, { discordFetch: fakeDiscord(calls) });
  try {
    const login = await (await fetch(`${server.base}/api/discord/login`, { method: 'POST' })).json();
    const url = new URL(login.url);
    assert.equal(url.searchParams.get('client_id'), '1536017631579078738');
    assert.equal(url.searchParams.get('redirect_uri'), 'https://vc.example.com/api/discord/callback');
    assert.equal(url.searchParams.get('scope'), 'identify');
    const state = url.searchParams.get('state');
    assert.notEqual(state, login.id);

    assert.deepEqual(await (await fetch(`${server.base}/api/discord/login/${login.id}`)).json(), { status: 'pending' });

    const bad = await fetch(`${server.base}/api/discord/callback?state=nope&code=good-code`);
    assert.equal(bad.status, 400);

    const callback = await fetch(`${server.base}/api/discord/callback?state=${state}&code=good-code`);
    assert.equal(callback.status, 200);
    assert.match(await callback.text(), /Brenninho/);
    const token = calls.find((c) => c.url.endsWith('/oauth2/token'));
    assert.equal(token.init.headers.Authorization, `Basic ${Buffer.from('1536017631579078738:shh').toString('base64')}`);
    assert.equal(new URLSearchParams(token.init.body).get('redirect_uri'), 'https://vc.example.com/api/discord/callback');

    const done = await (await fetch(`${server.base}/api/discord/login/${login.id}`)).json();
    assert.equal(done.status, 'done');
    assert.deepEqual(done.user, {
      id: '80351110224678912',
      username: 'brenninho',
      displayName: 'Brenninho',
      avatarUrl: 'https://cdn.discordapp.com/avatars/80351110224678912/abc123.png?size=128'
    });
    assert.equal((await fetch(`${server.base}/api/discord/login/${login.id}`)).status, 404);
    assert.equal((await fetch(`${server.base}/api/discord/callback?state=${state}&code=good-code`)).status, 400);
  } finally {
    await server.close();
  }
});

test('a cancelled or rejected discord login is reported to the app', async () => {
  const server = await boot({ DISCORD_CLIENT_SECRET: 'shh' }, { discordFetch: fakeDiscord([]) });
  try {
    for (const query of ['error=access_denied', 'code=bad-code']) {
      const login = await (await fetch(`${server.base}/api/discord/login`, { method: 'POST' })).json();
      const state = new URL(login.url).searchParams.get('state');
      assert.match(new URL(login.url).searchParams.get('redirect_uri'), /^http:\/\/127\.0\.0\.1:\d+\/api\/discord\/callback$/);
      await fetch(`${server.base}/api/discord/callback?state=${state}&${query}`);
      const status = await fetch(`${server.base}/api/discord/login/${login.id}`);
      assert.equal(status.status, 400);
      assert.ok((await status.json()).error);
    }
  } finally {
    await server.close();
  }
});

test('default avatars follow the user id', () => {
  assert.equal(avatarUrl({ id: '80351110224678912', avatar: null }), `https://cdn.discordapp.com/embed/avatars/${Number((80351110224678912n >> 22n) % 6n)}.png`);
  assert.equal(avatarUrl({ id: '1', avatar: 'a_anim' }), 'https://cdn.discordapp.com/avatars/1/a_anim.png?size=128');
});
