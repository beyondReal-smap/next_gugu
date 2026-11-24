#!/usr/bin/env node
/**
 * AI 검색(AEO/GEO)용 정적 산출물 생성기.
 *
 * 왜 정적 HTML인가:
 *   앱 라우트(/, /learn, /profile)는 전부 ClientLayout → AppShell로 감싸인 클라이언트 컴포넌트라
 *   정적 export 결과물에 크롤러가 읽을 본문이 남지 않는다. AppShell을 우회하려면 root layout을
 *   손대야 해서 앱 구조 전체가 흔들린다. 그래서 정책 페이지(privacy/terms.html)와 같은 방식으로
 *   public/ 아래에 완결된 HTML을 생성한다.
 *
 * 입력: ../../content/*.json (web·api 공용 단일 소스)
 * 출력: public/{robots.txt, sitemap.xml, llms.txt, llms-full.txt, ai/**, guide/**}
 *
 * 실행: yarn ai:build (build 전에 자동 실행됨)
 */
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = join(HERE, '..');
const CONTENT = join(WEB, '..', 'content');
const PUBLIC = join(WEB, 'public');

const read = (name) => JSON.parse(readFileSync(join(CONTENT, name), 'utf8'));
const site = read('site.json');
const tablesDoc = read('tables.json');
const modesDoc = read('modes.json');
const faqDoc = read('faq.json');
const pathDoc = read('learning-path.json');
const factsDoc = read('facts.json');

const ORIGIN = site.canonicalOrigin;
const TABLES = tablesDoc.tables;
const UPDATED = site.updated;

const factsFor = (table) => factsDoc.facts.filter((f) => f.a === table);

// ─────────────────────────────────────────────────────────── 공통 유틸

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function write(relPath, body) {
  const out = join(PUBLIC, relPath);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, body, 'utf8');
  return relPath;
}

/** JSON-LD는 </script> 조기 종료를 막기 위해 슬래시를 이스케이프한다 */
function jsonLd(obj) {
  const json = JSON.stringify(obj, null, 2).replace(/</g, '\\u003c');
  return `<script type="application/ld+json">\n${json}\n</script>`;
}

function breadcrumb(trail) {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((t, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: t.name,
      item: `${ORIGIN}${t.path}`,
    })),
  };
}

const ORG = {
  '@type': 'Organization',
  name: site.publisher.name,
  url: site.publisher.url,
};

const APP_ENTITY = {
  '@type': 'SoftwareApplication',
  name: site.name,
  alternateName: site.alternateNames,
  url: ORIGIN,
  applicationCategory: 'EducationalApplication',
  applicationSubCategory: '수학 학습',
  operatingSystem: 'Web, iOS, Android',
  inLanguage: 'ko',
  description: site.summary,
  featureList: site.features.map((f) => f.name),
  offers: { '@type': 'Offer', price: '0', priceCurrency: site.pricing.currency },
  publisher: ORG,
};

/**
 * 페이지 HTML 셸.
 * 크롤러가 첫 화면에서 바로 답을 얻도록 h1 → 요약 문단 → 본문 순서를 강제한다.
 */
function page({ slug, title, description, keywords, heading, lead, trail, blocks, structured }) {
  const url = `${ORIGIN}${slug}`;
  const graph = [
    { '@type': 'WebPage', '@id': `${url}#page`, url, name: title, description, inLanguage: 'ko', isPartOf: { '@id': `${ORIGIN}/#website` }, dateModified: UPDATED, publisher: ORG },
    breadcrumb(trail),
    ...(structured ?? []),
  ];

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}" />
<meta name="keywords" content="${esc(keywords.join(', '))}" />
<link rel="canonical" href="${url}" />
<meta name="robots" content="index, follow, max-snippet:-1, max-image-preview:large, max-video-preview:-1" />
<meta name="author" content="${esc(site.name)}" />
<meta property="og:type" content="article" />
<meta property="og:site_name" content="${esc(site.name)}" />
<meta property="og:locale" content="${esc(site.locale)}" />
<meta property="og:title" content="${esc(title)}" />
<meta property="og:description" content="${esc(description)}" />
<meta property="og:url" content="${url}" />
<meta property="og:image" content="${ORIGIN}/icons/icon-512x512.png" />
<meta name="twitter:card" content="summary" />
<meta name="twitter:title" content="${esc(title)}" />
<meta name="twitter:description" content="${esc(description)}" />
<meta name="twitter:image" content="${ORIGIN}/icons/icon-512x512.png" />
<link rel="icon" href="/icon-gugu.png" />
<link rel="alternate" type="application/json" href="${ORIGIN}/ai/dataset.json" title="AI-ready 데이터셋" />
<link rel="alternate" type="application/rss+xml" href="${ORIGIN}/rss.xml" title="${esc(site.name)} 구구단 가이드" />
<link rel="stylesheet" href="/guide/guide.css" />
${jsonLd({ '@context': 'https://schema.org', '@graph': graph })}
</head>
<body>
<a class="skip" href="#main">본문 바로가기</a>
<header class="topbar">
  <a class="brand" href="/"><img src="/icon-gugu.png" alt="" width="28" height="28" /><span>${esc(site.name)}</span></a>
  <nav aria-label="주요 메뉴">
    <a href="/guide">가이드</a>
    <a href="/guide/modes">게임 모드</a>
    <a href="/guide/faq">자주 묻는 질문</a>
    <a class="cta" href="/">앱 시작하기</a>
  </nav>
</header>
<main id="main">
<nav class="crumbs" aria-label="탐색 경로">${trail
    .map((t, i) => (i === trail.length - 1 ? `<span aria-current="page">${esc(t.name)}</span>` : `<a href="${t.path}">${esc(t.name)}</a>`))
    .join('<span class="sep">›</span>')}</nav>
<article>
<h1>${esc(heading)}</h1>
<p class="lead">${lead}</p>
${blocks.join('\n')}
</article>
</main>
<footer class="foot">
  <p><strong>${esc(site.name)}</strong> — ${esc(site.tagline)}. <a href="/">앱 설치하기</a></p>
  <p class="muted">최종 업데이트 ${esc(UPDATED)} · 출처 표기: ${esc(site.license.attribution)}</p>
  <p class="muted"><a href="/privacy">개인정보처리방침</a> · <a href="/terms">이용약관</a> · <a href="/llms.txt">llms.txt</a> · <a href="/ai/dataset.json">AI 데이터셋(JSON)</a> · <a href="/guide">가이드 전체</a></p>
