/* MediChain web client. Plain JavaScript, no libraries. All figures are read from the REST API. */
'use strict';

// ------------------------------------------------------------------ helpers
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const num = n => Number(n ?? 0).toLocaleString('en-IN');
const one = n => (Math.round(n * 10) / 10).toLocaleString('en-IN');
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const dmy = iso => { if (!iso) return '-'; const [y, m, d] = String(iso).slice(0, 10).split('-'); return d + '-' + MON[+m - 1] + '-' + y; };
const dm = iso => { const [, m, d] = String(iso).slice(0, 10).split('-'); return d + '-' + MON[+m - 1]; };
const hms = iso => iso ? String(iso).slice(11, 19) : '-';
const stamp = iso => iso ? dmy(iso) + ' ' + String(iso).slice(11, 16) : '-';
const addDays = (iso, n) => { const d = new Date(String(iso).slice(0, 10) + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const mean = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
const sum = a => a.reduce((x, y) => x + y, 0);
const STATUS = {STOCKED_OUT: 'Stocked out', UNDERSTOCKED: 'Understocked', ADEQUATE: 'Adequately stocked', OVERSTOCKED: 'Overstocked', UNKNOWN: 'No consumption'};
const RISK = {CRITICAL: 'Critical', HIGH: 'High', MODERATE: 'Moderate', LOW: 'Low'};
const WORD = {EXPIRED: 'Expired', EXPIRING_SOON: 'Near expiry', USABLE: 'Usable', PROPOSED: 'Pending', EXECUTED: 'Executed', REJECTED: 'Rejected', SUPERSEDED: 'Superseded',
  URGENT: 'Urgent', ORDER_NOW: 'Indent now', ORDER_SOON: 'Indent due', ISSUE: 'Issue', RECEIPT: 'Receipt', TRANSFER_IN: 'Transfer in', TRANSFER_OUT: 'Transfer out', STOCK_OUT: 'Not supplied'};
const st = code => '<span class="st ' + esc(code) + '"><i></i>' + esc(STATUS[code] || code) + '</span>';
const rk = code => '<span class="rk ' + esc(code) + '">' + esc(RISK[code] || code) + '</span>';
const tag = code => '<span class="tag ' + esc(code) + '">' + esc(WORD[code] || code) + '</span>';
const days = d => d == null ? '180+' : one(d);
const opts = (list, sel, label, value) => list.map(o => { const v = value ? value(o) : o; return '<option value="' + esc(v) + '"' + (String(v) === String(sel) ? ' selected' : '') + '>' + esc(label ? label(o) : o) + '</option>'; }).join('');
const panel = (title, body, extra) => '<section class="panel"><h2><div>' + title + '</div>' + (extra || '') + '</h2>' + body + '</section>';
const none = (cols, text) => '<tr><td class="none" colspan="' + cols + '">' + esc(text) + '</td></tr>';

function toast(message, isError) {
  const t = $('toast'); t.textContent = message; t.className = isError ? 'err' : ''; t.style.display = 'block';
  clearTimeout(toast.timer); toast.timer = setTimeout(() => { t.style.display = 'none'; }, isError ? 7000 : 3000);
}

// ------------------------------------------------------------------ state and API
const S = {
  session: null, regions: null, meta: null, district: null, horizon: 10, facilities: [],
  overview: null, analytics: null, live: null, lastRefresh: null,
  search: {medicineId: null, facilityId: '', data: null, tab: 'batches'},
  ask: {text: 'Which facilities in the district are at risk of running out of paracetamol and ORS in the next 10 days, and where can stock come from?', result: null},
  inv: {facilityId: null, medicineId: '', batches: []}, fr: {level: 'ALL', medicineId: ''}, fac: {district: ''}, history: [], health: null
};
const store = {
  get(k) { try { return JSON.parse(sessionStorage.getItem(k)); } catch (e) { return null; } },
  set(k, v) { try { if (v == null) sessionStorage.removeItem(k); else sessionStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* storage blocked: the session stays in memory */ } }
};
async function api(path, options) {
  const o = options || {};
  o.headers = Object.assign({}, o.headers, S.session ? {Authorization: 'Bearer ' + S.session.token} : {});
  const res = await fetch(path, o);
  const body = await res.json().catch(() => ({}));
  if (res.status === 401 && S.session) { signedOut('Your session has ended. Sign in again.'); throw new Error('Session ended'); }
  if (!res.ok) throw new Error(body.message || body.error || 'Request failed (' + res.status + ')');
  return body;
}
const post = (path, body) => api(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body || {})});
const qs = p => Object.entries(p).filter(([, v]) => v !== null && v !== undefined && v !== '').map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');
const med = id => S.meta.medicines.find(m => m.id === +id);
const myFacilities = () => S.facilities.filter(f => f.district === S.district);
const isAdmin = () => S.session && S.session.role === 'STATE_ADMIN';

async function loadMeta() {
  S.meta = await api('/api/meta');
  S.facilities = await api('/api/inventory/facilities' + (isAdmin() ? '' : '?' + qs({district: S.district})));
  if (!S.search.medicineId && S.meta.medicines.length) S.search.medicineId = S.meta.medicines[0].id;
}
async function loadOverview() {
  const [o, a, l] = await Promise.all([
    api('/api/analysis?' + qs({district: S.district, horizonDays: S.horizon})),
    api('/api/analytics?' + qs({district: S.district})),
    api('/api/live?' + qs({district: S.district, minutes: 30}))]);
  S.overview = o; S.analytics = a; S.live = l; S.lastRefresh = l.serverTime;
}
async function loadSearch() { S.search.data = await api('/api/analysis?' + qs({district: S.district, medicineId: S.search.medicineId, horizonDays: S.horizon})); }
async function refreshAll() { await loadOverview(); await loadSearch(); }

// ------------------------------------------------------------------ charts (canvas)
function chart(id, cfg) {
  const c = $(id); if (!c) return;
  const ratio = window.devicePixelRatio || 1, w = c.clientWidth, h = c.clientHeight;
  if (!w || !h) return;
  c.width = w * ratio; c.height = h * ratio;
  const g = c.getContext('2d'); g.scale(ratio, ratio); g.font = '11px Tahoma, Arial, sans-serif';
  const pad = {l: 46, r: 10, t: 8, b: 22}, n = cfg.n;
  const all = cfg.series.flatMap(s => s.values.filter(v => v != null));
  let lo = 0, hi = cfg.max != null ? cfg.max : Math.max(1, ...all) * 1.12;
  if (cfg.zoom && all.length) { lo = Math.max(0, Math.floor(Math.min(...all) - 1)); if (hi - lo < 2) lo = Math.max(0, hi - 2); }
  const step = nice((hi - lo) / 4); if (cfg.max == null) hi = lo + step * Math.ceil((hi - lo) / step);
  const band = (w - pad.l - pad.r) / (cfg.bars ? n : Math.max(1, n - 1));
  const x = i => cfg.bars ? pad.l + band * (i + .5) : pad.l + band * i;
  const y = v => pad.t + (h - pad.t - pad.b) * (1 - (Math.min(Math.max(v, lo), hi) - lo) / (hi - lo));
  g.lineWidth = 1; g.textAlign = 'right';
  for (let v = lo; v <= hi + 1e-9; v += step) {
    g.strokeStyle = '#e3e6ea'; g.beginPath(); g.moveTo(pad.l, y(v)); g.lineTo(w - pad.r, y(v)); g.stroke();
    g.fillStyle = '#5b6673'; g.fillText((Math.round(v * 10) / 10) + (cfg.unit || ''), pad.l - 6, y(v) + 4);
  }
  g.strokeStyle = '#8a94a3'; g.beginPath(); g.moveTo(pad.l, pad.t); g.lineTo(pad.l, h - pad.b); g.lineTo(w - pad.r, h - pad.b); g.stroke();
  g.textAlign = 'center'; g.fillStyle = '#5b6673';
  (cfg.labels || []).forEach(l => g.fillText(l.text, Math.min(Math.max(x(l.i), pad.l + 16), w - pad.r - 16), h - 6));
  if (cfg.marker) {
    const mx = x(cfg.marker.i); g.strokeStyle = '#b3261e'; g.lineWidth = 1.5; g.beginPath(); g.moveTo(mx, pad.t); g.lineTo(mx, h - pad.b); g.stroke();
    g.fillStyle = '#b3261e'; g.textAlign = mx > w - 90 ? 'right' : 'left'; g.fillText(cfg.marker.text, mx + (g.textAlign === 'right' ? -4 : 4), pad.t + 10);
  }
  cfg.series.forEach(s => {
    const pts = s.values.map((v, i) => v == null ? null : [x(i), y(v)]).filter(Boolean);
    if (!pts.length) return;
    if (s.bars) { g.fillStyle = s.color; const bw = Math.max(2, band * .68); pts.forEach(p => g.fillRect(p[0] - bw / 2, p[1], bw, Math.max(0, h - pad.b - p[1]))); return; }
    if (s.fill) { g.fillStyle = s.fill; g.beginPath(); g.moveTo(pts[0][0], h - pad.b); pts.forEach(p => g.lineTo(p[0], p[1])); g.lineTo(pts[pts.length - 1][0], h - pad.b); g.closePath(); g.fill(); }
    g.strokeStyle = s.color; g.lineWidth = 2; g.setLineDash(s.dashed ? [6, 4] : []);
    g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])); g.stroke(); g.setLineDash([]);
    if (s.dots) { g.fillStyle = s.color; pts.forEach(p => { g.beginPath(); g.arc(p[0], p[1], 2.6, 0, 7); g.fill(); }); }
  });
  c.onmousemove = e => {
    if (!cfg.tip) return;
    const r = c.getBoundingClientRect(), px = e.clientX - r.left;
    const i = Math.round(cfg.bars ? (px - pad.l) / band - .5 : (px - pad.l) / band);
    const text = i >= 0 && i < n ? cfg.tip(i) : null, t = $('tip');
    if (!text) { t.style.display = 'none'; return; }
    t.textContent = text; t.style.display = 'block'; t.style.left = Math.min(e.clientX + 12, window.innerWidth - t.offsetWidth - 8) + 'px'; t.style.top = (e.clientY - 28) + 'px';
  };
  c.onmouseleave = () => { $('tip').style.display = 'none'; };
}
function nice(raw) { const p = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-9)))), f = raw / p; return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p; }
const weekly = (arr, weeks) => { const out = []; for (let w = weeks; w >= 1; w--) out.push(mean(arr.slice(arr.length - w * 7, arr.length - (w - 1) * 7))); return out; };

