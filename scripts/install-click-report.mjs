#!/usr/bin/env node
/**
 * 서버에서 로그 파일을 직접 집계하는 CLI (토큰 불필요).
 *   node scripts/install-click-report.mjs [--days 7] [--recent 20]
 * 로그 위치: INSTALL_CLICK_LOG_DIR (기본 ./data/install-clicks)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.resolve(process.env.INSTALL_CLICK_LOG_DIR || path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'data', 'install-clicks'));
const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i > 0 ? Number(process.argv[i + 1]) : d; };
const days = arg('days', 7), recent = arg('recent', 0);
if (!fs.existsSync(dir)) { console.log(`로그 없음: ${dir}`); process.exit(0); }
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.jsonl')).sort().slice(-days);
const rows = files.flatMap((f) => fs.readFileSync(path.join(dir, f), 'utf8').split('\n').filter(Boolean).flatMap((l) => { try { return [JSON.parse(l)]; } catch { return []; } }));
const count = (fn) => rows.reduce((a, r) => { const k = fn(r); a[k] = (a[k] || 0) + 1; return a; }, {});
console.log(`기간: 최근 ${files.length}일 파일 (${files[0] ?? '-'} ~ ${files.at(-1) ?? '-'}) · 총 ${rows.length}건 · fbclid 보유 ${rows.filter((r) => r.fbclid).length}건`);
console.log('스토어별   ', count((r) => r.store));
console.log('버튼 위치별', count((r) => r.placement || 'unknown'));
console.log('캠페인별   ', count((r) => r.utm_campaign || (r.fbclid ? '(fbclid only)' : '(organic/none)')));
console.log('일자별     ', count((r) => (r.received_at_kst || '').slice(0, 10)));
if (recent) for (const r of rows.slice(-recent).reverse()) console.log(r.received_at_kst, r.store, r.placement, r.utm_campaign ?? '-', r.fbclid ? 'fbclid' : '-', (r.ua || '').slice(0, 60));
