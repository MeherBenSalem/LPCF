// Renders the listing artwork in marketing/images/ from real in-game captures (marketing/images/screenshots/) and the
// bundled OFL fonts. Usage (from the repository root): NODE_PATH="$(npm root -g)" node marketing/source/render.cjs [name...]
// Requires the `playwright` package with a Chromium build available.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

// The release and what it runs on. Supporting a new Minecraft version means editing VERSIONS (and the compatibility
// table in curseforge/description.md and the README).
const RELEASE = { version: '2.0', name: 'Chat, Nametags & Bubbles' };
const PLATFORMS = ['Paper', 'Purpur', 'Folia'];
const VERSIONS = ['1.20.1', '1.21.1', '26.1.2', '26.2', '26.3'];
// Builds checked in game by tools/qa (docs/QA-2.0.0.md).
const TESTED = { Paper: VERSIONS, Purpur: ['1.21.1', '26.3'], Folia: ['26.2', '26.3'] };

const root = path.resolve(__dirname, '..', '..');
const out = path.join(root, 'marketing', 'images');
const shots = path.join(out, 'screenshots');
const fonts = path.join(__dirname, 'fonts');
const url = (file) => 'file://' + file;
const shot = (name) => url(path.join(shots, name));

// Palette: a server at night. Deep indigo, the translucent nametag plate, a coral-to-amber rank gradient, cyan for
// links and mentions.
const C = { night: '#0d0f1c', deep: '#12152a', panel: '#1a1e38', line: '#2a2f55', ink: '#eef0ff', dim: '#9aa0c8',
  coral: '#ff7a59', amber: '#ffd166', cyan: '#4cc9f0', violet: '#9b8cff', mint: '#3ddc97' };
const GRAD = `linear-gradient(90deg, ${C.coral}, ${C.amber})`;

const css = `
@font-face { font-family: Display; src: url('${url(path.join(fonts, 'space-grotesk.woff2'))}'); font-weight: 300 700; }
@font-face { font-family: Body; src: url('${url(path.join(fonts, 'inter.woff2'))}'); font-weight: 100 900; }
* { box-sizing: border-box; }
html, body { margin: 0; background: ${C.night}; color: ${C.ink}; font-family: Body, sans-serif; font-weight: 500; }
.stage { position: relative; overflow: hidden; background:
  radial-gradient(ellipse at 10% 0%, rgba(155,140,255,.20) 0, rgba(155,140,255,0) 45%),
  radial-gradient(ellipse at 95% 100%, rgba(255,122,89,.16) 0, rgba(255,122,89,0) 45%),
  linear-gradient(180deg, ${C.deep}, ${C.night}); }
.grid { position: absolute; inset: 0; opacity: .35;
  background-image: linear-gradient(rgba(154,160,200,.08) 1px, transparent 1px), linear-gradient(90deg, rgba(154,160,200,.08) 1px, transparent 1px);
  background-size: 48px 48px; }
.stars { position: absolute; inset: 0; opacity: .55;
  background-image: radial-gradient(rgba(255,255,255,.7) 1px, transparent 1.4px), radial-gradient(rgba(255,255,255,.4) 1px, transparent 1.4px);
  background-size: 140px 140px, 90px 90px; background-position: 20px 30px, 70px 10px; }
.display { font-family: Display, sans-serif; font-weight: 700; letter-spacing: -.02em; }
.grad { background: ${GRAD}; -webkit-background-clip: text; background-clip: text; color: transparent; }
/* The nametag plate: Minecraft draws names on a translucent dark rectangle. */
.plate { display: inline-flex; align-items: center; gap: 12px; padding: 8px 18px 7px; border-radius: 10px;
  background: rgba(8,9,20,.62); border: 1px solid rgba(255,255,255,.10); box-shadow: 0 12px 30px rgba(0,0,0,.35); }
.kicker { display: inline-flex; align-items: center; gap: 10px; font-weight: 800; font-size: 19px; letter-spacing: .18em;
  text-transform: uppercase; color: ${C.amber}; }
.kicker::before { content: ''; width: 26px; height: 3px; border-radius: 3px; background: ${GRAD}; }
.photo { position: relative; overflow: hidden; border-radius: 18px; background: #000; border: 1px solid rgba(255,255,255,.14);
  box-shadow: 0 0 0 6px rgba(255,255,255,.03), 0 30px 70px rgba(0,0,0,.55); }
.photo > div > img { display: block; }
.chip { display: inline-flex; align-items: center; gap: 8px; font-weight: 700; font-size: 18px; letter-spacing: .04em;
  padding: 8px 14px 7px; border-radius: 999px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14); color: ${C.ink}; }
ul.points { list-style: none; padding: 0; margin: 0; }
ul.points li { position: relative; padding-left: 38px; margin: 0 0 18px; font-size: 26px; line-height: 1.32; font-weight: 500; color: #d9dcf5; }
ul.points li::before { content: '»'; position: absolute; left: 4px; top: -2px; font-family: Display; font-weight: 700; font-size: 28px; color: ${C.coral}; }
b.hi { color: #fff; font-weight: 800; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: .86em; color: ${C.cyan}; }
.dots { display: inline-flex; gap: 7px; }
.dots i { width: 11px; height: 11px; border-radius: 50%; background: ${C.amber}; display: block; }
.dots i:nth-child(2) { background: #ffa65f; } .dots i:nth-child(3) { background: ${C.coral}; }
`;