// ------------------------------------------------------------------ sign-in
function signinPage(message) {
  const r = S.regions;
  $('app').innerHTML = '<div class="signin"><div class="bar"><div class="sys">MediChain<small>Drug Stock Monitoring System</small></div><div class="who">' + esc(r ? r.stateName : '') + '</div></div><div class="mid">'
    + '<div class="about"><h1>Drug Stock Monitoring System</h1><p>Stock position, stock-out forecast, expiry watch and inter-facility transfer advice for government health facilities.</p>'
    + '<table><tbody><tr><th>State</th><td>' + esc(r ? r.stateName : '-') + '</td></tr><tr><th>Districts on the system</th><td>' + (r ? r.districts.length : '-') + '</td></tr>'
    + '<tr><th>Build</th><td>1.0 (proof of concept)</td></tr></tbody></table><p class="note">Proof-of-concept build with simulated stock and consumption data. Not an official government system.</p></div>'
    + '<section class="panel"><h2><div>Sign in</div></h2><form class="pad" id="loginForm"><label><span>User ID</span><input type="text" name="username" autocomplete="username" required autofocus></label>'
    + '<label><span>Password</span><input type="password" name="password" autocomplete="current-password" required></label>'
    + '<label><span>District</span><select name="district" required>' + (r ? opts(r.districts, store.get('mc.lastDistrict') || 'Vellore') : '') + '</select></label>'
    + '<div class="err" id="loginErr">' + esc(message || (r ? '' : 'Services are not reachable. Start inventory-service and forecast-service, then reload.')) + '</div><button class="go">Sign in</button>'
    + '<details><summary>Accounts in this PoC build</summary><table><tbody><tr><th>State administrator</th><td>admin</td><td>Admin@123</td></tr><tr><th>District pharmacist</th><td>dp.vellore</td><td>Vellore@123</td></tr>'
    + '<tr><td colspan="3">Each district has a dp.&lt;district&gt; account with password &lt;District&gt;@123.</td></tr></tbody></table></details></form></section></div>'
    + '<div class="foot"><span>MediChain 1.0</span><span>State administrators open any district. District users open their own district only.</span></div></div>';
  $('loginForm').onsubmit = async e => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(e.target).entries()), btn = e.target.querySelector('button');
    btn.disabled = true; $('loginErr').textContent = '';
    try {
      const res = await fetch('/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(v)});
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || 'Sign-in failed (' + res.status + ')');
      S.session = body; store.set('mc.session', body); store.set('mc.lastDistrict', body.workingDistrict);
      await start();
    } catch (err) { $('loginErr').textContent = err.message; btn.disabled = false; }
  };
}
function signedOut(message) {
  S.session = null; S.overview = null; S.search.data = null; store.set('mc.session', null); stopTimers();
  signinPage(message);
}

// ------------------------------------------------------------------ frame
const NAV = [
  ['Monitoring', [['dashboard', 'Dashboard'], ['search', 'Stock Status'], ['forecast', 'Forecast & Risk']]],
  ['Stores', [['inventory', 'Stores & Ledger'], ['redistribution', 'Transfers & Indents']]],
  ['Masters', [['facilities', 'Facilities'], ['medicines', 'Drug Master']]],
  ['Reports', [['analytics', 'Analytics'], ['reports', 'Reports']]],
  ['Administration', [['settings', 'Settings']]]
];
const PAGES = {dashboard: dashboardPage, search: searchPage, forecast: forecastPage, inventory: inventoryPage, redistribution: transfersPage, facilities: facilitiesPage, medicines: medicinesPage, analytics: analyticsPage, reports: reportsPage, settings: settingsPage};
const TITLE = Object.fromEntries(NAV.flatMap(g => g[1]));
const route = () => { const r = location.hash.replace('#/', ''); return PAGES[r] ? r : 'dashboard'; };

function frame() {
  const s = S.session, r = route(), critical = S.overview ? S.overview.riskCounts.CRITICAL : 0;
  $('app').innerHTML = '<div class="frame"><header class="bar"><div class="sys">MediChain<small>Drug Stock Monitoring System, ' + esc(s.stateName) + '</small></div><div class="who">'
    + '<a class="alerts" href="#/forecast" title="Stock lines at critical risk">Critical<b id="critCount">' + critical + '</b></a>'
    + '<span>District ' + (isAdmin() ? '<select id="gDistrict" aria-label="District">' + opts(S.meta.districts, S.district) + '</select>' : '<b>' + esc(S.district) + '</b>') + '</span>'
    + '<span><b>' + esc(s.displayName) + '</b> (' + esc(s.username) + ')</span><button data-act="signout">Sign out</button></div></header>'
    + '<nav class="nav">' + NAV.map(g => '<h3>' + g[0] + '</h3>' + g[1].map(p => '<a href="#/' + p[0] + '" class="' + (p[0] === r ? 'on' : '') + '">' + esc(p[1]) + '</a>').join('')).join('') + '</nav>'
    + '<main class="main" id="main"></main><footer class="foot"><span>MediChain 1.0 &nbsp;|&nbsp; ' + esc(s.designation) + '</span><span>Signed in ' + stamp(s.signedInAt) + (s.previousLoginAt ? ' &nbsp;|&nbsp; Previous sign-in ' + stamp(s.previousLoginAt) : '') + '</span></footer></div>';
}
function crumb() {
  const l = S.live, on = l && l.feedEnabled;
  return '<div class="crumb"><span>Home &rsaquo; ' + esc(S.session.stateName) + ' &rsaquo; ' + esc(S.district) + ' &rsaquo; <b>' + esc(TITLE[route()]) + '</b></span>'
    + '<span class="live' + (on ? '' : ' off') + '"><i></i>Stock as on <b id="asOn">' + (S.lastRefresh ? dmy(S.lastRefresh) + ' ' + hms(S.lastRefresh) : '-') + '</b> &nbsp;|&nbsp; ' + (on ? 'auto-refresh on' : 'dispensing feed off') + '</span></div>';
}
function render() {
  if (!S.session || !S.meta) return;
  const keep = [];
  document.querySelectorAll('.tall').forEach(el => keep.push(el.scrollTop));
  const mainScroll = $('main') ? $('main').scrollTop : 0, r = route();
  frame();
  $('main').innerHTML = crumb() + (S.overview ? PAGES[r]() : '<p class="pad">Loading data for ' + esc(S.district) + '...</p>');
  $('main').scrollTop = mainScroll;
  document.querySelectorAll('.tall').forEach((el, i) => { if (keep[i]) el.scrollTop = keep[i]; });
  if (!S.overview) return;
  if (r === 'search') searchCharts();
  if (r === 'dashboard' || r === 'analytics') serviceCharts();
  paintLive();
}

