import crypto from 'node:crypto';
import express from 'express';
import { HttpError, asyncRoute } from '../util.js';
import { rateLimit } from '../limits.js';

const TTL_MS = 10 * 60 * 1000;
const API = 'https://discord.com/api/v10';

const escape = (value) => String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const page = (res, status, title, message) => res.status(status).type('html').send(`<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${escape(title)}</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#1b1b26;color:#fff;font:16px system-ui,sans-serif;text-align:center}main{padding:24px;max-width:420px}h1{color:#ea71fd;font-size:22px}</style>
</head><body><main><h1>${escape(title)}</h1><p>${escape(message)}</p></main></body></html>`);

export const avatarUrl = (user) => {
  if (user.avatar) return `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png?size=128`;
  let index = 0;
  try {
    index = Number((BigInt(user.id) >> 22n) % 6n);
  } catch {
    index = 0;
  }
  return `https://cdn.discordapp.com/embed/avatars/${index}.png`;
};

export function discordRouter({ config, fetchImpl = fetch }) {
  const router = express.Router();
  const pending = new Map();
  const limit = rateLimit({ limit: config.discordLoginsPerHour, name: 'Discord logins' });
  const ready = () => Boolean(config.discordClientId && config.discordClientSecret);

  const sweep = () => {
    const now = Date.now();
    for (const [id, entry] of pending) if (now - entry.created > TTL_MS) pending.delete(id);
  };

  const redirectFor = (req) => `${config.publicUrl || `${req.protocol}://${req.get('host')}`}/api/discord/callback`;

  router.get('/', (req, res) => res.json({ enabled: ready(), clientId: config.discordClientId }));

  router.post('/login', limit, (req, res) => {
    if (!ready()) throw new HttpError(503, 'Discord login is not set up on this server.');
    sweep();
    const id = crypto.randomBytes(24).toString('base64url');
    const state = crypto.randomBytes(24).toString('base64url');
    const redirect = redirectFor(req);
    pending.set(id, { state, redirect, created: Date.now(), user: null, error: null });
    const params = new URLSearchParams({
      client_id: config.discordClientId,
      response_type: 'code',
      redirect_uri: redirect,
      scope: 'identify',
      state,
      prompt: 'consent'
    });
    res.status(201).json({ id, url: `https://discord.com/oauth2/authorize?${params}`, expiresIn: TTL_MS / 1000 });
  });

  router.get('/callback', asyncRoute(async (req, res) => {
    sweep();
    const state = typeof req.query.state === 'string' ? req.query.state : '';
    const entry = state ? [...pending.values()].find((e) => e.state === state) : null;
    if (!entry || entry.user || entry.error) return page(res, 400, 'Link expired', 'This login link expired or was already used. Start again from V-Converter.');
    if (req.query.error || typeof req.query.code !== 'string') {
      entry.error = 'The Discord login was cancelled.';
      return page(res, 200, 'Login cancelled', 'You can close this tab and go back to V-Converter.');
    }
    try {
      const auth = Buffer.from(`${config.discordClientId}:${config.discordClientSecret}`).toString('base64');
      const tokenResponse = await fetchImpl(`${API}/oauth2/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Authorization: `Basic ${auth}` },
        body: new URLSearchParams({ grant_type: 'authorization_code', code: req.query.code, redirect_uri: entry.redirect }).toString()
      });
      if (!tokenResponse.ok) throw new Error(`token ${tokenResponse.status}`);
      const token = await tokenResponse.json();
      const userResponse = await fetchImpl(`${API}/users/@me`, { headers: { Authorization: `Bearer ${token.access_token}` } });
      if (!userResponse.ok) throw new Error(`user ${userResponse.status}`);
      const user = await userResponse.json();
      entry.user = {
        id: String(user.id),
        username: String(user.username || ''),
        displayName: String(user.global_name || user.username || ''),
        avatarUrl: avatarUrl(user)
      };
      fetchImpl(`${API}/oauth2/token/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Authorization: `Basic ${auth}` },
        body: new URLSearchParams({ token: token.access_token, token_type_hint: 'access_token' }).toString()
      }).catch(() => {});
      return page(res, 200, `Hi, ${entry.user.displayName}!`, 'You are logged in. Close this tab and go back to V-Converter.');
    } catch (error) {
      console.error('Discord login failed:', error.message);
      entry.error = 'Discord did not accept the login. Try again.';
      return page(res, 502, 'Login failed', 'Discord did not accept the login. Go back to V-Converter and try again.');
    }
  }));

  router.get('/login/:id', (req, res) => {
    sweep();
    const entry = pending.get(req.params.id);
    if (!entry) throw new HttpError(404, 'This login expired. Start again.');
    if (entry.error) {
      pending.delete(req.params.id);
      throw new HttpError(400, entry.error);
    }
    if (!entry.user) return res.json({ status: 'pending' });
    pending.delete(req.params.id);
    res.json({ status: 'done', user: entry.user });
  });

  router.limit = limit;
  return router;
}