const page = (width, height, body, transparent = false) => `<!doctype html><html><head><meta charset="utf-8"><style>${css}
body { width: ${width}px; height: ${height}px; } .stage { width: ${width}px; height: ${height}px; }
${transparent ? 'html, body, .stage { background: transparent; }' : ''}</style></head>
<body><div class="stage">${body}</div></body></html>`;

// An exact region (x, y, w, h in 1920x1080 pixels) of a capture, scaled to W pixels wide.
const region = (file, x, y, w, h, W) => `<div style="position:relative;width:${W}px;height:${Math.round(W * h / w)}px;overflow:hidden">
  <img src="${shot(file)}" style="position:absolute;width:${Math.round(1920 * W / w)}px;left:${-Math.round(x * W / w)}px;top:${-Math.round(y * W / w)}px"></div>`;
const photo = (file, crop, W, style = '') => `<div class="photo" style="${style}">${region(file, ...crop, W)}</div>`;
const dots = () => '<span class="dots"><i></i><i></i><i></i></span>';

// The mark: a nametag plate with the gradient brackets, and a chat bubble with typing dots above it.
const mark = (scale = 1) => `<div style="position:relative;width:${300 * scale}px;height:${230 * scale}px">
  <div style="position:absolute;left:${118 * scale}px;top:0;width:${150 * scale}px;height:${84 * scale}px;border-radius:${26 * scale}px;
    background:${C.ink};display:flex;align-items:center;justify-content:center;gap:${12 * scale}px">
    ${[C.amber, '#ffa65f', C.coral].map((c) => `<i style="display:block;width:${20 * scale}px;height:${20 * scale}px;border-radius:50%;background:${c}"></i>`).join('')}
    <div style="position:absolute;left:${22 * scale}px;bottom:${-16 * scale}px;width:0;height:0;border-left:${12 * scale}px solid transparent;
      border-right:${12 * scale}px solid transparent;border-top:${18 * scale}px solid ${C.ink}"></div></div>
  <div style="position:absolute;left:0;top:${108 * scale}px;width:${300 * scale}px;height:${110 * scale}px;border-radius:${18 * scale}px;
    background:rgba(8,9,20,.78);border:${2 * scale}px solid rgba(255,255,255,.14);display:flex;align-items:center;justify-content:center">
    <span class="display grad" style="font-size:${74 * scale}px;line-height:1">[LP]</span></div></div>`;

const card = ({ kicker, title, points, art, width = 1600, height = 900, artWidth = 900 }) => page(width, height, `
  <div class="grid"></div>
  <div style="position:absolute;left:72px;top:80px;width:${width - artWidth - 180}px">
    <span class="kicker">${kicker}</span>
    <div class="display" style="font-size:74px;line-height:1.0;margin:24px 0 34px">${title}</div>
    <ul class="points">${points.map((p) => `<li>${p}</li>`).join('')}</ul>
  </div>
  <div style="position:absolute;right:64px;top:70px;width:${artWidth}px">${art}</div>
  <div style="position:absolute;left:72px;bottom:38px;display:flex;align-items:center;gap:14px;font-weight:700;font-size:16px;letter-spacing:.16em;color:${C.dim};text-transform:uppercase">
    ${dots()} LPCF ${RELEASE.version} · ${PLATFORMS.join(' · ')} · ${VERSIONS[0]} – ${VERSIONS[VERSIONS.length - 1]}</div>`);