// ------------------------------------------------------------------ live panel (repainted every few seconds)
function livePanel() {
  return panel('Stock movement today <span id="liveNote"></span>', '<div class="pad"><div class="strip" id="liveStrip" style="margin-bottom:8px"></div>'
    + '<div class="key"><span><i class="b" style="border-color:#23395d"></i>Units issued per minute, last 30 minutes</span></div><canvas id="cLive" style="height:150px"></canvas></div>'
    + '<div class="scroll"><table><thead><tr><th>Time</th><th>Facility</th><th>Drug</th><th>Entry</th><th class="n">Qty</th><th class="n">Balance</th></tr></thead><tbody id="liveRows"></tbody></table></div>');
}
function paintLive() {
  const l = S.live; if (!l) return;
  const asOn = $('asOn'); if (asOn) asOn.textContent = dmy(l.serverTime) + ' ' + hms(l.serverTime);
  if ($('liveNote')) $('liveNote').textContent = (l.ledgerSince ? '(ledger from ' + hms(l.ledgerSince).slice(0, 5) + '; ' : '(') + (l.feedEnabled ? 'simulated dispensing feed, ' + l.feedSpeed + 'x real time)' : 'dispensing feed off)');
  if ($('liveStrip')) {
    $('liveStrip').innerHTML = '<div><b>' + num(l.issuedUnitsToday) + '</b><span>units issued</span></div><div><b>' + num(l.issueCountToday) + '</b><span>issue entries</span></div>'
      + '<div class="' + (l.shortEventsToday ? 'o' : 'k') + '"><b>' + num(l.shortEventsToday) + '</b><span>not fully supplied</span></div><div class="k"><b>' + l.fillRateTodayPct + '%</b><span>fill rate today</span></div>'
      + '<div><b>' + num(l.receivedUnitsToday) + '</b><span>units received</span></div><div><b>' + num(l.transferredUnitsToday) + '</b><span>units transferred in</span></div>';
  }
  if ($('liveRows')) {
    const only = route() === 'inventory' ? l.recent.filter(t => String(t.facilityId) === String(S.inv.facilityId)) : l.recent;
    $('liveRows').innerHTML = only.length ? only.slice(0, 10).map(t => { const short = t.type === 'ISSUE' && t.shortfall > 0; return '<tr><td>' + hms(t.time) + '</td><td>' + esc(t.facilityName) + '</td><td>' + esc(t.medicineName)
      + '</td><td>' + tag(short && t.quantity === 0 ? 'STOCK_OUT' : t.type) + (short ? ' short by ' + num(t.shortfall) : '') + '</td><td class="n">' + num(t.quantity) + '</td><td class="n">' + num(t.balanceAfter) + '</td></tr>'; }).join('')
      : none(6, 'No ledger entries in the latest movement for this selection.');
  }
  const pm = l.perMinute;
  chart('cLive', {n: pm.length, bars: true, series: [{values: pm.map(p => p.units), color: '#23395d', bars: true}],
    labels: pm.map((p, i) => ({i, text: p.minute})).filter((_, i) => i % 5 === 0 || i === pm.length - 1), tip: i => pm[i].minute + '  ' + num(pm[i].units) + ' units in ' + pm[i].transactions + ' issues'});
  const tf = $('todayFill'); if (tf) tf.textContent = l.fillRateTodayPct + '%';
}
function servicePanel() {
  return '<div class="cols c2">' + panel('Fill rate by day <span>(last 30 days and today)</span>', '<div class="pad"><div class="key"><span><i style="border-color:#2e7d32"></i>Units issued as % of units demanded</span></div><canvas id="cFill"></canvas></div>')
    + panel('Stock lines not fully supplied, by day <span>(last 30 days)</span>', '<div class="pad"><div class="key"><span><i class="b" style="border-color:#b3261e"></i>Facility-drug lines short on the day</span></div><canvas id="cShort"></canvas></div>') + '</div>';
}
function serviceCharts() {
  const d = S.analytics.days, l = S.live; if (!d) return;
  const fill = d.map(x => x.fillRatePct).concat(l && l.demandedUnitsToday ? [l.fillRateTodayPct] : []), names = d.map(x => dm(x.date)).concat(['today']);
  chart('cFill', {n: fill.length, zoom: true, max: 100, unit: '%', series: [{values: fill, color: '#2e7d32', dots: true, fill: 'rgba(46,125,50,.10)'}],
    labels: names.slice(0, fill.length).map((t, i) => ({i, text: t})).filter((_, i) => i % 5 === 0), tip: i => names[i] + '  fill rate ' + fill[i] + '%' + (d[i] ? ', ' + num(d[i].unmetUnits) + ' units not supplied' : '')});
  chart('cShort', {n: d.length, bars: true, series: [{values: d.map(x => x.linesShort), color: '#b3261e', bars: true}],
    labels: d.map((x, i) => ({i, text: dm(x.date)})).filter((_, i) => i % 5 === 0), tip: i => dm(d[i].date) + '  ' + d[i].linesShort + ' lines short, ' + num(d[i].unmetUnits) + ' units not supplied'});
}

// ------------------------------------------------------------------ Dashboard
function statusCounts(rows) { const c = {STOCKED_OUT: 0, UNDERSTOCKED: 0, ADEQUATE: 0, OVERSTOCKED: 0, UNKNOWN: 0}; rows.forEach(r => { c[r.stockStatus]++; }); return c; }
function mixBar(c) { const t = sum(Object.values(c)) || 1; return '<div class="mix">' + Object.keys(c).map(k => c[k] ? '<i class="' + k + '" style="width:' + (c[k] / t * 100) + '%" title="' + STATUS[k] + ': ' + c[k] + '"></i>' : '').join('') + '</div>'; }
function dashboardPage() {
  const o = S.overview, a = S.analytics, c = statusCounts(o.risks), due = o.risks.filter(r => r.atRiskInHorizon), pending = o.transfers.filter(t => t.status === 'PROPOSED');
  let h = '<h1>District dashboard: ' + esc(S.district) + '</h1><p class="lead">' + o.retrieval.facilities + ' facilities, ' + o.retrieval.medicines + ' drugs, ' + o.risks.length + ' stock lines. Forecast base date ' + dmy(o.asOf) + '.</p>';
  h += '<div class="strip"><div class="o"><b>' + c.STOCKED_OUT + '</b><span>lines stocked out</span></div><div class="u"><b>' + c.UNDERSTOCKED + '</b><span>lines understocked</span></div><div class="k"><b>' + c.ADEQUATE + '</b><span>adequately stocked</span></div>'
    + '<div class="v"><b>' + c.OVERSTOCKED + '</b><span>overstocked</span></div><div class="o"><b>' + due.length + '</b><span>stock-out due in ' + S.horizon + ' days</span></div><div><b>' + pending.length + '</b><span>transfers awaiting approval</span></div>'
    + '<div><b>' + num(a.unitsAtExpiryRisk) + '</b><span>units likely to expire unused</span></div><div class="k"><b id="todayFill">' + (S.live ? S.live.fillRateTodayPct + '%' : '-') + '</b><span>fill rate today</span></div></div>';

  h += panel('Query', '<form class="pad filters" data-form="ask" style="grid-template-columns:1fr auto"><label><span>Ask about stock-out risk by drug, district and period</span><input type="text" name="q" value="' + esc(S.ask.text) + '"></label><div class="act"><button class="go">Run query</button></div></form>' + askResult());

  const byMed = {}; o.risks.forEach(r => { (byMed[r.medicineId] = byMed[r.medicineId] || []).push(r); });
  h += '<div class="cols c32">' + panel('Stock status by drug <span>(number of facilities)</span>', '<div class="scroll"><table><thead><tr><th>Drug</th><th class="n">SOH</th><th class="n">AMC</th><th class="n">MOS</th><th class="n">Out</th><th class="n">Under</th><th class="n">Adequate</th><th class="n">Over</th><th>Distribution</th><th></th></tr></thead><tbody>'
    + S.meta.medicines.filter(x => byMed[x.id]).map(x => { const rows = byMed[x.id], s = statusCounts(rows), soh = sum(rows.map(r => r.currentStock)), amc = sum(rows.map(r => r.avgMonthlyConsumption));
      return '<tr><td>' + esc(x.name) + '</td><td class="n">' + num(soh) + '</td><td class="n">' + num(amc) + '</td><td class="n">' + (amc ? one(soh / amc) : '-') + '</td><td class="n">' + s.STOCKED_OUT + '</td><td class="n">' + s.UNDERSTOCKED
        + '</td><td class="n">' + s.ADEQUATE + '</td><td class="n">' + s.OVERSTOCKED + '</td><td>' + mixBar(s) + '</td><td><button class="lk" data-act="open" data-med="' + x.id + '" data-fac="">Open</button></td></tr>'; }).join('')
    + '</tbody></table></div><div class="pad note">SOH: usable stock on hand. AMC: average monthly consumption over the last 90 days. MOS: months of stock (SOH / AMC). Understocked is below ' + S.meta.riskBands.MIN_STOCK_DAYS + ' days of stock, overstocked above ' + S.meta.riskBands.MAX_STOCK_DAYS + ' days.</div>')
    + panel('Projected stock-outs <span>(earliest first)</span>', '<div class="scroll tall"><table><thead><tr><th>Facility</th><th>Drug</th><th class="n">SOH</th><th class="n">Days</th><th>Stock-out on</th><th>Risk</th><th></th></tr></thead><tbody>'
      + (o.risks.filter(r => r.riskLevel !== 'LOW').slice(0, 12).map(r => '<tr><td>' + esc(r.facilityName) + '</td><td>' + esc(r.medicineName) + '</td><td class="n">' + num(r.currentStock) + '</td><td class="n">' + days(r.daysToStockout) + '</td><td>' + dm(r.stockoutDate)
        + '</td><td>' + rk(r.riskLevel) + '</td><td><button class="lk" data-act="open" data-med="' + r.medicineId + '" data-fac="' + r.facilityId + '">Open</button></td></tr>').join('') || none(7, 'No stock line is below ' + S.meta.riskBands.MODERATE + ' days of stock.')) + '</tbody></table></div>') + '</div>';
  return h + livePanel() + servicePanel();
}
function askResult() {
  const r = S.ask.result; if (!r) return '';
  return '<div class="pad" style="padding-top:0"><table class="kv"><tbody>' + r.pipeline.map(p => '<tr><th>' + esc(p.step) + '</th><td style="font-weight:normal">' + esc(p.detail) + '</td></tr>').join('')
    + '<tr><th>Result</th><td>' + esc(r.answer) + '</td></tr></tbody></table>' + (r.findings.length ? '<ul class="basis" style="margin-top:8px">' + r.findings.map(f => '<li>' + esc(f) + '</li>').join('') + '</ul>' : '')
    + '<div class="acts"><a href="#/redistribution">Open transfer advice</a> <a href="#/forecast">Open forecast and risk</a></div></div>';
}