</footer>
</body>
</html>
`;
}

// ─────────────────────────────────────────────────────────── 스타일

const CSS = `:root{--bg:#f8fafc;--surface:#fff;--surface-2:#f1f5f9;--text:#0f172a;--muted:#64748b;--border:#e2e8f0;--accent:#4f46e5;--accent-soft:#eef2ff;--warn:#b45309;--warn-soft:#fffbeb}
@media (prefers-color-scheme:dark){:root{--bg:#080b16;--surface:#111727;--surface-2:#1e273c;--text:#f1f5f9;--muted:#94a3b8;--border:#273149;--accent:#818cf8;--accent-soft:#1a2138;--warn:#fbbf24;--warn-soft:#241d0e}}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%;scroll-behavior:smooth}
body{margin:0;background:var(--bg);color:var(--text);line-height:1.75;-webkit-font-smoothing:antialiased;
font-family:"SUITE",system-ui,-apple-system,"Segoe UI",Roboto,"Apple SD Gothic Neo","Malgun Gothic",sans-serif}
.skip{position:absolute;left:-9999px}
.skip:focus{left:8px;top:8px;z-index:10;background:var(--accent);color:#fff;padding:8px 14px;border-radius:8px}
.topbar{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;
padding:14px 20px;border-bottom:1px solid var(--border);background:var(--surface);position:sticky;top:0;z-index:5}
.brand{display:flex;align-items:center;gap:8px;font-weight:800;font-size:1.05rem;color:var(--text);text-decoration:none}
.brand img{border-radius:7px}
.topbar nav{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
.topbar nav a{color:var(--muted);text-decoration:none;font-size:.92rem;padding:6px 10px;border-radius:8px}
.topbar nav a:hover{color:var(--text);background:var(--surface-2)}
.topbar nav a.cta{background:var(--accent);color:#fff;font-weight:700}
main{max-width:820px;margin:0 auto;padding:24px 20px 12px}
.crumbs{font-size:.85rem;color:var(--muted);margin-bottom:18px}
.crumbs a{color:var(--muted)}
.crumbs .sep{margin:0 6px;opacity:.6}
h1{font-size:1.9rem;line-height:1.35;margin:0 0 14px;letter-spacing:-.02em}
h2{font-size:1.3rem;margin:38px 0 12px;padding-top:14px;border-top:1px solid var(--border);letter-spacing:-.01em}
h3{font-size:1.05rem;margin:24px 0 8px}
p{margin:0 0 14px}
a{color:var(--accent)}
.lead{font-size:1.08rem;color:var(--text);background:var(--accent-soft);border-left:3px solid var(--accent);
padding:14px 16px;border-radius:0 10px 10px 0;margin-bottom:24px}
.muted{color:var(--muted);font-size:.9rem}
ul,ol{margin:0 0 14px;padding-left:22px}
li{margin-bottom:7px}
.wrap{overflow-x:auto;-webkit-overflow-scrolling:touch;margin:0 0 18px}
table{border-collapse:collapse;width:100%;min-width:420px;font-size:.94rem;background:var(--surface);border-radius:10px;overflow:hidden}
caption{text-align:left;color:var(--muted);font-size:.86rem;padding-bottom:8px}
th,td{border:1px solid var(--border);padding:9px 12px;text-align:left}
th{background:var(--surface-2);font-weight:700}
td.num,th.num{text-align:center;font-variant-numeric:tabular-nums}
.grid{display:grid;gap:12px;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));margin-bottom:18px}
.card{display:block;background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:14px 16px;text-decoration:none;color:var(--text)}
a.card:hover{border-color:var(--accent)}
.card .t{font-weight:800;font-size:1.05rem;margin-bottom:4px}
.card .d{color:var(--muted);font-size:.88rem;line-height:1.6}
.tag{display:inline-block;font-size:.75rem;font-weight:700;padding:2px 8px;border-radius:999px;background:var(--surface-2);color:var(--muted);margin-right:6px}
.tag.easy{background:#dcfce7;color:#166534}
.tag.medium{background:#fef3c7;color:#92400e}
.tag.hard{background:#fee2e2;color:#991b1b}
@media (prefers-color-scheme:dark){.tag.easy{background:#052e16;color:#86efac}.tag.medium{background:#292109;color:#fcd34d}.tag.hard{background:#2d0f0f;color:#fca5a5}}
.note{background:var(--warn-soft);border:1px solid var(--border);border-left:3px solid var(--warn);padding:12px 16px;border-radius:0 10px 10px 0;margin-bottom:18px}
.qa{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:4px 16px;margin-bottom:12px}
.qa h3{margin:14px 0 6px;font-size:1rem}
.qa p{color:var(--muted)}
.pager{display:flex;justify-content:space-between;gap:12px;margin:32px 0 8px;font-size:.92rem}
.foot{max-width:820px;margin:0 auto;padding:22px 20px 44px;border-top:1px solid var(--border);color:var(--muted);font-size:.88rem}
.foot p{margin:0 0 6px}
`;

// ─────────────────────────────────────────────────────────── 조각 렌더러

function factsTable(table) {
  const rows = factsFor(table)
    .map(
      (f) =>
        `<tr><td class="num">${f.a} × ${f.b}</td><td class="num"><strong>${f.answer}</strong></td><td>${esc(f.reading)}</td></tr>`,
    )
    .join('\n');
  return `<div class="wrap"><table>
<caption>${table}단 곱셈식 9개 — 식, 답, 읽는 법</caption>
<thead><tr><th class="num">곱셈식</th><th class="num">답</th><th>읽는 법</th></tr></thead>
<tbody>
${rows}
</tbody></table></div>`;
}

function fullGrid() {
  const head = ['×', ...Array.from({ length: 9 }, (_, i) => i + 1)]
    .map((h) => `<th class="num">${h}</th>`)
    .join('');
  const rows = TABLES.map((t) => {
    const cells = factsFor(t.table)
      .map((f) => `<td class="num">${f.answer}</td>`)
      .join('');
    return `<tr><th class="num"><a href="/guide/${t.table}-dan">${t.table}</a></th>${cells}</tr>`;
  }).join('\n');
  return `<div class="wrap"><table>
<caption>2단부터 9단까지 전체 구구단 표 (가로: 곱하는 수 1~9)</caption>
<thead><tr>${head}</tr></thead>
<tbody>
${rows}
</tbody></table></div>`;
}

const DIFF_KO = { easy: '쉬움', medium: '보통', hard: '어려움' };

function qaBlock(items) {
  return items
    .map((x) => `<div class="qa"><h3>${esc(x.q)}</h3><p>${esc(x.a)}</p></div>`)
    .join('\n');
}

function faqSchema(items) {
  return {
    '@type': 'FAQPage',
    mainEntity: items.map((x) => ({
      '@type': 'Question',
      name: x.q,
      acceptedAnswer: { '@type': 'Answer', text: x.a },
    })),
  };
}

// ─────────────────────────────────────────────────────────── 페이지: 허브

function buildHub() {
  const slug = '/guide';
  const cards = TABLES.map(
    (t) =>
      `<a class="card" href="/guide/${t.table}-dan"><div class="t">${t.table}단 <span class="tag ${t.difficulty}">${DIFF_KO[t.difficulty]}</span></div><div class="d">${esc(t.summary)}</div></a>`,
  ).join('\n');

  const steps = pathDoc.steps
    .map(
      (s) =>
        `<tr><td class="num">${s.step}</td><td class="num"><a href="/guide/${s.table}-dan">${s.table}단</a></td><td>${esc(s.keyIdea)}</td><td>${esc(s.estimatedDays)}</td></tr>`,
    )
    .join('\n');

  const rules = TABLES.map((t) => `<tr><td class="num"><a href="/guide/${t.table}-dan">${t.table}단</a></td><td>${esc(t.pattern)}</td></tr>`).join('\n');

  const hubFaq = faqDoc.faqs.filter((f) => ['learning-order', 'commutative', 'hardest-table', 'how-long'].includes(f.id));

  const blocks = [
    `<p>구구단은 1부터 9까지의 수를 서로 곱한 결과를 정리한 곱셈표로, 한국 초등학교 2학년 수학에서 <strong>곱셈구구</strong>라는 이름으로 배웁니다. 이 가이드는 실제로 암기 부담이 있는 <strong>2단~9단, 곱하는 수 1~9까지 총 72개 곱셈식</strong>을 다룹니다.</p>`,

    `<h2>2~9단 전체 구구단 표</h2>`,
    fullGrid(),
    `<p class="muted">숫자를 클릭하면 해당 단의 학습 전략, 자주 틀리는 문제, 읽는 법이 담긴 페이지로 이동합니다.</p>`,

    `<h2>구구단 학습 순서 — 단 번호 순서로 외우면 안 되는 이유</h2>`,
    `<p>${esc(pathDoc.summary)} 2단·5단처럼 규칙이 눈에 보이는 단을 먼저 배워 성공 경험을 만들고, 앞 단이 뒷 단의 계산 발판이 되도록 배치하며, 규칙이 약한 7단·8단을 가장 마지막에 둡니다.</p>`,
    `<div class="wrap"><table>
<caption>권장 학습 순서 8단계</caption>
<thead><tr><th class="num">순서</th><th class="num">단</th><th>핵심 아이디어</th><th>예상 기간</th></tr></thead>
<tbody>
${steps}
</tbody></table></div>`,
    `<ul>${pathDoc.principles.map((p) => `<li><strong>${esc(p.title)}</strong> — ${esc(p.detail)}</li>`).join('')}</ul>`,

    `<h2>단별 학습 가이드</h2>`,
    `<div class="grid">\n${cards}\n</div>`,

    `<h2>단마다 있는 검산 규칙</h2>`,
    `<p>답이 맞는지 1초 안에 확인하는 방법입니다. 이 규칙만으로도 틀린 답의 상당수를 즉시 걸러낼 수 있습니다.</p>`,
    `<div class="wrap"><table>
<caption>단별 규칙 한눈에 보기</caption>
<thead><tr><th class="num">단</th><th>규칙</th></tr></thead>
<tbody>
${rules}
</tbody></table></div>`,

    `<h2>72개를 다 외울 필요는 없습니다</h2>`,
    `<p>곱셈은 순서를 바꿔도 답이 같습니다(교환법칙). 3 × 7과 7 × 3은 한 번만 외우면 되므로 <strong>실제로 외울 식은 36개</strong>입니다. 뒤쪽 단으로 갈수록 새로 외울 식이 줄어들어, 7단에서 새로 외울 식은 7 × 7, 7 × 8, 7 × 9 세 개뿐입니다.</p>`,
    `<div class="note"><strong>구구단 읽는 법</strong> — ${esc(tablesDoc.readingRule)}</div>`,

    `<h2>게임으로 반복하기</h2>`,
    `<p>${esc(site.summary)} 자세한 규칙은 <a href="/guide/modes">게임 모드 안내</a>에서 볼 수 있습니다.</p>`,
    `<div class="grid">${modesDoc.modes
      .map((m) => `<div class="card"><div class="t">${esc(m.name)}</div><div class="d">${esc(m.tagline)} — ${esc(m.rule)}</div></div>`)
      .join('\n')}</div>`,
    `<p><a class="card" href="/" style="text-align:center;font-weight:800;color:var(--accent)">구구 어드벤처 앱으로 구구단 시작하기 →</a></p>`,

    `<h2>자주 묻는 질문</h2>`,
    qaBlock(hubFaq.map((f) => ({ q: f.q, a: f.a }))),
    `<p><a href="/guide/faq">자주 묻는 질문 전체 보기 →</a></p>`,

    `<h2>AI·개발자를 위한 데이터</h2>`,
    `<p>이 가이드의 모든 내용은 기계가 읽을 수 있는 형식으로도 제공됩니다.</p>`,
    `<ul>${site.dataEndpoints.map((d) => `<li><a href="${d.path}"><code>${esc(d.path)}</code></a> — ${esc(d.description)}</li>`).join('')}</ul>`,
  ];

  return write(
    'guide/index.html',
    page({
      slug,
      title: '구구단 완전 정복 가이드 — 2단부터 9단까지 표·학습 순서·외우는 법',
      description:
        '2~9단 전체 구구단 표와 단별 학습 전략을 한 곳에 정리했습니다. 권장 학습 순서(2→5→3→4→6→9→7→8), 단별 검산 규칙, 자주 틀리는 곱셈식과 해결법을 담았습니다.',
      keywords: ['구구단', '구구단 표', '구구단 외우는 법', '곱셈구구', '구구단 순서', '구구단 게임', '초등 2학년 수학', '2단', '9단'],
      heading: '구구단 완전 정복 가이드 — 2단부터 9단까지',
      lead: '구구단 72개를 전부 외울 필요는 없습니다. 교환법칙을 쓰면 실제로 외울 식은 36개이고, 단마다 답을 만들어 내는 방법과 1초 검산 규칙이 있습니다. 이 문서는 2~9단 전체 표, 권장 학습 순서, 단별 전략을 한 곳에 모은 가이드입니다.',
      trail: [
        { name: '홈', path: '/' },
        { name: '구구단 가이드', path: '/guide' },
      ],
      structured: [
        { '@type': 'WebSite', '@id': `${ORIGIN}/#website`, url: ORIGIN, name: site.name, description: site.summary, inLanguage: 'ko', publisher: ORG },
        APP_ENTITY,
        {
          '@type': 'LearningResource',
          name: '구구단 완전 정복 가이드',
          url: `${ORIGIN}${slug}`,
          inLanguage: 'ko',
          learningResourceType: '학습 가이드',
          educationalLevel: site.audience.educationalLevel,
          teaches: '2단부터 9단까지의 곱셈구구',
          audience: { '@type': 'EducationalAudience', educationalRole: 'student' },
          dateModified: UPDATED,
        },
        {
          '@type': 'ItemList',
          name: '단별 학습 가이드',
          itemListElement: TABLES.map((t, i) => ({
            '@type': 'ListItem',
            position: i + 1,
            name: `${t.table}단`,
            url: `${ORIGIN}/guide/${t.table}-dan`,
          })),
        },
        faqSchema(hubFaq.map((f) => ({ q: f.q, a: f.a }))),
      ],
      blocks,
    }),
  );
}

// ─────────────────────────────────────────────────────────── 페이지: 단별

function buildTable(t) {
  const slug = `/guide/${t.table}-dan`;
  const step = pathDoc.steps.find((s) => s.table === t.table);
  const idx = TABLES.findIndex((x) => x.table === t.table);
  const prev = TABLES[idx - 1];
  const next = TABLES[idx + 1];
  const facts = factsFor(t.table);

  const mistakes = t.commonMistakes
    .map((m) => {
      const f = factsDoc.facts.find((x) => x.id === m.fact);
      return `<tr><td class="num"><strong>${f.a} × ${f.b} = ${m.correct}</strong></td><td class="num">${m.confusedWith.join(', ')}</td><td>${esc(m.why)} <em>${esc(m.fix)}</em></td></tr>`;
    })
    .join('\n');

  const blocks = [
    `<p><span class="tag ${t.difficulty}">난이도 ${DIFF_KO[t.difficulty]}</span><span class="tag">권장 학습 순서 ${t.learnOrder}번째</span>${step ? `<span class="tag">예상 기간 ${esc(step.estimatedDays)}</span>` : ''}</p>`,
    `<p>${esc(t.whyMatters)}</p>`,

    `<h2>${t.table}단 표와 읽는 법</h2>`,
    factsTable(t.table),
    `<div class="note"><strong>${t.table}단의 규칙</strong> — ${esc(t.pattern)}</div>`,

    `<h2>${t.table}단 외우는 법</h2>`,
    `<ol>${t.strategies.map((s) => `<li><strong>${esc(s.title)}</strong> — ${esc(s.detail)}</li>`).join('\n')}</ol>`,

    `<h2>${t.table}단에서 자주 틀리는 문제</h2>`,
    `<div class="wrap"><table>
<caption>${t.table}단 오답이 많은 식과 해결법</caption>
<thead><tr><th class="num">맞는 식</th><th class="num">흔한 오답</th><th>왜 틀리고, 어떻게 고치나</th></tr></thead>
<tbody>
${mistakes}
</tbody></table></div>`,

    `<h2>${t.table}단이 나오는 곳</h2>`,
    `<ul>${t.realLife.map((r) => `<li>${esc(r)}</li>`).join('')}</ul>`,

    `<h2>${t.table}단과 함께 보면 좋은 단</h2>`,
    `<div class="grid">${t.relatedTables
      .map((n) => {
        const rel = TABLES.find((x) => x.table === n);
        return rel
          ? `<a class="card" href="/guide/${n}-dan"><div class="t">${n}단</div><div class="d">${esc(rel.summary)}</div></a>`
          : `<div class="card"><div class="t">${n}단</div><div class="d">이 앱의 학습 범위(2~9단) 밖이지만 규칙 확장에 도움이 됩니다.</div></div>`;
      })
      .join('\n')}</div>`,

    `<h2>${t.table}단 자주 묻는 질문</h2>`,
    qaBlock(t.questions),

    `<h2>${t.table}단 연습하기</h2>`,
    `<p>구구 어드벤처의 <a href="/guide/modes">학습 모드</a>에서 단을 <strong>${t.table}단</strong>으로 지정하면 이 단만 집중해서 반복할 수 있습니다. 틀린 식은 출제 가중치가 올라가 더 자주 나옵니다.${step ? ` ${t.table}단에는 <strong>${esc(modesDoc.modes.find((m) => m.id === step.recommendedMode)?.name ?? '학습')}</strong> 모드를 권합니다.` : ''}</p>`,
    `<p><a class="card" href="/" style="text-align:center;font-weight:800;color:var(--accent)">${t.table}단 연습 시작하기 →</a></p>`,

    `<nav class="pager">${prev ? `<a href="/guide/${prev.table}-dan">← ${prev.table}단</a>` : '<span></span>'}<a href="/guide">가이드 전체</a>${next ? `<a href="/guide/${next.table}-dan">${next.table}단 →</a>` : '<span></span>'}</nav>`,
  ];

  const factList = facts.map((f) => `${f.expression}`).join(', ');

  return write(
    `guide/${t.table}-dan.html`,
    page({
      slug,
      title: `${t.table}단 구구단 — 표·읽는 법·외우는 법과 자주 틀리는 문제`,
      description: `${t.table}단 곱셈식 9개의 표와 읽는 법, ${t.strategies[0].title} 등 학습 전략, ${t.commonMistakes.map((m) => m.fact.replace('x', ' × ')).join('·')}처럼 자주 틀리는 식의 원인과 해결법을 정리했습니다.`,
      keywords: [`${t.table}단`, `구구단 ${t.table}단`, `${t.table}단 외우는 법`, `${t.table}단 표`, '구구단', '곱셈구구'],
      heading: `${t.table}단 — ${t.summary}`,
      // h1이 요약을 담당하므로 lead는 규칙 + 첫 전략으로 바로 들어간다.
      // ('N단은 ' 접두어를 붙이면 pattern 문장과 주어가 겹친다)
      lead: `${esc(t.pattern)} ${esc(t.strategies[0].detail)}`,
      trail: [
        { name: '홈', path: '/' },
        { name: '구구단 가이드', path: '/guide' },
        { name: `${t.table}단`, path: slug },
      ],
      structured: [
        {
          '@type': 'LearningResource',
          name: `${t.table}단 구구단 학습 가이드`,
          url: `${ORIGIN}${slug}`,
          inLanguage: 'ko',
          learningResourceType: '학습 가이드',
          educationalLevel: site.audience.educationalLevel,
          teaches: `${t.table}단 곱셈구구 (${factList})`,
          dateModified: UPDATED,
        },
        {
          '@type': 'HowTo',
          name: `${t.table}단 외우는 법`,
          description: t.summary,
          inLanguage: 'ko',
          totalTime: step ? 'P5D' : undefined,
          step: t.strategies.map((s, i) => ({
            '@type': 'HowToStep',
            position: i + 1,
            name: s.title,
            text: s.detail,
            url: `${ORIGIN}${slug}#step-${i + 1}`,
          })),
        },
        faqSchema(t.questions),
      ],
      blocks,
    }),
  );
}

// ─────────────────────────────────────────────────────────── 페이지: FAQ

function buildFaq() {
  const slug = '/guide/faq';
  const blocks = [];
  for (const cat of faqDoc.categories) {
    const items = faqDoc.faqs.filter((f) => f.category === cat.id);
    if (!items.length) continue;
    blocks.push(`<h2>${esc(cat.name)}</h2>`);
    blocks.push(qaBlock(items.map((f) => ({ q: f.q, a: f.a }))));
  }
  blocks.push(`<h2>더 보기</h2>`);
  blocks.push(
    `<ul><li><a href="/guide">2~9단 전체 표와 학습 순서</a></li><li><a href="/guide/modes">게임 모드별 규칙</a></li><li><a href="/guide/7-dan">가장 어려운 7단 공략</a></li></ul>`,
  );

  return write(
    'guide/faq.html',
    page({
      slug,
      title: '구구단 학습 자주 묻는 질문 — 순서·기간·안 외워질 때',
      description:
        '구구단을 어떤 순서로 외워야 하는지, 다 외우는 데 얼마나 걸리는지, 아이가 못 외울 때 어떻게 도와야 하는지 등 구구단 학습에서 가장 많이 나오는 질문에 답합니다.',
      keywords: ['구구단 질문', '구구단 순서', '구구단 외우는 기간', '구구단 안 외워질 때', '구구단 교육', '곱셈구구'],
      heading: '구구단 학습 자주 묻는 질문',
      lead: '구구단 학습 순서, 필요한 기간, 잘 안 외워질 때의 대처법까지 가장 많이 나오는 질문을 모았습니다. 각 답은 구구 어드벤처의 학습 데이터와 단별 가이드에 근거합니다.',
      trail: [
        { name: '홈', path: '/' },
        { name: '구구단 가이드', path: '/guide' },
        { name: '자주 묻는 질문', path: slug },
      ],
      structured: [faqSchema(faqDoc.faqs.map((f) => ({ q: f.q, a: f.a })))],
      blocks,
    }),
  );
}

// ─────────────────────────────────────────────────────────── 페이지: 게임 모드

function buildModes() {
  const slug = '/guide/modes';
  const rows = modesDoc.modes
    .map(
      (m) =>
        `<tr><td><strong>${esc(m.name)}</strong></td><td>${m.questionCount ?? '무제한'}</td><td>${m.timeLimitSeconds ? `${m.timeLimitSeconds}초` : '없음'}</td><td>${m.lives ?? '없음'}</td><td>${m.tableSelectable ? '가능' : '전체 무작위'}</td><td class="num">+${m.xpBonusPerCorrect}</td></tr>`,
    )
    .join('\n');

  const details = modesDoc.modes
    .map(
      (m) => `<h3>${esc(m.name)} — ${esc(m.tagline)}</h3>
<p>${esc(m.detail)} ${esc(m.rule)}</p>
<p class="muted"><strong>이럴 때 좋습니다:</strong> ${esc(m.bestFor)}</p>`,
    )
    .join('\n');

  const blocks = [
    `<p>구구 어드벤처는 같은 72개 곱셈식을 서로 다른 6가지 방식으로 반복하게 합니다. 같은 식을 다른 형태로 만나야 기억이 특정 문제 형식에 묶이지 않기 때문입니다.</p>`,

    `<h2>모드 한눈에 비교</h2>`,
    `<div class="wrap"><table>
<caption>게임 모드 6종의 규칙 비교</caption>
<thead><tr><th>모드</th><th>문제 수</th><th>제한 시간</th><th>목숨</th><th>단 선택</th><th class="num">XP 보너스</th></tr></thead>
<tbody>
${rows}
</tbody></table></div>`,

    `<h2>모드별 자세한 설명</h2>`,
    details,

    `<h2>약한 구구단만 더 자주 나오는 이유</h2>`,
    `<p>${esc(modesDoc.progression.adaptive.description)}</p>`,

    `<h2>레벨·별·스트릭</h2>`,
    `<ul>
<li><strong>레벨과 XP</strong> — ${esc(modesDoc.progression.xp.base)} ${esc(modesDoc.progression.xp.levelFormula)}</li>
<li><strong>마스터리 별</strong> — ${esc(modesDoc.progression.mastery.description)}</li>
<li><strong>스트릭</strong> — ${esc(modesDoc.progression.streak.description)}</li>
</ul>`,

    `<h2>어떤 순서로 쓰면 좋은가</h2>`,
    `<ol>
<li>새로 배우는 단은 <strong>학습</strong> 모드로 정확도부터 잡습니다.</li>
<li>답이 안정되면 <strong>스피드런</strong>으로 속도를 올려 자동화합니다.</li>
<li><strong>OX 퀴즈</strong>로 값이 비슷한 오답을 구분하는 힘을 기릅니다.</li>
<li><strong>빈칸 추리</strong>로 곱셈을 거꾸로 다뤄 나눗셈을 준비합니다.</li>
<li>마지막에 <strong>60초 챌린지</strong>와 <strong>서바이벌</strong>로 2~9단 전체를 점검합니다.</li>
</ol>`,
    `<p><a class="card" href="/" style="text-align:center;font-weight:800;color:var(--accent)">모드 골라서 시작하기 →</a></p>`,
  ];

  return write(
    'guide/modes.html',
    page({
      slug,
      title: '구구단 게임 모드 6종 — 규칙과 추천 사용 순서',
      description:
        '학습·스피드런·60초 챌린지·서바이벌·빈칸 추리·OX 퀴즈 6가지 모드의 규칙, 제한 시간, XP 보너스를 비교하고 어떤 상황에 어떤 모드를 쓰면 좋은지 안내합니다.',
      keywords: ['구구단 게임', '구구단 앱', '구구단 연습', '구구단 퀴즈', '구구단 모드', '초등 수학 게임'],
      heading: '게임 모드 6종 — 규칙과 추천 사용 순서',
      lead: '구구단은 이해보다 자동화가 목표라 노출 횟수가 결정적입니다. 구구 어드벤처는 같은 72개 식을 6가지 방식으로 반복하게 하고, 틀린 식의 출제 가중치를 올려 그 반복을 약한 곳에 집중시킵니다.',
      trail: [
        { name: '홈', path: '/' },
        { name: '구구단 가이드', path: '/guide' },
        { name: '게임 모드', path: slug },
      ],
      structured: [
        APP_ENTITY,
        {
          '@type': 'ItemList',
          name: '구구 어드벤처 게임 모드',
          itemListElement: modesDoc.modes.map((m, i) => ({
            '@type': 'ListItem',
            position: i + 1,
            name: m.name,
            description: m.aiSummary,
          })),
        },
      ],
      blocks,
    }),
  );
}

// ─────────────────────────────────────────────────────────── AI 데이터 산출물

function buildDataset() {
  const written = [];
  const dataset = {
    $schema: 'https://gugu.smap.site/ai/dataset.json',
    version: site.version,
    updated: UPDATED,
    license: site.license,
    site: {
      name: site.name,
      url: site.url,
      summary: site.summary,
      description: site.description,
      audience: site.audience,
      platforms: site.platforms,
      pricing: site.pricing,
      features: site.features,
      scope: site.scope,
      mcp: site.mcp,
      dataEndpoints: site.dataEndpoints,
    },
    readingRule: tablesDoc.readingRule,
    tables: TABLES.map((t) => ({ ...t, facts: factsFor(t.table), url: `${ORIGIN}/guide/${t.table}-dan` })),
    facts: factsDoc.facts,
    modes: modesDoc.modes,
    progression: modesDoc.progression,
    learningPath: pathDoc,
    faq: faqDoc.faqs,
  };
  written.push(write('ai/dataset.json', `${JSON.stringify(dataset, null, 2)}\n`));

  for (const t of TABLES) {
    written.push(
      write(
        `ai/tables/${t.table}.json`,
        `${JSON.stringify(
          {
            version: site.version,
            updated: UPDATED,
            url: `${ORIGIN}/guide/${t.table}-dan`,
            readingRule: tablesDoc.readingRule,
            ...t,
            facts: factsFor(t.table),
            learningStep: pathDoc.steps.find((s) => s.table === t.table) ?? null,
            license: site.license,
          },
          null,
          2,
        )}\n`,
      ),
    );
  }
  return written;
}

// ─────────────────────────────────────────────────────────── robots / sitemap

// AI 검색·학습 크롤러를 명시적으로 허용한다. 기본 Allow만 두면 일부 봇은
// 명시적 User-agent 블록이 없을 때 보수적으로 동작하는 경우가 있어 개별 선언한다.
const AI_BOTS = [
  'GPTBot', 'OAI-SearchBot', 'ChatGPT-User',
  'ClaudeBot', 'Claude-User', 'Claude-SearchBot', 'anthropic-ai',
  'PerplexityBot', 'Perplexity-User',
  'Google-Extended', 'Googlebot', 'Googlebot-Image', 'Bingbot',
  'Applebot', 'Applebot-Extended',
  'DuckDuckBot', 'Yeti', 'Daum',
  'CCBot', 'cohere-ai', 'Meta-ExternalAgent', 'Amazonbot', 'MistralAI-User',
];

function buildRobots() {
  const blocks = AI_BOTS.map((b) => `User-agent: ${b}\nAllow: /`).join('\n\n');
  const body = `# ${site.name} — ${ORIGIN}
# AI 검색·에이전트용 요약: ${ORIGIN}/llms.txt
# 기계 판독용 데이터셋: ${ORIGIN}/ai/dataset.json
# MCP 서버(읽기 전용): ${site.mcp.endpoint}

User-agent: *
Allow: /
# Next.js 내부 산출물(RSC payload)과 OAuth 콜백 — 색인 대상이 아니다.
# 와일드카드 대신 실제 경로를 명시한다. 네이버 Yeti 등 일부 봇은 패턴 문법 지원이 불완전해
# 잘못 매칭되면 llms.txt까지 함께 막힌다.
# /learn·/profile 자체는 막지 않는다. 그 페이지들은 meta robots=noindex로 처리했는데,
# robots.txt로 크롤을 막아 버리면 크롤러가 그 태그를 읽지 못해 URL만 색인되는 역효과가 난다.
Disallow: /_next/static/chunks/
Disallow: /index.txt
Disallow: /learn/index.txt
Disallow: /profile/index.txt
Disallow: /play/index.txt
Disallow: /auth/

${blocks}

Sitemap: ${ORIGIN}/sitemap.xml
Sitemap: ${ORIGIN}/rss.xml
`;
  return write('robots.txt', body);
}

/**
 * RSS 2.0 피드.
 * 네이버 서치어드바이저는 sitemap과 별개로 RSS 제출을 지원하고, 새 문서 수집이 그쪽이 더 빠르다.
 * 가이드 문서를 항목으로 싣는다.
 */
function buildRss() {
  // KST 자정 기준. 콘텐츠 갱신일(site.updated)을 그대로 발행일로 쓴다.
  const pubDate = new Date(`${UPDATED}T00:00:00+09:00`).toUTCString();
  const items = [
    {
      loc: '/guide',
      title: '구구단 완전 정복 가이드 — 2단부터 9단까지',
      desc: '2~9단 전체 표, 권장 학습 순서, 단별 검산 규칙을 한 곳에 정리한 가이드입니다.',
    },
    ...TABLES.map((t) => ({
      loc: `/guide/${t.table}-dan`,
      title: `${t.table}단 구구단 — 표·읽는 법·외우는 법`,
      desc: `${t.summary} ${t.pattern}`,
    })),
    {
      loc: '/guide/modes',
      title: '구구단 게임 모드 6종 — 규칙과 추천 사용 순서',
      desc: '학습·스피드런·60초 챌린지·서바이벌·빈칸 추리·OX 퀴즈의 규칙과 사용 순서를 안내합니다.',
    },
    {
      loc: '/guide/faq',
      title: '구구단 학습 자주 묻는 질문',
      desc: '구구단 학습 순서, 필요한 기간, 잘 안 외워질 때의 대처법을 정리했습니다.',
    },
  ];

  const body = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>${esc(site.name)} — 구구단 가이드</title>
    <link>${ORIGIN}/guide</link>
    <description>${esc(site.summary)}</description>
    <language>ko</language>
    <lastBuildDate>${pubDate}</lastBuildDate>
    <atom:link href="${ORIGIN}/rss.xml" rel="self" type="application/rss+xml" />
${items
  .map(
    (i) => `    <item>
      <title>${esc(i.title)}</title>
      <link>${ORIGIN}${i.loc}</link>
      <guid isPermaLink="true">${ORIGIN}${i.loc}</guid>
      <description>${esc(i.desc)}</description>
      <pubDate>${pubDate}</pubDate>
    </item>`,
  )
  .join('\n')}
  </channel>
</rss>
`;
  return write('rss.xml', body);
}

function buildSitemap() {
  // /learn·/profile은 프리렌더 HTML에 고유 본문이 없어 noindex 처리했다.
  // sitemap에 남겨 두면 "색인하지 말라면서 제출은 한다"는 모순 신호가 되므로 넣지 않는다.
  const urls = [
    { loc: '/', priority: '1.0', changefreq: 'weekly' },
    { loc: '/guide', priority: '0.9', changefreq: 'monthly' },
    ...TABLES.map((t) => ({ loc: `/guide/${t.table}-dan`, priority: '0.8', changefreq: 'monthly' })),
    { loc: '/guide/faq', priority: '0.8', changefreq: 'monthly' },
    { loc: '/guide/modes', priority: '0.7', changefreq: 'monthly' },
    { loc: '/privacy', priority: '0.3', changefreq: 'yearly' },
    { loc: '/terms', priority: '0.3', changefreq: 'yearly' },
  ];
  const body = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls
  .map(
    (u) =>
      `  <url>\n    <loc>${ORIGIN}${u.loc}</loc>\n    <lastmod>${UPDATED}</lastmod>\n    <changefreq>${u.changefreq}</changefreq>\n    <priority>${u.priority}</priority>\n  </url>`,
  )
  .join('\n')}
</urlset>
`;
  return write('sitemap.xml', body);
}

// ─────────────────────────────────────────────────────────── llms.txt

function buildLlmsTxt() {
  const body = `# ${site.name}

> ${site.summary}

${site.description}

학습 범위는 ${site.scope.tables[0]}단~${site.scope.tables[site.scope.tables.length - 1]}단, 곱하는 수 1~9로 총 ${site.scope.totalFacts}개 곱셈식입니다. ${site.scope.note}

## 구구단 가이드 (사람이 읽는 문서)

- [구구단 완전 정복 가이드](${ORIGIN}/guide): 2~9단 전체 표, 권장 학습 순서(2→5→3→4→6→9→7→8), 단별 검산 규칙
${TABLES.map((t) => `- [${t.table}단](${ORIGIN}/guide/${t.table}-dan): ${t.summary} 난이도 ${DIFF_KO[t.difficulty]}.`).join('\n')}
- [게임 모드 안내](${ORIGIN}/guide/modes): 6가지 모드의 규칙, XP·레벨·스트릭 구조, 오답 가중 출제 방식
- [자주 묻는 질문](${ORIGIN}/guide/faq): 학습 순서·기간·안 외워질 때의 대처법 등 ${faqDoc.faqs.length}문답

## 기계 판독용 데이터

- [dataset.json](${ORIGIN}/ai/dataset.json): 사이트·단별 콘텐츠·72개 곱셈식·게임 모드·학습 로드맵·FAQ 전체
${TABLES.map((t) => `- [${t.table}단 데이터](${ORIGIN}/ai/tables/${t.table}.json): ${t.table}단 곱셈식 9개와 학습 전략·오답 패턴`).join('\n')}
- [MCP 서버](${site.mcp.endpoint}): ${site.mcp.description}. transport=${site.mcp.transport}, 인증 없음(읽기 전용). 도구: search_gugu, get_table, get_fact, make_practice_set, list_modes, get_learning_path, search_faq, get_app_info

## 앱

- [구구 어드벤처 앱 소개·설치](${ORIGIN}/): App Store·Google Play 설치 링크
- [웹에서 플레이](${ORIGIN}/play/): 설치 없이 브라우저에서 바로 실행
- 플랫폼: ${site.platforms.map((p) => p.label).join(', ')}
- 가격: ${site.pricing.free} ${site.pricing.premium}

## 핵심 사실 요약

- 구구단에서 가장 어려운 단은 7단이며, 가장 많이 틀리는 식은 7 × 8 = 56입니다.
- 교환법칙(3 × 7 = 7 × 3)을 적용하면 실제로 외울 식은 72개가 아니라 36개입니다.
- 단별 검산 규칙: 2·4·6·8단은 답이 짝수, 3단은 자릿수 합이 3의 배수, 5단은 5나 0으로 끝남, 9단은 자릿수 합이 항상 9.
- 낭독 규칙: ${tablesDoc.readingRule}

## Optional

- [개인정보처리방침](${ORIGIN}/privacy)
- [이용약관](${ORIGIN}/terms)

---
최종 업데이트: ${UPDATED} · 인용 시 출처: ${site.license.attribution}
`;
  return write('llms.txt', body);
}

function buildLlmsFullTxt() {
  const sections = [];
  sections.push(`# ${site.name} — 전체 콘텐츠

> ${site.summary}

이 파일은 ${ORIGIN} 의 가이드 본문 전체를 한 파일로 합친 것입니다. 최종 업데이트 ${UPDATED}. 인용 시 출처: ${site.license.attribution}
`);

  sections.push(`## 구구단이란

구구단은 1부터 9까지의 수를 서로 곱한 결과를 정리한 곱셈표입니다. 한국 초등학교 2학년 수학에서 '곱셈구구'라는 이름으로 배웁니다. 실제 암기 부담이 있는 범위는 2단~9단, 곱하는 수 1~9로 총 72개 곱셈식입니다.

곱셈은 순서를 바꿔도 답이 같으므로(교환법칙) 3 × 7과 7 × 3은 한 번만 외우면 됩니다. 이 규칙을 적용하면 실제로 외울 식은 36개입니다.

낭독 규칙: ${tablesDoc.readingRule}
`);

  sections.push(`## 권장 학습 순서

${pathDoc.summary}

${pathDoc.principles.map((p) => `- **${p.title}**: ${p.detail}`).join('\n')}

| 순서 | 단 | 목표 | 핵심 아이디어 | 예상 기간 | 추천 모드 |
| --- | --- | --- | --- | --- | --- |
${pathDoc.steps
  .map((s) => `| ${s.step} | ${s.table}단 | ${s.goal} | ${s.keyIdea} | ${s.estimatedDays} | ${modesDoc.modes.find((m) => m.id === s.recommendedMode)?.name ?? s.recommendedMode} |`)
  .join('\n')}

${pathDoc.afterAll.title}
${pathDoc.afterAll.items.map((i) => `- ${i}`).join('\n')}

보호자를 위한 조언
${pathDoc.parentTips.map((i) => `- ${i}`).join('\n')}
`);

  for (const t of TABLES) {
    const facts = factsFor(t.table);
    sections.push(`## ${t.table}단

${t.summary}

${t.whyMatters}

- 난이도: ${DIFF_KO[t.difficulty]} (전체 8개 단 중 ${t.difficultyRank}번째로 어려움)
- 권장 학습 순서: ${t.learnOrder}번째
- 규칙: ${t.pattern}

### ${t.table}단 표

| 곱셈식 | 답 | 읽는 법 |
| --- | --- | --- |
${facts.map((f) => `| ${f.a} × ${f.b} | ${f.answer} | ${f.reading} |`).join('\n')}

### ${t.table}단 외우는 법

${t.strategies.map((s, i) => `${i + 1}. **${s.title}** — ${s.detail}`).join('\n')}

### ${t.table}단에서 자주 틀리는 문제

${t.commonMistakes
  .map((m) => {
    const f = factsDoc.facts.find((x) => x.id === m.fact);
    return `- **${f.a} × ${f.b} = ${m.correct}** (흔한 오답: ${m.confusedWith.join(', ')}) — ${m.why} ${m.fix}`;
  })
  .join('\n')}

### ${t.table}단이 나오는 곳

${t.realLife.map((r) => `- ${r}`).join('\n')}

### ${t.table}단 자주 묻는 질문

${t.questions.map((q) => `**Q. ${q.q}**\n\nA. ${q.a}`).join('\n\n')}

출처: ${ORIGIN}/guide/${t.table}-dan
`);
  }

  sections.push(`## 게임 모드

${site.name}는 같은 72개 곱셈식을 6가지 방식으로 반복하게 합니다.

| 모드 | 문제 수 | 제한 시간 | 목숨 | 단 선택 | XP 보너스 | 이럴 때 좋음 |
| --- | --- | --- | --- | --- | --- | --- |
${modesDoc.modes
  .map(
    (m) =>
      `| ${m.name} | ${m.questionCount ?? '무제한'} | ${m.timeLimitSeconds ? `${m.timeLimitSeconds}초` : '없음'} | ${m.lives ?? '없음'} | ${m.tableSelectable ? '가능' : '전체 무작위'} | +${m.xpBonusPerCorrect} | ${m.bestFor} |`,
  )
  .join('\n')}

${modesDoc.modes.map((m) => `### ${m.name}\n\n${m.detail} ${m.rule}\n\n${m.aiSummary}`).join('\n\n')}

### 진행 시스템

- 레벨/XP: ${modesDoc.progression.xp.base} ${modesDoc.progression.xp.levelFormula}
- 마스터리 별: ${modesDoc.progression.mastery.description}
- 스트릭: ${modesDoc.progression.streak.description}
- 오답 가중 출제: ${modesDoc.progression.adaptive.description}

출처: ${ORIGIN}/guide/modes
`);

  sections.push(`## 자주 묻는 질문

${faqDoc.faqs.map((f) => `**Q. ${f.q}**\n\nA. ${f.a}`).join('\n\n')}

출처: ${ORIGIN}/guide/faq
`);

  sections.push(`## 앱 정보

- 이름: ${site.name} (${site.alternateNames.join(', ')})
- 웹: ${site.url}
- 플랫폼: ${site.platforms.map((p) => p.label).join(', ')}
- 가격: ${site.pricing.free} ${site.pricing.premium}
- 대상: ${site.audience.primary}
- 주요 기능:
${site.features.map((f) => `  - ${f.name}: ${f.detail}`).join('\n')}

### AI를 위한 데이터 접근

${site.dataEndpoints.map((d) => `- \`${d.path}\` (${d.format}) — ${d.description}`).join('\n')}

MCP 서버: ${site.mcp.endpoint} (${site.mcp.transport}, ${site.mcp.auth})
`);

  return write('llms-full.txt', sections.join('\n'));
}

// ─────────────────────────────────────────────────────────── 실행

function main() {
  const written = [];
  written.push(write('guide/guide.css', CSS));
  written.push(buildHub());
  for (const t of TABLES) written.push(buildTable(t));
  written.push(buildFaq());
  written.push(buildModes());
  written.push(...buildDataset());
  written.push(buildRobots());
  written.push(buildSitemap());
  written.push(buildRss());
  written.push(buildLlmsTxt());
  written.push(buildLlmsFullTxt());

  console.log(`AI 검색용 산출물 ${written.length}개 생성 (web/public/):`);
  for (const w of written) console.log(`  - ${w}`);
}

main();