// Transparent section header: the title on a nametag plate, with a typing bubble.
const header = (title) => page(1400, 120, `
  <div style="position:absolute;left:0;top:18px;display:flex;align-items:center;gap:18px">
    <div style="width:70px;height:46px;border-radius:16px;background:${C.ink};display:flex;align-items:center;justify-content:center;gap:7px;margin-top:-26px">
      ${[C.amber, '#ffa65f', C.coral].map((c) => `<i style="display:block;width:10px;height:10px;border-radius:50%;background:${c}"></i>`).join('')}</div>
    <div class="plate" style="padding:10px 26px 9px;background:rgba(13,15,28,.88)">
      <span class="display grad" style="font-size:48px;line-height:1.05">[</span>
      <span class="display" style="font-size:46px;line-height:1.05;white-space:nowrap;color:${C.ink}">${title}</span>
      <span class="display grad" style="font-size:48px;line-height:1.05">]</span></div>
  </div>`, true);

const divider = () => page(1400, 44, `
  <div style="position:absolute;left:0;right:0;top:21px;height:2px;background:linear-gradient(90deg,rgba(255,122,89,0),${C.coral} 30%,${C.amber} 70%,rgba(255,209,102,0))"></div>
  <div style="position:absolute;left:50%;top:6px;transform:translateX(-50%);padding:8px 16px;border-radius:999px;background:${C.night};border:1px solid rgba(255,255,255,.14)">${dots()}</div>`, true);

// A tested-builds grid for the compatibility card.
const matrix = () => `<div style="display:grid;grid-template-columns:160px repeat(${VERSIONS.length},1fr);gap:10px;align-items:center">
  <div></div>${VERSIONS.map((v) => `<div class="display" style="text-align:center;font-size:27px;color:${C.ink}">${v}</div>`).join('')}
  ${PLATFORMS.map((p) => `<div class="display" style="font-size:28px;color:${C.amber}">${p}</div>${VERSIONS.map((v) => {
    const tested = TESTED[p].includes(v);
    return `<div style="height:60px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:16px;letter-spacing:.06em;
      background:${tested ? 'rgba(61,220,151,.14)' : 'rgba(255,255,255,.05)'};border:1px solid ${tested ? 'rgba(61,220,151,.55)' : 'rgba(255,255,255,.12)'};
      color:${tested ? C.mint : C.dim}">${tested ? '✔ TESTED' : 'SUPPORTED'}</div>`;
  }).join('')}`).join('')}</div>`;