// ------------------------------------------------------------------ Stock Status
function searchPage() {
  const d = S.search.data, m = med(S.search.medicineId);
  if (!d || !m) return '<h1>Stock status by facility</h1><p class="pad">Loading...</p>';
  const rows = d.risks, unit = m.unit, sel = rows.find(r => String(r.facilityId) === String(S.search.facilityId)) || rows[0], c = statusCounts(rows);
  let h = '<h1>Stock status by facility</h1><p class="lead">Stock on hand, consumption, forecast and stock-out risk for one drug across the district.</p>';
  h += panel('Selection', '<div class="pad filters"><label><span>Drug</span><select id="sMed">' + opts(S.meta.medicines, S.search.medicineId, x => x.name, x => x.id) + '</select></label>'
    + '<label><span>District</span><select id="sDist"' + (isAdmin() ? '' : ' disabled') + '>' + opts(S.meta.districts, S.district) + '</select></label>'
    + '<label><span>Facility (optional)</span><select id="sFac"><option value="">All facilities</option>' + opts(myFacilities(), S.search.facilityId, f => f.name, f => f.id) + '</select></label>'
    + '<div class="act"><button class="go" data-act="analyse">Show stock position</button></div></div>');
  h += '<div class="strip"><div><b>' + rows.length + '</b><span>facilities in ' + esc(d.interpreted.district) + '</span></div><div class="o"><b>' + rows.filter(r => r.atRiskInHorizon).length + '</b><span>stock-out due in ' + d.interpreted.horizonDays + ' days</span></div>'
    + '<div><b>' + num(sum(rows.map(r => r.currentStock))) + '</b><span>' + esc(unit) + ' on hand</span></div><div><b>' + num(Math.round(sum(rows.map(r => r.recentDailyDemand)))) + '</b><span>' + esc(unit) + ' issued per day (14-day avg)</span></div>'
    + '<div class="o"><b>' + c.STOCKED_OUT + '</b><span>stocked out</span></div><div class="u"><b>' + c.UNDERSTOCKED + '</b><span>understocked</span></div><div class="k"><b>' + c.ADEQUATE + '</b><span>adequate</span></div><div class="v"><b>' + c.OVERSTOCKED + '</b><span>overstocked</span></div></div>';
  if (!sel) return h + '<p class="pad">No facilities in this district.</p>';

  h += '<div class="cols c32">' + panel(esc(m.name) + ' <span>(' + esc(d.interpreted.district) + ', ' + esc(unit) + ')</span>', '<div class="scroll tall"><table><thead><tr><th>Facility</th><th class="n">SOH</th><th class="n">AMC</th><th class="n">MOS</th><th class="n">Forecast<small> /day</small></th><th class="n">Days of stock</th><th>Stock status</th><th>Risk</th></tr></thead><tbody>'
    + rows.map(r => '<tr class="pick' + (r === sel ? ' sel' : '') + '" data-act="pick" data-id="' + r.facilityId + '"><td>' + esc(r.facilityName) + '</td><td class="n">' + num(r.currentStock) + '</td><td class="n">' + num(r.avgMonthlyConsumption) + '</td><td class="n">' + (r.monthsOfStock == null ? '-' : r.monthsOfStock)
      + '</td><td class="n">' + one(r.forecastDailyDemand) + '</td><td class="n">' + days(r.daysToStockout) + '</td><td>' + st(r.stockStatus) + '</td><td>' + rk(r.riskLevel) + '</td></tr>').join('') + '</tbody></table></div>');
  const tabs = [['batches', 'Batches'], ['history', 'Consumption'], ['forecast', 'Forecast'], ['risk', 'Risk basis']];
  h += '<section class="panel"><h2><div>' + esc(sel.facilityName) + '</div>' + rk(sel.riskLevel) + '</h2><div class="tabs">' + tabs.map(t => '<button data-act="tab" data-id="' + t[0] + '" class="' + (S.search.tab === t[0] ? 'on' : '') + '">' + t[1] + '</button>').join('') + '</div>' + facilityTab(sel, unit, d.asOf) + '</section></div>';

  const last30 = mean(sel.history.slice(-30)), change = last30 > 0 ? (sel.forecastDailyDemand - last30) / last30 * 100 : 0;
  h += '<div class="cols c334">' + panel('Demand forecast', '<div class="pad"><table class="kv"><tbody><tr><th>Forecast, next ' + d.interpreted.horizonDays + ' days</th><td>' + one(sel.forecastDailyDemand) + ' ' + esc(unit) + '/day</td></tr><tr><th>Against last 30 days</th><td>' + (change >= 0 ? '+' : '') + Math.round(change)
    + '%</td></tr><tr><th>Back-test error (14 days)</th><td>' + (sel.backtestErrorPct >= 0 ? sel.backtestErrorPct + '%' : 'n/a') + '</td></tr></tbody></table><div class="key" style="padding-top:8px"><span><i style="border-color:#1d5fa8"></i>Issued (weekly avg)</span><span><i class="d" style="border-color:#b3261e"></i>Forecast</span>'
    + (sel.linkedDisease ? '<span><i class="d" style="border-color:#2e7d32"></i>' + esc(sel.linkedDisease.toLowerCase()) + ' cases (scaled)</span>' : '') + '</div><canvas id="cDemand"></canvas></div>');
  h += panel('Stock-out risk', '<div class="pad"><table class="kv"><tbody><tr><th>Days of stock</th><td>' + days(sel.daysToStockout) + '</td></tr><tr><th>Projected stock-out</th><td>' + dmy(sel.stockoutDate) + '</td></tr><tr><th>Risk level</th><td>' + rk(sel.riskLevel)
    + '</td></tr><tr><th>Probability of stock-out in 14 days</th><td>' + sel.stockoutProbabilityPct + '%</td></tr><tr><th>Stock status</th><td>' + st(sel.stockStatus) + '</td></tr></tbody></table><p style="padding:8px 0 4px"><b>Basis</b></p><ul class="basis">'
    + sel.keyFactors.map(f => '<li>' + esc(f) + '</li>').join('') + '</ul></div>');
  const mine = d.transfers.filter(t => t.toFacilityId === sel.facilityId || t.fromFacilityId === sel.facilityId);
  h += panel('Transfer advice', '<div class="pad">' + (mine.length ? mine.map(adviceBlock).join('') : '<p>No transfer involves this facility for ' + esc(m.name) + '.</p><p class="note">' + esc(sel.suggestedAction) + '</p>') + '</div>') + '</div>';
  return h;
}
function facilityTab(r, unit, asOf) {
  if (S.search.tab === 'batches') {
    return '<div class="scroll"><table><thead><tr><th>Batch no.</th><th class="n">Qty</th><th>Mfg. date</th><th>Expiry date</th><th>Status</th></tr></thead><tbody>'
      + (r.batches.map(b => '<tr><td>' + esc(b.batchNo) + '</td><td class="n">' + num(b.quantity) + '</td><td>' + dmy(b.manufactureDate) + '</td><td>' + dmy(b.expiryDate) + '</td><td>' + tag(b.status) + '</td></tr>').join('') || none(5, 'No batches on record.'))
      + '</tbody></table></div><div class="pad note">Usable stock ' + num(r.currentStock) + ' ' + esc(unit) + '. Expired batches are excluded from stock on hand. Near expiry means 90 days or less.</div>';
  }
  if (S.search.tab === 'history') {
    return '<div class="pad"><table class="kv"><tbody><tr><th>Average per day, last 7 days</th><td>' + one(mean(r.history.slice(-7))) + '</td></tr><tr><th>Average per day, last 30 days</th><td>' + one(mean(r.history.slice(-30))) + '</td></tr><tr><th>Last 14 days against previous 14</th><td>'
      + (r.trendPct > 0 ? '+' : '') + r.trendPct + '%</td></tr></tbody></table><div class="key" style="padding-top:8px"><span><i class="b" style="border-color:#7fa3cf"></i>' + esc(unit) + ' issued per day, last 60 days to ' + dmy(addDays(asOf, -1)) + '</span></div><canvas id="cHistory"></canvas></div>';
  }
  if (S.search.tab === 'forecast') {
    const weeks = [0, 1, 2, 3].map(w => sum(r.forecast.slice(w * 7, w * 7 + 7)));
    return '<div class="pad"><div class="key"><span><i class="d" style="border-color:#b3261e"></i>Forecast ' + esc(unit) + ' per day, next 30 days</span></div><canvas id="cForecast"></canvas></div><div class="scroll"><table><thead><tr><th>Week from</th>' + weeks.map((_, i) => '<th class="n">' + dm(addDays(asOf, i * 7)) + '</th>').join('')
      + '<th class="n">30 days</th></tr></thead><tbody><tr><td>Forecast (' + esc(unit) + ')</td>' + weeks.map(w => '<td class="n">' + num(w) + '</td>').join('') + '<td class="n"><b>' + num(sum(r.forecast)) + '</b></td></tr></tbody></table></div>';
  }
  return '<div class="pad"><table class="kv"><tbody><tr><th>Risk score</th><td>' + r.riskScore + ' / 100</td></tr><tr><th>Units likely to expire unused</th><td>' + num(r.unitsAtExpiryRisk) + '</td></tr><tr><th>Advice</th><td>' + esc(r.suggestedAction) + '</td></tr></tbody></table><ul class="basis" style="margin-top:8px">'
    + r.keyFactors.map(f => '<li>' + esc(f) + '</li>').join('') + '</ul></div>';
}
function adviceBlock(t) {
  return '<div class="advice"><div class="h"><span>Transfer ' + num(t.quantity) + ' ' + esc(t.unit) + ' of ' + esc(t.medicineName) + '</span><span>' + rk(t.urgency) + ' ' + tag(t.status) + '</span></div><div class="b"><table><thead><tr><th></th><th>Facility</th><th class="n">SOH now</th><th class="n">Days</th><th class="n">SOH after</th><th class="n">Days</th></tr></thead><tbody>'
    + '<tr><td>From</td><td>' + esc(t.fromName) + '</td><td class="n">' + num(t.fromStockBefore) + '</td><td class="n">' + one(t.donorDaysBefore) + '</td><td class="n">' + num(t.fromStockAfter) + '</td><td class="n">' + one(t.donorDaysAfter) + '</td></tr>'
    + '<tr><td>To</td><td>' + esc(t.toName) + '</td><td class="n">' + num(t.toStockBefore) + '</td><td class="n">' + one(t.receiverDaysBefore) + '</td><td class="n">' + num(t.toStockAfter) + '</td><td class="n">' + one(t.receiverDaysAfter) + '</td></tr></tbody></table>'
    + '<p style="padding:8px 0 4px"><b>Basis</b> (distance ' + one(t.distanceKm) + ' km)</p><ul class="basis">' + t.keyFactors.map(f => '<li>' + esc(f) + '</li>').join('') + t.batches.map(b => '<li>Batch to issue: ' + esc(b) + '</li>').join('') + '</ul>'
    + (t.status === 'PROPOSED' ? '<div class="acts"><button class="go" data-act="accept" data-id="' + t.id + '">Approve and issue transfer</button><button class="no" data-act="reject" data-id="' + t.id + '">Reject</button></div>' : '') + '</div></div>';
}
function searchCharts() {
  const d = S.search.data; if (!d) return;
  const sel = d.risks.find(r => String(r.facilityId) === String(S.search.facilityId)) || d.risks[0]; if (!sel) return;
  const W = 14, hist = weekly(sel.history, W), fc = [0, 1, 2, 3].map(w => mean(sel.forecast.slice(w * 7, w * 7 + 7))), n = W + 4, series = [];
  const cases = sel.linkedDisease && d.caseTrends[sel.linkedDisease];
  if (cases) { const cw = weekly(cases, W), k = mean(hist) / Math.max(mean(cw), 1e-9); series.push({values: cw.map(v => v * k), color: '#2e7d32', dashed: true}); }
  series.push({values: hist, color: '#1d5fa8', dots: true, fill: 'rgba(29,95,168,.10)'});
  series.push({values: Array(W - 1).fill(null).concat([hist[W - 1]], fc), color: '#b3261e', dashed: true, dots: true});
  const label = i => dm(addDays(d.asOf, (i - W) * 7));
  chart('cDemand', {n, series, labels: [0, 4, 8, 12, 16].map(i => ({i, text: label(i)})), tip: i => 'Week from ' + label(i) + '  ' + one(i < W ? hist[i] : fc[i - W]) + ' ' + sel.unit + '/day' + (i < W ? '' : ' (forecast)')});
  if (S.search.tab === 'history') { const h60 = sel.history.slice(-60); chart('cHistory', {n: 60, bars: true, series: [{values: h60, color: '#7fa3cf', bars: true}], labels: [0, 15, 30, 45, 59].map(i => ({i, text: dm(addDays(d.asOf, i - 60))})), tip: i => dm(addDays(d.asOf, i - 60)) + '  ' + num(h60[i]) + ' ' + sel.unit}); }
  if (S.search.tab === 'forecast') chart('cForecast', {n: 30, series: [{values: sel.forecast, color: '#b3261e', dashed: true, fill: 'rgba(179,38,30,.07)'}], labels: [0, 7, 14, 21, 29].map(i => ({i, text: dm(addDays(d.asOf, i))})),
    marker: sel.daysToStockout != null && sel.daysToStockout <= 29 ? {i: sel.daysToStockout, text: 'stock-out'} : null, tip: i => dm(addDays(d.asOf, i)) + '  ' + num(sel.forecast[i]) + ' ' + sel.unit});
}

