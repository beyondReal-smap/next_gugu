#!/usr/bin/env node
/**
 * 설치 클릭 로그 서버 (의존성 없음, Node 18+).
 *
 * 랜딩은 정적 export(dist)라 자체 API 라우트가 없다 → 이 작은 서버를 pm2로 따로 띄우고
 * 리버스 프록시(Cloudflare Tunnel ingress / nginx)에서 gugu.smap.site/api/install-click* 를 이 포트로 보낸다.
 *
 *   POST /api/install-click          설치 CTA 클릭 1건 기록 (sendBeacon, text/plain JSON)
 *   GET  /api/install-click/summary  집계 JSON (Authorization: Bearer $INSTALL_CLICK_ADMIN_TOKEN)
 *   GET  /api/install-click/recent   최근 N건 (동일 인증, ?limit=50)
 *   GET  /api/install-click/health   상태 확인
 *
 * 로그: $INSTALL_CLICK_LOG_DIR/install-clicks-YYYY-MM-DD.jsonl (기본 ./data/install-clicks, git 제외)
 * 환경변수:
 *   PORT                      기본 4318
 *   HOST                      기본 127.0.0.1
 *   INSTALL_CLICK_LOG_DIR     로그 디렉터리
 *   INSTALL_CLICK_ADMIN_TOKEN 조회용 토큰 (미설정이면 summary/recent 비활성 → 401)
 *   INSTALL_CLICK_ORIGINS     허용 Origin(쉼표) 기본 https://gugu.smap.site
 *
 * 개인정보: IP는 원문 저장하지 않고 일 단위 솔트 해시 앞 12자만 저장. 쿠키(fbc/fbp)는 광고 매칭용이라 저장하되
 * 조회 API 응답에서는 제외한다.
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 4318);
const HOST = process.env.HOST || '127.0.0.1';
const LOG_DIR = path.resolve(process.env.INSTALL_CLICK_LOG_DIR || path.join(__dirname, '..', 'data', 'install-clicks'));
const ADMIN_TOKEN = process.env.INSTALL_CLICK_ADMIN_TOKEN || '';
const ORIGINS = (process.env.INSTALL_CLICK_ORIGINS || 'https://gugu.smap.site')
  .split(',').map((s) => s.trim()).filter(Boolean);
const MAX_BODY = 8 * 1024;
const STORES = new Set(['ios', 'android']);
const STR_FIELDS = ['click_id', 'placement', 'page', 'href', 'fbclid', 'utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term', 'fbc', 'fbp'];

fs.mkdirSync(LOG_DIR, { recursive: true });

const s = (v, n = 300) => (typeof v === 'string' ? v.slice(0, n) : undefined);
const dayKey = (d = new Date()) => new Date(d.getTime() + 9 * 3600e3).toISOString().slice(0, 10); // KST 기준 일자
const logFile = (day) => path.join(LOG_DIR, `install-clicks-${day}.jsonl`);

function hashIp(ip, day) {
  return crypto.createHash('sha256').update(`${day}|${ip}`).digest('hex').slice(0, 12);
}

function cors(req, res) {
  const origin = req.headers.origin;
  if (origin && ORIGINS.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    res.setHeader('Vary', 'Origin');
  }
  res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
}

function send(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body === undefined ? '' : JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > MAX_BODY) { reject(new Error('too_large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function authorized(req) {
  if (!ADMIN_TOKEN) return false;
  const h = req.headers.authorization || '';
  const given = h.startsWith('Bearer ') ? h.slice(7) : '';
  const a = Buffer.from(given), b = Buffer.from(ADMIN_TOKEN);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function* readRecords(days) {
  const files = fs.readdirSync(LOG_DIR).filter((f) => /^install-clicks-\d{4}-\d{2}-\d{2}\.jsonl$/.test(f)).sort();
  const picked = days ? files.slice(-days) : files;
  for (const f of picked) {
    for (const line of fs.readFileSync(path.join(LOG_DIR, f), 'utf8').split('\n')) {
      if (!line) continue;
      try { yield JSON.parse(line); } catch { /* 깨진 줄 무시 */ }
    }
  }
}

const inc = (o, k) => { o[k] = (o[k] || 0) + 1; };
function summarize(days) {
  const out = { total: 0, by_store: {}, by_placement: {}, by_campaign: {}, by_day: {}, with_fbclid: 0, unique_click_ids: 0 };
  const ids = new Set();
  for (const r of readRecords(days)) {
    out.total++;
    inc(out.by_store, r.store);
    inc(out.by_placement, r.placement || 'unknown');
    inc(out.by_campaign, r.utm_campaign || (r.fbclid ? '(fbclid only)' : '(organic/none)'));
    inc(out.by_day, (r.received_at_kst || '').slice(0, 10));
    if (r.fbclid) out.with_fbclid++;
    if (r.click_id) ids.add(r.click_id);
  }
  out.unique_click_ids = ids.size;
  return out;
}

function recent(limit) {
  const all = [...readRecords(7)];
  return all.slice(-limit).reverse().map(({ fbc, fbp, ...rest }) => rest);
}

const server = http.createServer(async (req, res) => {
  cors(req, res);
  const url = new URL(req.url || '/', 'http://x');
  const p = url.pathname.replace(/\/+$/, '');
  if (req.method === 'OPTIONS') return send(res, 204);

  if (req.method === 'GET' && p === '/api/install-click/health') return send(res, 200, { ok: true });

  if (req.method === 'GET' && (p === '/api/install-click/summary' || p === '/api/install-click/recent')) {
    if (!authorized(req)) return send(res, 401, { error: 'unauthorized' });
    if (p.endsWith('summary')) return send(res, 200, summarize(Number(url.searchParams.get('days')) || 0));
    return send(res, 200, recent(Math.min(Number(url.searchParams.get('limit')) || 50, 500)));
  }

  if (req.method === 'POST' && p === '/api/install-click') {
    try {
      const raw = await readBody(req);
      const b = JSON.parse(raw || '{}');
      if (!b || typeof b !== 'object' || !STORES.has(b.store)) return send(res, 400, { error: 'invalid' });
      const now = new Date();
      const day = dayKey(now);
      const ip = (req.headers['cf-connecting-ip'] || req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').toString().split(',')[0].trim();
      const rec = { received_at: now.toISOString(), received_at_kst: new Date(now.getTime() + 9 * 3600e3).toISOString().replace('Z', '+09:00'), store: b.store };
      for (const k of STR_FIELDS) { const v = s(b[k], k === 'href' ? 600 : 200); if (v) rec[k] = v; }
      rec.ua = s(req.headers['user-agent'], 300);
      rec.referer = s(req.headers.referer, 300);
      rec.ip_hash = ip ? hashIp(ip, day) : undefined;
      rec.country = s(req.headers['cf-ipcountry'], 4);
      fs.appendFile(logFile(day), JSON.stringify(rec) + '\n', (err) => { if (err) console.error('log write failed', err.message); });
      return send(res, 204);
    } catch (e) {
      return send(res, e && e.message === 'too_large' ? 413 : 400, { error: 'bad_request' });
    }
  }

  return send(res, 404, { error: 'not_found' });
});

server.listen(PORT, HOST, () => console.log(`install-click server on http://${HOST}:${PORT} → ${LOG_DIR}`));