const assets = {
  icon: () => page(512, 512, `
    <div style="position:absolute;inset:0;border-radius:104px;overflow:hidden;background:
      radial-gradient(circle at 25% 15%, rgba(155,140,255,.45), rgba(155,140,255,0) 55%),
      radial-gradient(circle at 85% 95%, rgba(255,122,89,.40), rgba(255,122,89,0) 55%), linear-gradient(160deg,#1c2044,#0b0d1a)"></div>
    <div style="position:absolute;left:46px;top:136px">${mark(1.4)}</div>`, true),

  banner: () => page(1800, 600, `
    <div class="stars"></div>
    <div style="position:absolute;right:0;top:0;bottom:0;width:1060px">
      <div style="position:absolute;inset:0">${region('hero.jpg', 380, 180, 1180, 668, 1060)}</div>
      <div style="position:absolute;inset:0;background:linear-gradient(90deg,${C.night} 0%,rgba(13,15,28,.6) 12%,rgba(13,15,28,0) 30%)"></div>
    </div>
    <div style="position:absolute;left:80px;top:74px;display:flex;align-items:center;gap:30px">
      <img src="${url(path.join(out, 'icon.png'))}" style="width:150px;height:150px">
      <div>
        <div class="display grad" style="font-size:128px;line-height:.9">LPCF</div>
        <div class="display" style="font-size:44px;line-height:1.1;margin-top:10px">${RELEASE.name}</div>
      </div>
    </div>
    <div style="position:absolute;left:80px;top:318px;width:860px;font-size:31px;line-height:1.35;font-weight:500;color:#d9dcf5">
      Your LuckPerms ranks <b class="hi">in chat, above heads and in the tab list</b>. Chat bubbles, mentions,
      hover cards and clickable links. No client mods.</div>
    <div style="position:absolute;left:80px;bottom:52px;display:flex;gap:12px">
      <span class="chip" style="background:${GRAD};color:#1a1020;border:none">${RELEASE.version} · ${RELEASE.name}</span>
      <span class="chip">${PLATFORMS.join(' · ')}</span>
      <span class="chip">Minecraft ${VERSIONS[0]} – ${VERSIONS[VERSIONS.length - 1]}</span>
    </div>`),

  'card-nametags': () => card({
    kicker: 'New in 2.0', title: 'Nametags that<br>match your chat',
    points: ['Your LuckPerms prefix <b class="hi">above every head</b>, gradients and hex included',
      'Several lines, with PlaceholderAPI', 'Follows teleports, deaths and dimension changes',
      'Hidden when invisible, vanished or spectating; dims when sneaking'],
    art: photo('nametag-lines.jpg', [180, 170, 1560, 880], 900) }),

  'card-bubbles': () => card({
    kicker: 'Chat bubbles', title: 'See who’s<br>talking',
    points: ['Public chat floats above the speaker', 'Stays longer for longer messages',
      'Never for channels, staff chat or muted players', '<code>/lpcf bubbles</code> turns yours off'],
    art: photo('hero.jpg', [380, 120, 1160, 760], 900) }),

  'card-chat': () => card({
    kicker: 'Interactive chat', title: 'Chat that<br>does more',
    points: ['Hover a name for rank and world, click to message', '<b class="hi">@mentions</b> with a ping and an action-bar notice',
      'Clickable links and <code>[ITEM]</code> tooltips', 'Emoji shortcuts: <code>&lt;3</code> → ❤ and your own'],
    art: photo('chat.jpg', [0, 440, 1060, 620], 900) }),

  'card-tab': () => card({
    kicker: 'Tab list', title: 'Sorted by<br>rank',
    points: ['Names from your format, ordered by LuckPerms weight', 'Header and footer with online count, ping and placeholders',
      'Per-group join, first-join and quit messages', 'Group, track and world chat formats'],
    art: photo('tablist.jpg', [500, 0, 920, 560], 900) }),

  'card-safe': () => card({
    kicker: 'Safe by design', title: 'Players type,<br>you format',
    points: ['What players type is never parsed as a format', 'PlaceholderAPI only ever sees your text, not theirs',
      '<code>lpcf.colorcodes</code>: colours and gradients, never click or hover', '<code>/lpcf debug</code> shows which format applies and why'],
    art: photo('debug.jpg', [0, 260, 1520, 800], 900) }),

  'card-everywhere': () => card({
    kicker: 'One jar', title: 'Every server,<br>every version',
    points: ['Paper, Purpur and <b class="hi">Folia</b>', 'Minecraft 1.20.1 to 26.3, Java 17+',
      'Your 1.x config and data folder move over by themselves', 'No ProtocolLib, no client mods, no resource pack'],
    art: `${matrix()}${photo('folia.jpg', [520, 200, 1000, 400], 900, 'margin-top:26px')}`, artWidth: 900 }),
};

const HEADERS = { 'whats-new': 'What’s new in 2.0', nametags: 'Nametags', bubbles: 'Chat bubbles', chat: 'Chat',
  tab: 'Tab list', safe: 'Safe chat', commands: 'Commands', config: 'Configuration',
  compatibility: 'Compatibility', faq: 'FAQ', gallery: 'Gallery' };
for (const [key, title] of Object.entries(HEADERS)) assets[`header-${key}`] = () => header(title);
assets.divider = divider;

const SIZES = { icon: [512, 512], banner: [1800, 600], divider: [1400, 44] };
const sizeOf = (name) => SIZES[name] || (name.startsWith('header-') ? [1400, 120] : [1600, 900]);

(async () => {
  const wanted = process.argv.slice(2);
  // The banner shows the icon, so the icon renders first.
  const names = Object.keys(assets).filter((n) => !wanted.length || wanted.includes(n))
    .sort((a, b) => (a === 'icon' ? -1 : b === 'icon' ? 1 : 0));
  const browser = await chromium.launch();
  for (const name of names) {
    const [width, height] = sizeOf(name);
    const pg = await browser.newPage({ viewport: { width, height } });
    const file = path.join(out, `.render-${name}.html`);
    fs.writeFileSync(file, assets[name]());
    await pg.goto(url(file));
    await pg.evaluate(() => document.fonts.ready);
    await pg.waitForTimeout(150);
    await pg.screenshot({ path: path.join(out, `${name}.png`), omitBackground: name === 'icon' || name === 'divider' || name.startsWith('header-') });
    fs.unlinkSync(file);
    await pg.close();
    console.log('rendered', name);
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