// ------------------------------------------------------------------ Forecast & Risk
function forecastPage() {
  const o = S.overview, rows = o.risks.filter(r => (S.fr.level === 'ALL' || r.riskLevel === S.fr.level) && (!S.fr.medicineId || String(r.medicineId) === String(S.fr.medicineId)));
  return '<h1>Forecast and stock-out risk</h1><p class="lead">All stock lines in ' + esc(S.district) + ', earliest projected stock-out first.</p>'
    + panel('Selection', '<div class="pad filters"><label><span>Drug</span><select id="fMed"><option value="">All drugs</option>' + opts(S.meta.medicines, S.fr.medicineId, x => x.name, x => x.id) + '</select></label><label><span>Risk level</span><select id="fLevel">'
      + ['ALL', 'CRITICAL', 'HIGH', 'MODERATE', 'LOW'].map(l => '<option value="' + l + '"' + (S.fr.level === l ? ' selected' : '') + '>' + (l === 'ALL' ? 'All (' + o.risks.length + ')' : RISK[l] + ' (' + o.riskCounts[l] + ')') + '</option>').join('') + '</select></label></div>')
    + panel('Stock lines <span>(' + rows.length + ')</span>', '<div class="scroll"><table><thead><tr><th>Facility</th><th>Drug</th><th class="n">SOH</th><th class="n">AMC</th><th class="n">Forecast<small> /day</small></th><th class="n">Trend</th><th class="n">Days of stock</th><th>Stock-out on</th><th class="n">Prob.<small> 14 d</small></th><th>Stock status</th><th>Risk</th><th>Advice</th><th></th></tr></thead><tbody>'
      + (rows.map(r => '<tr><td>' + esc(r.facilityName) + '</td><td>' + esc(r.medicineName) + '</td><td class="n">' + num(r.currentStock) + '</td><td class="n">' + num(r.avgMonthlyConsumption) + '</td><td class="n">' + one(r.forecastDailyDemand) + '</td><td class="n">' + (r.trendPct > 0 ? '+' : '') + r.trendPct + '%</td><td class="n">' + days(r.daysToStockout)
        + '</td><td>' + dmy(r.stockoutDate) + '</td><td class="n">' + r.stockoutProbabilityPct + '%</td><td>' + st(r.stockStatus) + '</td><td>' + rk(r.riskLevel) + '</td><td>' + esc(r.suggestedAction) + '</td><td><button class="lk" data-act="open" data-med="' + r.medicineId + '" data-fac="' + r.facilityId + '">Open</button></td></tr>').join('') || none(13, 'No stock lines match the selection.'))
      + '</tbody></table></div>')
    + panel('Expiry watch <span>(batches likely to expire before they are used)</span>', '<div class="scroll"><table><thead><tr><th>Facility</th><th>Drug</th><th>Batch no.</th><th>Expiry date</th><th class="n">Days left</th><th class="n">Batch qty</th><th class="n">Likely to expire</th><th class="n">Value (Rs)</th><th>Advice</th></tr></thead><tbody>'
      + (o.expiryAlerts.map(a => '<tr><td>' + esc(a.facilityName) + '</td><td>' + esc(a.medicineName) + '</td><td>' + esc(a.batchNo) + '</td><td>' + dmy(a.expiryDate) + '</td><td class="n">' + a.daysToExpiry + '</td><td class="n">' + num(a.batchQuantity) + '</td><td class="n"><b>' + num(a.unitsLikelyToExpire)
        + '</b></td><td class="n">' + num(a.valueAtRisk) + '</td><td>' + esc(a.action) + '</td></tr>').join('') || none(9, 'No batch is forecast to expire before it is used.')) + '</tbody></table></div>');
}

// ------------------------------------------------------------------ Stores & Ledger
function batchStatus(b, asOf) { const left = Math.round((new Date(b.expiryDate) - new Date(asOf)) / 864e5); return left <= 0 ? 'EXPIRED' : left <= 90 ? 'EXPIRING_SOON' : 'USABLE'; }
function inventoryPage() {
  const asOf = S.overview.asOf, list = S.inv.batches.filter(b => b.quantity > 0 && (!S.inv.medicineId || String(b.medicineId) === String(S.inv.medicineId)));
  const usable = sum(list.filter(b => batchStatus(b, asOf) !== 'EXPIRED').map(b => b.quantity)), mo = opts(S.meta.medicines, S.inv.medicineId, x => x.name, x => x.id);
  return '<h1>Stores and stock ledger</h1><p class="lead">Batch-wise stock at a facility. Enter receipts and issues here; every entry is written to the ledger.</p>'
    + panel('Selection', '<div class="pad filters"><label><span>Facility</span><select id="iFac">' + opts(myFacilities(), S.inv.facilityId, f => f.name, f => f.id) + '</select></label><label><span>Drug</span><select id="iMed"><option value="">All drugs</option>' + mo + '</select></label></div>')
    + panel('Batches on hand <span>(' + list.length + ' batches, ' + num(usable) + ' usable units)</span>', '<div class="scroll tall"><table><thead><tr><th>Drug</th><th>Batch no.</th><th class="n">Qty</th><th>Mfg. date</th><th>Expiry date</th><th>Received on</th><th>Status</th></tr></thead><tbody>'
      + (list.map(b => '<tr><td>' + esc((med(b.medicineId) || {}).name || b.medicineId) + '</td><td>' + esc(b.batchNo) + '</td><td class="n">' + num(b.quantity) + '</td><td>' + dmy(b.manufactureDate) + '</td><td>' + dmy(b.expiryDate) + '</td><td>' + dmy(b.receivedDate) + '</td><td>' + tag(batchStatus(b, asOf)) + '</td></tr>').join('') || none(7, 'No batches for this selection.'))
      + '</tbody></table></div>')
    + '<div class="cols c2">' + panel('Receipt entry', '<form class="pad filters" data-form="batch"><label><span>Drug</span><select name="medicineId">' + mo + '</select></label><label><span>Batch no.</span><input type="text" name="batchNo" required></label><label><span>Quantity</span><input type="number" name="quantity" min="1" required></label>'
      + '<label><span>Mfg. date</span><input type="date" name="manufactureDate"></label><label><span>Expiry date</span><input type="date" name="expiryDate" required></label><div class="act"><button class="go">Save receipt</button></div></form>')
    + panel('Issue entry', '<form class="pad filters" data-form="consumption"><label><span>Drug</span><select name="medicineId">' + mo + '</select></label><label><span>Quantity demanded</span><input type="number" name="demanded" min="0" required></label><label><span>Quantity issued</span><input type="number" name="dispensed" min="0" required></label>'
      + '<div class="act"><button class="go">Save issue</button></div><p class="note" style="grid-column:1/-1">Issues are taken from the earliest-expiring batch first.</p></form>') + '</div>'
    + panel('Latest ledger entries for this facility <span id="liveNote"></span>', '<div class="scroll"><table><thead><tr><th>Time</th><th>Facility</th><th>Drug</th><th>Entry</th><th class="n">Qty</th><th class="n">Balance</th></tr></thead><tbody id="liveRows"></tbody></table></div>');
}
async function loadBatches() {
  const facs = myFacilities();
  if (!facs.find(f => String(f.id) === String(S.inv.facilityId))) S.inv.facilityId = facs.length ? facs[0].id : null;
  S.inv.batches = S.inv.facilityId ? await api('/api/inventory/batches?facilityId=' + S.inv.facilityId) : [];
}

// ------------------------------------------------------------------ Transfers & Indents
function transfersPage() {
  const o = S.overview, pending = o.transfers.filter(t => t.status === 'PROPOSED');
  return '<h1>Transfers and indents</h1><p class="lead">Facilities forecast to stock out within ' + S.horizon + ' days are topped up to 30 days of stock from the nearest facility with surplus. A donor keeps at least 45 days of its own requirement.</p>'
    + panel('Transfer advice awaiting approval <span>(' + pending.length + ')</span>', '<div class="pad">' + (pending.map(adviceBlock).join('') || '<p>No transfer is advised at present.</p>') + '</div>')
    + panel('Indent advice <span>(' + o.procurement.length + ' due within 3 weeks)</span>', '<div class="scroll"><table><thead><tr><th>Facility</th><th>Drug</th><th class="n">Indent qty</th><th>Indent by</th><th>Expected receipt</th><th class="n">Lead time</th><th class="n">Re-order level</th><th class="n">Safety stock</th><th class="n">Value (Rs)</th><th>Status</th><th>Basis</th></tr></thead><tbody>'
      + (o.procurement.map(r => '<tr><td>' + esc(r.facilityName) + '</td><td>' + esc(r.medicineName) + '</td><td class="n"><b>' + num(r.quantity) + '</b></td><td>' + dmy(r.orderBy) + '</td><td>' + dmy(r.expectedDelivery) + '</td><td class="n">' + r.leadTimeDays + ' d</td><td class="n">' + num(r.reorderPoint) + '</td><td class="n">' + num(r.safetyStock)
        + '</td><td class="n">' + num(r.estimatedCost) + '</td><td>' + tag(r.status) + '</td><td>' + esc(r.rationale) + '</td></tr>').join('') || none(11, 'No indent falls due in the next 3 weeks.')) + '</tbody></table></div>')
    + panel('Transfer register <span>(' + S.history.length + ')</span>', '<div class="scroll"><table><thead><tr><th class="n">No.</th><th>Drug</th><th>From</th><th>To</th><th class="n">Qty</th><th class="n">Distance</th><th>Status</th><th>Advised on</th><th>Decided on</th></tr></thead><tbody>'
      + (S.history.map(t => '<tr><td class="n">' + t.id + '</td><td>' + esc(t.medicineName) + '</td><td>' + esc(t.fromName) + '</td><td>' + esc(t.toName) + '</td><td class="n">' + num(t.quantity) + '</td><td class="n">' + one(t.distanceKm) + ' km</td><td>' + tag(t.status) + '</td><td>' + stamp(t.createdAt) + '</td><td>' + (t.decidedAt ? stamp(t.decidedAt) : '-') + '</td></tr>').join('')
        || none(9, 'No transfer has been advised yet.')) + '</tbody></table></div>');
}

// ------------------------------------------------------------------ Facilities, Drug Master
function facilitiesPage() {
  const list = S.facilities.filter(f => !S.fac.district || f.district === S.fac.district), risk = {};
  S.overview.risks.forEach(r => { if (r.riskLevel === 'CRITICAL' || r.riskLevel === 'HIGH') risk[r.facilityId] = (risk[r.facilityId] || 0) + 1; });
  return '<h1>Facility master</h1><p class="lead">Hospitals, community health centres and primary health centres on the system.</p>'
    + panel('Facilities <span>(' + list.length + ')</span>', '<div class="scroll"><table><thead><tr><th>Code</th><th>Facility</th><th>Type</th><th>District</th><th class="n">Latitude</th><th class="n">Longitude</th><th class="n">Lines at high or critical risk</th></tr></thead><tbody>'
      + list.map(f => '<tr><td>' + esc(f.code) + '</td><td>' + esc(f.name) + '</td><td>' + esc(f.type) + '</td><td>' + esc(f.district) + '</td><td class="n">' + f.latitude + '</td><td class="n">' + f.longitude + '</td><td class="n">' + (f.district === S.district ? (risk[f.id] || 0) : '-') + '</td></tr>').join('') + '</tbody></table></div>',
      isAdmin() ? '<select id="facDist" style="width:190px"><option value="">All districts</option>' + opts(S.meta.districts, S.fac.district) + '</select>' : '')
    + (isAdmin() ? panel('Add facility', '<form class="pad filters" data-form="facility"><label><span>Code</span><input type="text" name="code" required maxlength="12"></label><label><span>Facility name</span><input type="text" name="name" required></label><label><span>Type</span><select name="type"><option>PHC</option><option>CHC</option><option>GH</option></select></label>'
      + '<label><span>District</span><input type="text" name="district" required list="dl" value="' + esc(S.district) + '"><datalist id="dl">' + S.meta.districts.map(d => '<option value="' + esc(d) + '">').join('') + '</datalist></label><label><span>Latitude</span><input type="number" name="latitude" step="0.0001" min="-90" max="90" required></label>'
      + '<label><span>Longitude</span><input type="number" name="longitude" step="0.0001" min="-180" max="180" required></label><div class="act"><button class="go">Add facility</button></div></form>') : '<p class="note">Master data is maintained by the state administrator.</p>');
}
function medicinesPage() {
  const soh = {}, due = {};
  S.overview.risks.forEach(r => { soh[r.medicineId] = (soh[r.medicineId] || 0) + r.currentStock; if (r.atRiskInHorizon) due[r.medicineId] = (due[r.medicineId] || 0) + 1; });
  return '<h1>Drug master</h1><p class="lead">Essential drug list with unit, supplier lead time and the seasonal disease used in the forecast.</p>'
    + panel('Drugs <span>(' + S.meta.medicines.length + ')</span>', '<div class="scroll"><table><thead><tr><th>Code</th><th>Drug</th><th>Category</th><th>Unit</th><th>Seasonal disease</th><th class="n">Lead time</th><th class="n">Unit rate (Rs)</th><th class="n">SOH, ' + esc(S.district) + '</th><th class="n">Facilities due to stock out</th><th></th></tr></thead><tbody>'
      + S.meta.medicines.map(m => '<tr><td>' + esc(m.code) + '</td><td>' + esc(m.name) + '</td><td>' + esc(m.category) + '</td><td>' + esc(m.unit) + '</td><td>' + esc(m.linkedDisease ? m.linkedDisease.charAt(0) + m.linkedDisease.slice(1).toLowerCase() : '-') + '</td><td class="n">' + m.leadTimeDays + ' d</td><td class="n">' + m.unitCost
        + '</td><td class="n">' + num(soh[m.id]) + '</td><td class="n">' + (due[m.id] || 0) + '</td><td><button class="lk" data-act="open" data-med="' + m.id + '" data-fac="">Open</button></td></tr>').join('') + '</tbody></table></div>')
    + (isAdmin() ? panel('Add drug', '<form class="pad filters" data-form="medicine"><label><span>Code</span><input type="text" name="code" required maxlength="8"></label><label><span>Drug name and strength</span><input type="text" name="name" required></label><label><span>Category</span><input type="text" name="category"></label>'
      + '<label><span>Unit</span><input type="text" name="unit" required value="tablets"></label><label><span>Seasonal disease</span><select name="linkedDisease"><option value="">None</option><option>DENGUE</option><option>DIARRHOEA</option><option>ARI</option><option>MALARIA</option></select></label>'
      + '<label><span>Lead time (days)</span><input type="number" name="leadTimeDays" min="1" max="120" value="14" required></label><label><span>Unit rate (Rs)</span><input type="number" name="unitCost" min="0" step="0.01" value="1" required></label><div class="act"><button class="go">Add drug</button></div></form>')
      : '<p class="note">Master data is maintained by the state administrator.</p>');
}

// ------------------------------------------------------------------ Analytics, Reports, Settings
function analyticsPage() {
  const a = S.analytics, rc = a.recommendationCounts;
  return '<h1>Analytics: ' + esc(a.district) + '</h1><p class="lead">Service level, wastage and the outcome of transfer advice.</p>'
    + '<div class="strip"><div class="k"><b>' + a.fillRate30Pct + '%</b><span>fill rate, 30 days</span></div><div class="k"><b>' + a.fillRate90Pct + '%</b><span>fill rate, 90 days</span></div><div class="o"><b>' + a.stockoutRate30Pct + '%</b><span>line-days short, 30 days</span></div>'
    + '<div><b>' + num(a.unmetUnits30) + '</b><span>units not supplied, 30 days</span></div><div class="o"><b>' + num(a.expiredUnitsOnHand) + '</b><span>expired units on shelf</span></div><div><b>Rs ' + num(a.expiredValue) + '</b><span>value of expired stock</span></div>'
    + '<div class="u"><b>' + num(a.unitsAtExpiryRisk) + '</b><span>units likely to expire</span></div><div><b>' + num(a.unitsRedistributed) + '</b><span>units transferred on advice</span></div></div>'
    + servicePanel() + '<div class="cols c32">' + livePanel()
    + panel('Outcome of transfer advice', '<div class="scroll"><table><thead><tr><th>Status</th><th class="n">Count</th></tr></thead><tbody>' + Object.keys(rc).map(k => '<tr><td>' + tag(k) + '</td><td class="n">' + rc[k] + '</td></tr>').join('')
      + '<tr><td>Approved, of those decided</td><td class="n">' + a.acceptanceRatePct + '%</td></tr></tbody></table></div>') + '</div>'
    + panel('Facility performance <span>(last 30 days)</span>', '<div class="scroll"><table><thead><tr><th>Facility</th><th class="n">Fill rate</th><th class="n">Line-days short</th><th class="n">Lines at high or critical risk</th><th class="n">Units likely to expire</th></tr></thead><tbody>'
      + a.facilities.map(f => '<tr><td>' + esc(f.name) + '</td><td class="n">' + f.fillRatePct + '%</td><td class="n">' + f.stockoutDays + '</td><td class="n">' + f.criticalItems + '</td><td class="n">' + num(f.unitsAtExpiryRisk) + '</td></tr>').join('') + '</tbody></table></div>');
}
const REPORTS = {
  risk: ['Stock status and stock-out risk', 'One row per facility and drug.', ['Facility', 'Drug', 'Unit', 'SOH', 'AMC', 'MOS', 'Forecast per day', 'Trend %', 'Days of stock', 'Stock-out date', 'Probability % (14 days)', 'Stock status', 'Risk level', 'Advice'],
    () => S.overview.risks.map(r => [r.facilityName, r.medicineName, r.unit, r.currentStock, r.avgMonthlyConsumption, r.monthsOfStock ?? '', r.forecastDailyDemand, r.trendPct, r.daysToStockout ?? '180+', r.stockoutDate ?? '', r.stockoutProbabilityPct, STATUS[r.stockStatus], r.riskLevel, r.suggestedAction])],
  transfers: ['Transfer register', 'All transfer advice with status and basis.', ['No.', 'Drug', 'From', 'To', 'Qty', 'Unit', 'Distance km', 'Urgency', 'Status', 'Advised on', 'Decided on', 'Basis'],
    () => S.history.map(t => [t.id, t.medicineName, t.fromName, t.toName, t.quantity, t.unit, t.distanceKm, t.urgency, t.status, t.createdAt, t.decidedAt ?? '', t.rationale])],
  expiry: ['Expiry watch', 'Batches forecast to expire before they are used.', ['Facility', 'Drug', 'Batch', 'Expiry date', 'Days left', 'Batch qty', 'Units likely to expire', 'Value Rs', 'Advice'],
    () => S.overview.expiryAlerts.map(a => [a.facilityName, a.medicineName, a.batchNo, a.expiryDate, a.daysToExpiry, a.batchQuantity, a.unitsLikelyToExpire, a.valueAtRisk, a.action])],
  indents: ['Indent advice', 'Re-order quantities and dates.', ['Facility', 'Drug', 'Indent qty', 'Unit', 'Indent by', 'Expected receipt', 'Lead time days', 'Re-order level', 'Safety stock', 'Value Rs', 'Status', 'Basis'],
    () => S.overview.procurement.map(r => [r.facilityName, r.medicineName, r.quantity, r.unit, r.orderBy, r.expectedDelivery, r.leadTimeDays, r.reorderPoint, r.safetyStock, r.estimatedCost, r.status, r.rationale])],
  facilities: ['Facility performance', 'Fill rate and shortages per facility, last 30 days.', ['Code', 'Facility', 'Fill rate %', 'Line-days short', 'Lines at high or critical risk', 'Units likely to expire'],
    () => S.analytics.facilities.map(f => [f.code, f.name, f.fillRatePct, f.stockoutDays, f.criticalItems, f.unitsAtExpiryRisk])],
  daily: ['Daily service level', 'Fill rate and lines short by day, last 30 days.', ['Date', 'Fill rate %', 'Lines short', 'Units issued', 'Units not supplied'],
    () => S.analytics.days.map(d => [d.date, d.fillRatePct, d.linesShort, d.issuedUnits, d.unmetUnits])]
};
function reportsPage() {
  return '<h1>Reports</h1><p class="lead">Current position for ' + esc(S.district) + '. CSV files open in Excel.</p>' + panel('Available reports', '<div class="scroll"><table><thead><tr><th>Report</th><th>Contents</th><th class="n">Rows</th><th></th></tr></thead><tbody>'
    + Object.keys(REPORTS).map(k => '<tr><td>' + REPORTS[k][0] + '</td><td>' + REPORTS[k][1] + '</td><td class="n">' + REPORTS[k][3]().length + '</td><td><button data-act="csv" data-id="' + k + '">Download CSV</button></td></tr>').join('')
    + '</tbody></table></div><div class="pad"><button data-act="print">Print this page</button></div>');
}
function downloadCsv(key) {
  const r = REPORTS[key], cell = v => '"' + String(v ?? '').replace(/"/g, '""') + '"';
  const csv = [r[2]].concat(r[3]()).map(row => row.map(cell).join(',')).join('\r\n'), a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob(['﻿' + csv], {type: 'text/csv;charset=utf-8'}));
  a.download = 'medichain-' + key + '-' + S.district.toLowerCase() + '-' + S.overview.asOf + '.csv';
  document.body.appendChild(a); a.click(); a.remove();
}
function settingsPage() {
  const b = S.meta.riskBands, hl = S.health, s = S.session, up = v => v ? '<span class="st ' + (v === 'UP' ? 'ADEQUATE' : 'STOCKED_OUT') + '"><i></i>' + esc(v) + '</span>' : 'checking';
  return '<h1>Settings</h1><p class="lead">Session, service status and the rules used in the analysis.</p><div class="cols c2">'
    + panel('Session', '<table class="kv"><tbody><tr><th>User</th><td>' + esc(s.displayName) + ' (' + esc(s.username) + ')</td></tr><tr><th>Office</th><td>' + esc(s.designation) + '</td></tr><tr><th>Access</th><td>' + (isAdmin() ? 'All districts' : esc(s.homeDistrict) + ' district') + '</td></tr><tr><th>Signed in</th><td>'
      + stamp(s.signedInAt) + '</td></tr><tr><th>Session ends</th><td>' + stamp(s.expiresAt) + '</td></tr></tbody></table><form class="pad filters" data-form="settings"><label><span>Risk horizon (days)</span><input type="number" name="horizon" min="3" max="60" value="' + S.horizon + '" required></label><div class="act"><button class="go">Save</button></div></form>')
    + panel('Services', '<table class="kv"><tbody><tr><th>forecast-service (8082)</th><td>' + up(hl && hl.forecastService) + '</td></tr><tr><th>inventory-service (8081)</th><td>' + up(hl && hl.inventoryService) + '</td></tr><tr><th>Dispensing feed</th><td>'
      + (S.live && S.live.feedEnabled ? 'Simulated, ' + S.live.feedSpeed + 'x real time' : 'Off') + '</td></tr><tr><th>Consumption history used</th><td>' + S.overview.retrieval.historyDays + ' days</td></tr></tbody></table><div class="pad"><button data-act="health">Check again</button></div>') + '</div>'
    + panel('Analysis rules', '<div class="scroll"><table><thead><tr><th>Rule</th><th>Value</th></tr></thead><tbody>' + [
      ['Stock status', 'Stocked out: SOH is zero. Understocked: under ' + b.MIN_STOCK_DAYS + ' days of stock. Adequately stocked: ' + b.MIN_STOCK_DAYS + ' to ' + b.MAX_STOCK_DAYS + ' days. Overstocked: over ' + b.MAX_STOCK_DAYS + ' days.'],
      ['Stock-out risk', 'Critical: ' + b.CRITICAL + ' days of stock or less. High: up to ' + b.HIGH + ' days. Moderate: up to ' + b.MODERATE + ' days. Low: more than ' + b.MODERATE + ' days.'],
      ['Demand forecast', 'Damped-trend Holt exponential smoothing with weekday pattern, fitted per stock line; adjusted for district disease case trend.'],
      ['Days of stock', 'Day-by-day simulation, earliest-expiring batch issued first, expired batches written off.'],
      ['Transfer advice', 'Receiver topped up to 30 days; donor keeps 45 days; batches with under 21 days shelf life are not moved; 120 km limit.'],
      ['Indent advice', 'Re-order level = lead-time demand + safety stock (95% service level). Indent covers lead time plus 30 days.'],
      ['Near expiry', 'Batch expiring in 90 days or less.']].map(r => '<tr><td>' + r[0] + '</td><td>' + r[1] + '</td></tr>').join('') + '</tbody></table></div>');
}

// ------------------------------------------------------------------ events
async function busy(fn, ok) {
  document.body.style.cursor = 'progress';
  try { await fn(); if (ok) toast(ok); } catch (e) { if (S.session) toast(e.message, true); }
  document.body.style.cursor = ''; render();
}
async function enter() {
  if (!S.session) return;
  try {
    const r = route();
    if (r === 'inventory') await loadBatches();
    if (r === 'redistribution' || r === 'reports') S.history = await api('/api/recommendations');
    if (r === 'settings') { S.health = null; api('/api/health').then(h => { S.health = h; if (route() === 'settings') render(); }).catch(() => {}); }
  } catch (e) { if (S.session) toast(e.message, true); }
  render(); if ($('main')) $('main').scrollTop = 0;
}
async function changeDistrict(value) {
  S.district = value; S.search.facilityId = ''; S.search.data = null; S.inv.facilityId = null; S.ask.result = null; S.overview = null; render();
  await busy(async () => { await refreshAll(); if (route() === 'inventory') await loadBatches(); });
}
document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el || !S.session) return;
  const act = el.dataset.act, id = el.dataset.id;
  if (act === 'signout') { api('/api/auth/logout', {method: 'POST'}).catch(() => {}).finally(() => signedOut('You have signed out.')); }
  else if (act === 'analyse') { const dist = $('sDist').value; S.search.medicineId = +$('sMed').value; S.search.facilityId = dist === S.district ? $('sFac').value : ''; if (dist !== S.district) changeDistrict(dist); else busy(loadSearch); }
  else if (act === 'pick') { S.search.facilityId = id; render(); }
  else if (act === 'tab') { S.search.tab = id; render(); }
  else if (act === 'open') { S.search.medicineId = +el.dataset.med; S.search.facilityId = el.dataset.fac; busy(loadSearch).then(() => { location.hash = '#/search'; }); }
  else if (act === 'accept' || act === 'reject') { el.disabled = true; busy(async () => { await post('/api/recommendations/' + id + '/' + act); await refreshAll(); S.history = await api('/api/recommendations'); }, act === 'accept' ? 'Transfer issued. Stock moved between facilities.' : 'Transfer advice rejected.'); }
  else if (act === 'csv') downloadCsv(id);
  else if (act === 'print') window.print();
  else if (act === 'health') enter();
});
document.addEventListener('change', e => {
  const t = e.target; if (!S.session) return;
  if (t.id === 'gDistrict') changeDistrict(t.value);
  else if (t.id === 'iFac') { S.inv.facilityId = t.value; busy(loadBatches); }
  else if (t.id === 'iMed') { S.inv.medicineId = t.value; render(); }
  else if (t.id === 'fMed') { S.fr.medicineId = t.value; render(); }
  else if (t.id === 'fLevel') { S.fr.level = t.value; render(); }
  else if (t.id === 'facDist') { S.fac.district = t.value; render(); }
  else if (t.id === 'sDist' && t.value !== S.district) { $('sFac').innerHTML = '<option value="">All facilities</option>' + opts(S.facilities.filter(f => f.district === t.value), '', f => f.name, f => f.id); }
});
document.addEventListener('submit', e => {
  const form = e.target.closest('[data-form]'); if (!form) return;
  e.preventDefault();
  const v = Object.fromEntries(new FormData(form).entries()), kind = form.dataset.form;
  if (kind === 'ask') { S.ask.text = v.q; busy(async () => { S.ask.result = await post('/api/query', {query: v.q, district: S.district}); const dist = S.ask.result.interpreted.district; if (dist !== S.district) S.district = dist; await refreshAll(); }); }
  else if (kind === 'settings') { S.horizon = Math.max(3, Math.min(60, +v.horizon || 10)); store.set('mc.horizon', S.horizon); busy(refreshAll, 'Saved.'); }
  else if (kind === 'facility') busy(async () => { await post('/api/inventory/facilities', {code: v.code.trim(), name: v.name.trim(), type: v.type, district: v.district.trim(), latitude: +v.latitude, longitude: +v.longitude}); await loadMeta(); await refreshAll(); }, 'Facility added.');
  else if (kind === 'medicine') busy(async () => { await post('/api/inventory/medicines', {code: v.code.trim().toUpperCase(), name: v.name.trim(), category: v.category.trim(), unit: v.unit.trim(), aliases: v.name.trim().split(' ')[0].toLowerCase(), linkedDisease: v.linkedDisease || null, leadTimeDays: +v.leadTimeDays, unitCost: +v.unitCost}); await loadMeta(); await refreshAll(); }, 'Drug added.');
  else if (kind === 'batch') busy(async () => { await post('/api/inventory/batches', {facilityId: +S.inv.facilityId, medicineId: +v.medicineId, batchNo: v.batchNo, quantity: +v.quantity, manufactureDate: v.manufactureDate || null, expiryDate: v.expiryDate}); await loadBatches(); await refreshAll(); }, 'Receipt saved.');
  else if (kind === 'consumption') busy(async () => { await post('/api/inventory/consumption', {facilityId: +S.inv.facilityId, medicineId: +v.medicineId, demanded: +v.demanded, dispensed: +v.dispensed}); await loadBatches(); await refreshAll(); }, 'Issue saved.');
});
window.addEventListener('hashchange', enter);
window.addEventListener('resize', () => { if (S.session && S.overview) render(); });

// ------------------------------------------------------------------ refresh timers
const LIVE_MS = 5000, FULL_MS = 30000;
let timers = [];
function stopTimers() { timers.forEach(clearInterval); timers = []; }
const typing = () => { const a = document.activeElement; return a && ['INPUT', 'SELECT', 'TEXTAREA'].includes(a.tagName); };
function startTimers() {
  stopTimers();
  timers.push(setInterval(async () => {
    if (!S.session || document.hidden) return;
    try { S.live = await api('/api/live?' + qs({district: S.district, minutes: 30})); S.lastRefresh = S.live.serverTime; paintLive(); if (route() === 'dashboard' || route() === 'analytics') serviceCharts(); } catch (e) { /* the next manual action reports it */ }
  }, LIVE_MS));
  timers.push(setInterval(async () => {
    if (!S.session || document.hidden || !S.overview) return;
    try {
      await loadOverview(); if (route() === 'search') await loadSearch(); if (route() === 'inventory') await loadBatches();
      if (['dashboard', 'search', 'forecast', 'analytics', 'inventory'].includes(route()) && !typing()) render();
      else { const c = $('critCount'); if (c) c.textContent = S.overview.riskCounts.CRITICAL; }
    } catch (e) { /* keep the last figures */ }
  }, FULL_MS));
}

// ------------------------------------------------------------------ start
async function start() {
  S.district = S.session.workingDistrict; S.horizon = store.get('mc.horizon') || 10;
  $('app').innerHTML = '<p class="pad">Loading...</p>';
  try { await loadMeta(); render(); await refreshAll(); startTimers(); await enter(); }
  catch (e) { if (S.session) { $('app').innerHTML = '<div class="pad"><div class="warn">' + esc(e.message) + '</div><button onclick="location.reload()">Reload</button></div>'; } }
}
(async function init() {
  try { const res = await fetch('/api/auth/regions'); if (res.ok) S.regions = await res.json(); } catch (e) { /* services down: the sign-in page says so */ }
  const saved = store.get('mc.session');
  if (saved && saved.token) {
    S.session = saved;
    try { S.session = await api('/api/auth/me'); await start(); return; } catch (e) { S.session = null; store.set('mc.session', null); }
  }
  signinPage();
})();
