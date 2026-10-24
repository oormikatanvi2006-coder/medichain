/* MediChain web client. Plain JavaScript; Leaflet for the map. All figures are read from the REST API. */
'use strict';

// ------------------------------------------------------------------ team shown on the sign-in page
// Add an e-mail address to show a contact link, e.g. {name: 'Oormika', email: 'name@example.com'}.
const TEAM = [{name: 'Oormika', email: ''}, {name: 'Amrusha', email: ''}, {name: 'Hari', email: ''}, {name: 'Harrshan', email: ''}];
// Put the institution logo in the static folder with this name; nothing is shown if the file is missing.
const LOGO = '<img class="logo" src="srm-logo.png" alt="SRM logo" onerror="this.remove()">';

// ------------------------------------------------------------------ helpers
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const num = n => Number(n ?? 0).toLocaleString('en-IN');
const one = n => (Math.round(n * 10) / 10).toLocaleString('en-IN');
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const dmy = iso => { if (!iso) return '-'; const [y, m, d] = String(iso).slice(0, 10).split('-'); return +d + ' ' + MON[+m - 1] + ' ' + y; };
const dm = iso => { if (!iso) return '-'; const [, m, d] = String(iso).slice(0, 10).split('-'); return +d + ' ' + MON[+m - 1]; };
const hms = iso => iso ? String(iso).slice(11, 19) : '-';
const stamp = iso => iso ? dmy(iso) + ', ' + String(iso).slice(11, 16) : '-';
const addDays = (iso, n) => { const d = new Date(String(iso).slice(0, 10) + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const mean = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
const sum = a => a.reduce((x, y) => x + y, 0);
const initials = n => String(n).split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
const STATUS = {STOCKED_OUT: 'Stocked out', UNDERSTOCKED: 'Understocked', ADEQUATE: 'Adequate', OVERSTOCKED: 'Overstocked', UNKNOWN: 'No use recorded'};
const RISK = {CRITICAL: 'Critical', HIGH: 'High', MODERATE: 'Moderate', LOW: 'Low'};
const RISK_ORDER = ['CRITICAL', 'HIGH', 'MODERATE', 'LOW'];
const WORD = {EXPIRED: 'Expired', EXPIRING_SOON: 'Near expiry', USABLE: 'Usable', PROPOSED: 'Awaiting approval', EXECUTED: 'Approved', REJECTED: 'Rejected', SUPERSEDED: 'Replaced',
  URGENT: 'Urgent', ORDER_NOW: 'Indent now', ORDER_SOON: 'Indent soon', ISSUE: 'Issued', RECEIPT: 'Received', TRANSFER_IN: 'Transfer in', TRANSFER_OUT: 'Transfer out', STOCK_OUT: 'Not supplied'};
const st = code => '<span class="st ' + esc(code) + '"><i></i>' + esc(STATUS[code] || code) + '</span>';
const rk = code => '<span class="rk ' + esc(code) + '">' + esc(RISK[code] || code) + '</span>';
const tag = code => '<span class="tag ' + esc(code) + '">' + esc(WORD[code] || code) + '</span>';
const days = d => d == null ? '180+' : one(d);
const opts = (list, sel, label, value) => list.map(o => { const v = value ? value(o) : o; return '<option value="' + esc(v) + '"' + (String(v) === String(sel) ? ' selected' : '') + '>' + esc(label ? label(o) : o) + '</option>'; }).join('');
const panel = (title, body, extra) => '<section class="panel"><h2><div>' + title + '</div>' + (extra || '') + '</h2>' + body + '</section>';
const kvRows = list => list.map(f => { const i = f.indexOf(': '); return i < 0 ? '<tr><td colspan="2">' + esc(f) + '</td></tr>' : '<tr><th>' + esc(f.slice(0, i)) + '</th><td>' + esc(f.slice(i + 2)) + '</td></tr>'; }).join('');
const none = (cols, text) => '<tr><td class="none" colspan="' + cols + '">' + esc(text) + '</td></tr>';
const tabs = (act, list, on) => '<div class="tabs">' + list.map(t => '<button data-act="' + act + '" data-id="' + t[0] + '" class="' + (on === t[0] ? 'on' : '') + '">' + t[1] + '</button>').join('') + '</div>';
const head = (title, sub, tools) => '<div class="head"><div><h1>' + title + '</h1>' + (sub ? '<p>' + sub + '</p>' : '') + '</div>' + (tools ? '<div class="tools">' + tools + '</div>' : '') + '</div>';
const fname = n => String(n).replace(/^Government General Hospital/, 'GGH').replace(/^Government Hospital/, 'GH');
const shortName = n => { const p = String(n).split(' – '); return p.length > 1 ? p[1] : n; };

const ICON = {
  home: '<path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  pill: '<path d="M10.5 3.5a5 5 0 0 1 7 7l-7 7a5 5 0 0 1-7-7z"/><path d="M7 7l7 7"/>',
  alert: '<path d="M12 3l9.5 17h-19z"/><path d="M12 10v4M12 17.5v.01"/>',
  truck: '<path d="M2 6h12v10H2zM14 10h4l3 3v3h-7z"/><circle cx="6" cy="18" r="1.8"/><circle cx="17" cy="18" r="1.8"/>',
  sliders: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  box: '<path d="M3 7l9-4 9 4v10l-9 4-9-4z"/><path d="M3 7l9 4 9-4M12 11v10"/>',
  chart: '<path d="M4 20V11M10 20V5M16 20v-6M2 20h20"/>',
  cog: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  cross: '<path d="M9 3h6v6h6v6h-6v6H9v-6H3V9h6z"/>',
  ask: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  share: '<path d="M4 12h12M12 6l6 6-6 6"/>'
};
const icon = (name, size) => '<svg class="i" viewBox="0 0 24 24"' + (size ? ' style="width:' + size + 'px;height:' + size + 'px"' : '') + ' aria-hidden="true">' + ICON[name] + '</svg>';

function toast(message, isError) {
  const t = $('toast'); t.textContent = message; t.className = isError ? 'err' : ''; t.style.display = 'block';
  clearTimeout(toast.timer); toast.timer = setTimeout(() => { t.style.display = 'none'; }, isError ? 7000 : 3500);
}

// ------------------------------------------------------------------ state and API
const S = {
  session: null, regions: null, meta: null, district: null, horizon: 10, facilities: [],
  overview: null, analytics: null, live: null, lastRefresh: null, lastSeen: null,
  search: {medicineId: null, facilityId: '', data: null, tab: 'summary'},
  ask: {text: 'Which facilities will run out of paracetamol and ORS in the next 10 days, and where can stock come from?', result: null},
  inv: {facilityId: null, medicineId: '', batches: []}, sc: {casePct: 50, demandPct: 0, disease: '', medicineId: '', result: null},
  fr: {level: 'ALL', medicineId: '', tab: 'risk'}, tr: {tab: 'approve'}, adm: {tab: 'facilities', district: ''}, mapMed: '', mapView: null, popupOpen: false,
  history: [], health: null
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
  if (res.status === 401 && S.session) { signedOut('Your session has ended. Please sign in again.'); throw new Error('Session ended'); }
  if (!res.ok) throw new Error(body.message || body.error || 'Request failed (' + res.status + ')');
  return body;
}
const post = (path, body) => api(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body || {})});
const qs = p => Object.entries(p).filter(([, v]) => v !== null && v !== undefined && v !== '').map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');
const med = id => S.meta.medicines.find(m => m.id === +id);
const myFacilities = () => S.facilities.filter(f => f.district === S.district);
const isAdmin = () => S.session && S.session.role === 'STATE_ADMIN';
const pendingTransfers = () => S.overview ? S.overview.transfers.filter(t => t.status === 'PROPOSED') : [];

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
  const g = c.getContext('2d'); g.scale(ratio, ratio); g.font = '11px "Segoe UI", Arial, sans-serif';
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
    g.strokeStyle = '#edf0f3'; g.beginPath(); g.moveTo(pad.l, y(v)); g.lineTo(w - pad.r, y(v)); g.stroke();
    g.fillStyle = '#5d6b7a'; g.fillText((Math.round(v * 10) / 10) + (cfg.unit || ''), pad.l - 6, y(v) + 4);
  }
  g.textAlign = 'center'; g.fillStyle = '#5d6b7a';
  (cfg.labels || []).forEach(l => g.fillText(l.text, Math.min(Math.max(x(l.i), pad.l + 16), w - pad.r - 16), h - 6));
  if (cfg.marker) {
    const mx = x(cfg.marker.i); g.strokeStyle = '#c0362c'; g.lineWidth = 1.5; g.beginPath(); g.moveTo(mx, pad.t); g.lineTo(mx, h - pad.b); g.stroke();
    g.fillStyle = '#c0362c'; g.textAlign = mx > w - 90 ? 'right' : 'left'; g.fillText(cfg.marker.text, mx + (g.textAlign === 'right' ? -4 : 4), pad.t + 10);
  }
  cfg.series.forEach(s => {
    const pts = s.values.map((v, i) => v == null ? null : [x(i), y(v)]).filter(Boolean);
    if (!pts.length) return;
    if (s.bars) { g.fillStyle = s.color; const bw = Math.max(2, band * .66); pts.forEach(p => { const hh = Math.max(0, h - pad.b - p[1]); g.beginPath(); g.roundRect ? g.roundRect(p[0] - bw / 2, p[1], bw, hh, [2, 2, 0, 0]) : g.rect(p[0] - bw / 2, p[1], bw, hh); g.fill(); }); return; }
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
    t.textContent = text; t.style.display = 'block'; t.style.left = Math.min(e.clientX + 12, window.innerWidth - t.offsetWidth - 8) + 'px'; t.style.top = (e.clientY - 30) + 'px';
  };
  c.onmouseleave = () => { $('tip').style.display = 'none'; };
}
function nice(raw) { const p = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-9)))), f = raw / p; return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p; }
const weekly = (arr, weeks) => { const out = []; for (let w = weeks; w >= 1; w--) out.push(mean(arr.slice(arr.length - w * 7, arr.length - (w - 1) * 7))); return out; };

// ------------------------------------------------------------------ sign-in
function signinPage(message) {
  const r = S.regions, last = store.get('mc.lastDistrict') || 'Vellore';
  const feat = (ic, title, text) => '<div class="feat"><div class="ic">' + icon(ic) + '</div><div><b>' + title + '</b><span>' + text + '</span></div></div>';
  $('app').innerHTML = '<div class="signin"><section class="hero">'
    + '<div class="brand"><span class="mark">' + icon('cross', 18) + '</span><div>MediChain<small>Drug stock monitoring system</small></div></div>'
    + '<div><h1>Know which medicines will run out, before they do.</h1><p class="tagline">MediChain forecasts stock at every health facility in a district and shows where medicines can be moved from.</p></div>'
    + '<div class="feats">' + feat('clock', 'Predict', 'Days of stock left for every medicine at every facility.') + feat('truck', 'Redistribute', 'Transfers from nearby surplus, sending stock that would expire first.')
    + feat('ask', 'Explain', 'Every suggestion shows the figures behind it.') + '</div>'
    + '<div class="team"><h3>Questions? Contact the team</h3><p>MediChain project team</p><ul>'
    + TEAM.map(t => '<li><span class="avatar">' + initials(t.name) + '</span><div>' + esc(t.name) + (t.email ? '<a href="mailto:' + esc(t.email) + '">' + esc(t.email) + '</a>' : '') + '</div></li>').join('')
    + '</ul></div></section>'
    + '<section class="side"><div class="corner">' + LOGO + '</div><div class="card"><h2>Sign in</h2><p>' + (r ? esc(r.stateName) + ' &middot; ' + r.districts.length + ' districts' : 'Drug stock monitoring') + '</p>'
    + '<form id="loginForm"><label><span>User ID</span><input type="text" name="username" autocomplete="username" required autofocus></label>'
    + '<label><span>Password</span><input type="password" name="password" autocomplete="current-password" required></label>'
    + '<label><span>District</span><select name="district" required>' + (r ? opts(r.districts, last) : '') + '</select></label>'
    + '<div class="err" id="loginErr">' + esc(message || (r ? '' : 'The services are not reachable yet. Reload the page in a minute.')) + '</div><button class="go">Sign in</button></form>'
    + '<div class="demo"><p>No account? Try the demo as:</p><div class="chips"><button type="button" class="chip" data-demo="admin">State administrator</button><button type="button" class="chip" data-demo="district">District pharmacist</button></div></div></div>'
    + '<div class="fine">Proof of concept &middot; simulated data &middot; not an official government system</div></section></div>';
  const form = $('loginForm');
  form.onsubmit = async e => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(form).entries()), btn = form.querySelector('button');
    btn.disabled = true; $('loginErr').textContent = '';
    try {
      const res = await fetch('/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(v)});
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || 'Sign-in failed (' + res.status + ')');
      S.session = body; store.set('mc.session', body); store.set('mc.lastDistrict', body.workingDistrict);
      await start();
    } catch (err) { $('loginErr').textContent = err.message; btn.disabled = false; }
  };
  document.querySelectorAll('[data-demo]').forEach(b => { b.onclick = () => {
    const d = form.district.value || 'Vellore';
    if (b.dataset.demo === 'admin') { form.username.value = 'admin'; form.password.value = 'Admin@123'; }
    else { form.username.value = 'dp.' + d.toLowerCase(); form.password.value = d + '@123'; }
    form.requestSubmit();
  }; });
}
function signedOut(message) {
  S.session = null; S.overview = null; S.search.data = null; store.set('mc.session', null); stopTimers(); dropMap();
  signinPage(message);
}

// ------------------------------------------------------------------ frame
const NAV = [['dashboard', 'Overview', 'home'], ['search', 'Medicines', 'pill'], ['forecast', 'Risk register', 'alert'], ['redistribution', 'Transfers & indents', 'truck'],
  ['scenario', 'Scenario planning', 'sliders'], ['inventory', 'Stores & ledger', 'box'], ['analytics', 'Analytics & reports', 'chart'], ['admin', 'Administration', 'cog']];
const PAGES = {dashboard: dashboardPage, search: medicinesPage, forecast: riskPage, redistribution: transfersPage, scenario: scenarioPage, inventory: storesPage, analytics: analyticsPage, admin: adminPage};
const route = () => { const r = location.hash.replace('#/', ''); return PAGES[r] ? r : 'dashboard'; };

function frame() {
  const s = S.session, r = route(), o = S.overview, critical = o ? o.riskCounts.CRITICAL : 0, pend = pendingTransfers().length, on = S.live && S.live.feedEnabled;
  const badge = {forecast: critical, redistribution: pend};
  $('app').innerHTML = '<div class="frame"><header class="top"><div class="brand"><span class="mark">' + icon('cross', 18) + '</span><div>MediChain<small>' + esc(s.stateName) + '</small></div></div>'
    + (isAdmin() ? '<select id="gDistrict" aria-label="District">' + opts(S.meta.districts, S.district) + '</select>' : '<span class="pill">' + esc(S.district) + ' district</span>')
    + '<div class="grow"></div><span class="pill' + (on ? '' : ' off') + '" title="Stock figures refresh by themselves"><i class="dot"></i>' + (on ? 'Live' : 'Feed off') + ' &middot; <span id="asOn">' + (S.lastRefresh ? hms(S.lastRefresh) : '-') + '</span></span>'
    + '<span class="user"><span class="avatar">' + initials(s.displayName) + '</span><span>' + esc(s.displayName) + '</span></span><button data-act="signout">Sign out</button>' + LOGO + '</header>'
    + '<nav class="nav">' + NAV.map(p => '<a href="#/' + p[0] + '" class="' + (p[0] === r ? 'on' : '') + '">' + icon(p[2]) + esc(p[1]) + (badge[p[0]] ? '<span class="n" id="nb-' + p[0] + '">' + badge[p[0]] + '</span>' : '') + '</a>').join('') + '</nav>'
    + '<main class="main" id="main"></main></div>';
}
function render() {
  if (!S.session || !S.meta) return;
  const mainScroll = $('main') ? $('main').scrollTop : 0, r = route();
  dropMap();
  frame();
  $('main').innerHTML = S.overview ? PAGES[r]() : '<p class="pad">Loading ' + esc(S.district) + '...</p>';
  $('main').scrollTop = mainScroll;
  if (!S.overview) return;
  if (r === 'dashboard') drawMap();
  if (r === 'search') searchCharts();
  if (r === 'analytics') serviceCharts();
  paintLive(true);
}

// ------------------------------------------------------------------ live movement
function livePanel() {
  return panel('Live stock movement <span id="liveNote"></span>', '<div class="mini" id="liveStrip"></div><div class="cols c2" style="margin:0"><div class="pad"><div class="key"><span><i class="b" style="border-color:#16677c"></i>Units issued per minute, last 30 minutes</span></div>'
    + '<canvas id="cLive" style="height:170px"></canvas></div><ul class="feed" id="liveFeed"></ul></div>');
}
function paintLive(first) {
  const l = S.live; if (!l) return;
  if ($('asOn')) $('asOn').textContent = hms(l.serverTime);
  if ($('liveNote')) $('liveNote').textContent = l.feedEnabled ? '(simulated dispensing, ' + l.feedSpeed + 'x speed)' : '(dispensing feed off)';
  if ($('liveStrip')) {
    $('liveStrip').innerHTML = '<div><b>' + num(l.issuedUnitsToday) + '</b>units issued today</div><div><b>' + num(l.issueCountToday) + '</b>issue entries</div>'
      + '<div><b>' + l.fillRateTodayPct + '%</b>fill rate today</div><div><b' + (l.shortEventsToday ? ' style="color:var(--out)"' : '') + '>' + num(l.shortEventsToday) + '</b>not fully supplied</div>'
      + '<div><b>' + num(l.receivedUnitsToday) + '</b>units received</div><div><b>' + num(l.transferredUnitsToday) + '</b>units transferred in</div>';
  }
  const fresh = t => !first && S.lastSeen && t.time > S.lastSeen;
  if ($('liveFeed')) {
    $('liveFeed').innerHTML = l.recent.slice(0, 12).map(t => { const short = t.type === 'ISSUE' && t.shortfall > 0;
      return '<li class="' + (fresh(t) ? 'new' : '') + '"><time>' + hms(t.time) + '</time><div><b>' + esc(shortName(t.facilityName)) + '</b> &middot; ' + esc(t.medicineName) + '</div><span class="q">' + num(t.quantity) + ' ' + tag(short && t.quantity === 0 ? 'STOCK_OUT' : t.type) + '</span></li>'; }).join('')
      || '<li><span></span><span class="muted">No movement yet today.</span></li>';
  }
  if ($('liveRows')) {
    const only = l.recent.filter(t => String(t.facilityId) === String(S.inv.facilityId));
    $('liveRows').innerHTML = only.length ? only.slice(0, 12).map(t => { const short = t.type === 'ISSUE' && t.shortfall > 0; return '<tr><td>' + hms(t.time) + '</td><td>' + esc(t.medicineName)
      + '</td><td>' + tag(short && t.quantity === 0 ? 'STOCK_OUT' : t.type) + (short ? ' <span class="muted">short by ' + num(t.shortfall) + '</span>' : '') + '</td><td class="n">' + num(t.quantity) + '</td><td class="n">' + num(t.balanceAfter) + '</td></tr>'; }).join('')
      : none(5, 'No ledger entries for this facility in the latest movement.');
  }
  if (!first && MAP) l.recent.filter(fresh).forEach(t => ping(t.facilityId));
  if (l.recent.length) S.lastSeen = l.recent.reduce((m, t) => t.time > m ? t.time : m, S.lastSeen || '');
  const pm = l.perMinute;
  chart('cLive', {n: pm.length, bars: true, series: [{values: pm.map(p => p.units), color: '#16677c', bars: true}],
    labels: pm.map((p, i) => ({i, text: p.minute})).filter((_, i) => i % 6 === 0 || i === pm.length - 1), tip: i => pm[i].minute + '  ' + num(pm[i].units) + ' units in ' + pm[i].transactions + ' issues'});
}

// ------------------------------------------------------------------ live map (Leaflet + OpenStreetMap, no API key)
const RISK_COLOR = {CRITICAL: '#c0362c', HIGH: '#d07a12', MODERATE: '#c9a400', LOW: '#2f8a3f'};
const STATUS_COLOR = {STOCKED_OUT: '#c0362c', UNDERSTOCKED: '#d07a12', ADEQUATE: '#2f8a3f', OVERSTOCKED: '#2766b0', UNKNOWN: '#6b7280'};
let MAP = null, MARK = {};
function dropMap() { if (MAP) { MAP.remove(); MAP = null; MARK = {}; S.popupOpen = false; } }
function mapPanel() {
  const meds = S.meta.medicines.filter(m => S.overview.risks.some(r => r.medicineId === m.id));
  const legend = S.mapMed ? Object.keys(STATUS_COLOR).filter(k => k !== 'UNKNOWN').map(k => '<span><i style="background:' + STATUS_COLOR[k] + '"></i>' + STATUS[k] + '</span>').join('')
    : RISK_ORDER.map(k => '<span><i style="background:' + RISK_COLOR[k] + '"></i>' + RISK[k] + ' risk</span>').join('');
  return panel('Facility map', '<div id="lmap" class="lmap"></div><div class="legend">' + legend + '<span><i class="ln"></i>Transfer advised</span><span>Larger dot = bigger hospital. A ring flashes when a facility issues stock.</span></div>',
    '<div class="mapbar"><select id="mapMed" aria-label="Colour the map by"><option value="">All medicines (worst risk)</option>' + opts(meds, S.mapMed, m => m.name, m => m.id) + '</select></div>');
}
function drawMap() {
  const el = $('lmap'); if (!el) return;
  if (!window.L) { el.innerHTML = '<div class="mapfail">The map could not be loaded.</div>'; return; }
  const o = S.overview, lines = {}, medId = S.mapMed ? +S.mapMed : null;
  o.risks.forEach(r => { if (!medId || r.medicineId === medId) (lines[r.facilityId] = lines[r.facilityId] || []).push(r); });
  const facs = myFacilities().filter(f => lines[f.id]);
  MAP = L.map(el, {scrollWheelZoom: false, zoomSnap: .5});
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(MAP);
  MARK = {};
  facs.forEach(f => {
    const ls = lines[f.id].slice().sort((a, b) => RISK_ORDER.indexOf(a.riskLevel) - RISK_ORDER.indexOf(b.riskLevel) || (a.daysToStockout ?? 999) - (b.daysToStockout ?? 999)), w = ls[0];
    const color = medId ? STATUS_COLOR[w.stockStatus] : RISK_COLOR[w.riskLevel], radius = f.type === 'GH' ? 12 : f.type === 'CHC' ? 9.5 : 7.5;
    const pop = '<div class="pop"><h3>' + esc(f.name) + '</h3><p>' + esc(f.type) + ' &middot; ' + esc(f.district) + '</p><table><tbody>'
      + ls.slice(0, 4).map(r => '<tr><td>' + esc(r.medicineName) + '</td><td class="n">' + days(r.daysToStockout) + ' d</td><td>' + rk(r.riskLevel) + '</td></tr>').join('') + '</tbody></table>'
      + '<div class="acts"><button class="go" data-act="open" data-med="' + w.medicineId + '" data-fac="' + f.id + '">Open stock details</button></div></div>';
    MARK[f.id] = L.circleMarker([f.latitude, f.longitude], {radius, color: '#fff', weight: 2, fillColor: color, fillOpacity: .92}).addTo(MAP)
      .bindPopup(pop).bindTooltip(shortName(f.name) + ': ' + (medId ? STATUS[w.stockStatus] : w.medicineName + ', ' + days(w.daysToStockout) + ' days left'));
  });
  const byId = {}; facs.forEach(f => { byId[f.id] = f; });
  pendingTransfers().filter(t => !medId || t.medicineName === (med(medId) || {}).name).forEach(t => {
    const a = byId[t.fromFacilityId], b = byId[t.toFacilityId]; if (!a || !b) return;
    const label = num(t.quantity) + ' ' + t.unit + ' of ' + t.medicineName + ': ' + shortName(t.fromName) + ' to ' + shortName(t.toName) + ', ' + one(t.distanceKm) + ' km';
    L.polyline([[a.latitude, a.longitude], [b.latitude, b.longitude]], {color: '#0f4c5c', weight: 2.5, dashArray: '7 6', opacity: .9}).addTo(MAP).bindTooltip(label);
    const lat = a.latitude + (b.latitude - a.latitude) * .62, lng = a.longitude + (b.longitude - a.longitude) * .62;
    const ang = Math.atan2(-(b.latitude - a.latitude), (b.longitude - a.longitude) * Math.cos(a.latitude * Math.PI / 180)) * 180 / Math.PI;
    L.marker([lat, lng], {interactive: false, icon: L.divIcon({className: '', iconSize: [16, 16], iconAnchor: [8, 8],
      html: '<svg width="16" height="16" viewBox="0 0 16 16" style="transform:rotate(' + ang + 'deg)"><path d="M2 2l12 6-12 6 3-6z" fill="#0f4c5c"/></svg>'})}).addTo(MAP);
  });
  const v = S.mapView;
  if (v && v.district === S.district) MAP.setView(v.c, v.z);
  else if (facs.length) MAP.fitBounds(facs.map(f => [f.latitude, f.longitude]), {padding: [36, 36], maxZoom: 12});
  else MAP.setView([11.1, 78.6], 7);
  MAP.on('moveend', () => { if (MAP) S.mapView = {district: S.district, c: MAP.getCenter(), z: MAP.getZoom()}; });
  MAP.on('popupopen', () => { S.popupOpen = true; }); MAP.on('popupclose', () => { S.popupOpen = false; });
}
function ping(facilityId) {
  const m = MARK[facilityId]; if (!m || !MAP) return;
  const p = L.circleMarker(m.getLatLng(), {radius: m.options.radius, color: m.options.fillColor, weight: 2, fill: false, className: 'ping', interactive: false}).addTo(MAP);
  setTimeout(() => { if (MAP) MAP.removeLayer(p); }, 1600);
}

// ------------------------------------------------------------------ Overview
const EXAMPLES = [['Paracetamol and ORS, 10 days', 'Which facilities will run out of paracetamol and ORS in the next 10 days, and where can stock come from?'],
  ['ORS, next 7 days', 'Which facilities will run out of ORS in the next 7 days?'], ['Amoxicillin, 14 days', 'Where is amoxicillin at risk of running out in the next 14 days?']];
function dashboardPage() {
  const o = S.overview, a = S.analytics, due = o.risks.filter(r => r.atRiskInHorizon), pend = pendingTransfers(), out = o.risks.filter(r => r.stockStatus === 'STOCKED_OUT').length;
  const kpi = (cls, ic, value, text, href, act) => '<a class="kpi ' + cls + '" href="' + href + '"' + (act || '') + '><div class="ic">' + icon(ic, 20) + '</div><div><b>' + value + '</b><span>' + text + '</span></div></a>';
  let h = head(esc(S.district) + ' district', o.retrieval.facilities + ' facilities &middot; ' + o.retrieval.medicines + ' medicines &middot; stock as on ' + dmy(o.asOf));
  h += '<div class="kpis">' + kpi('o', 'alert', due.length, 'stock-outs expected in ' + S.horizon + ' days', '#/forecast', ' data-act="frtab" data-id="risk"')
    + kpi('v', 'truck', pend.length, 'transfers waiting for approval', '#/redistribution', ' data-act="trtab" data-id="approve"')
    + kpi('u', 'box', out, 'medicine lines out of stock now', '#/forecast', ' data-act="frtab" data-id="risk"')
    + kpi('', 'clock', num(a.unitsAtExpiryRisk), 'units may expire before use', '#/forecast', ' data-act="frtab" data-id="expiry"') + '</div>';
  h += '<div class="ask"><h2>Ask about stock</h2><form data-form="ask"><input type="text" name="q" value="' + esc(S.ask.text) + '" aria-label="Question"><button class="go">Ask</button></form>'
    + '<div class="chips">' + EXAMPLES.map(e => '<button type="button" class="chip" data-act="example" data-q="' + esc(e[1]) + '">' + esc(e[0]) + '</button>').join('') + '</div>' + askResult() + '</div>';
  h += '<div class="cols c32">' + mapPanel() + attentionPanel() + '</div>' + livePanel();
  return h;
}
function attentionPanel() {
  const o = S.overview, pend = pendingTransfers(), list = o.risks.filter(r => r.riskLevel === 'CRITICAL' || r.riskLevel === 'HIGH').slice(0, 7);
  return panel('Needs attention <span>(' + list.length + ')</span>', '<ul class="att">' + (list.map(r => {
    const t = pend.find(x => x.toFacilityId === r.facilityId && x.medicineName === r.medicineName);
    return '<li><div class="d ' + r.riskLevel + '"><b>' + days(r.daysToStockout) + '</b><small>days left</small></div><div class="t"><b>' + esc(r.medicineName) + '</b><div>' + esc(r.facilityName) + '</div><div>'
      + (t ? 'Transfer of ' + num(t.quantity) + ' ' + esc(t.unit) + ' from ' + esc(shortName(t.fromName)) + ' is ready' : esc(r.suggestedAction)) + '</div></div>'
      + (t ? '<button class="go" data-act="trtab" data-id="approve" data-go="redistribution">Review</button>' : '<button data-act="open" data-med="' + r.medicineId + '" data-fac="' + r.facilityId + '">View</button>') + '</li>'; }).join('')
    || '<li><span class="muted">No medicine is at high or critical risk.</span></li>') + '</ul>');
}
function askResult() {
  const r = S.ask.result; if (!r) return '';
  return '<div class="answer"><div class="big">' + esc(r.answer) + '</div>' + (r.findings.length ? '<ol>' + r.findings.slice(0, 6).map(f => '<li>' + esc(f) + '</li>').join('') + '</ol>' : '')
    + '<div class="steps">' + r.pipeline.map(p => '<span><b>' + esc(p.step) + ':</b> ' + esc(p.detail) + '</span>').join('') + '</div>'
    + (pendingTransfers().length ? '<div class="acts" style="padding:10px 0 0"><button class="go" data-act="trtab" data-id="approve" data-go="redistribution">Review the transfers</button></div>' : '') + '</div>';
}

// ------------------------------------------------------------------ Medicines
function statusCounts(rows) { const c = {STOCKED_OUT: 0, UNDERSTOCKED: 0, ADEQUATE: 0, OVERSTOCKED: 0, UNKNOWN: 0}; rows.forEach(r => { c[r.stockStatus]++; }); return c; }
function mixBar(c) { const t = sum(Object.values(c)) || 1; return '<div class="mix">' + Object.keys(c).map(k => c[k] ? '<i class="' + k + '" style="width:' + (c[k] / t * 100) + '%" title="' + STATUS[k] + ': ' + c[k] + '"></i>' : '').join('') + '</div>'; }
function medicinesPage() {
  const o = S.overview, byMed = {};
  o.risks.forEach(r => { (byMed[r.medicineId] = byMed[r.medicineId] || []).push(r); });
  let h = head('Medicines', 'Choose a medicine to see its stock at every facility in ' + esc(S.district) + '.');
  h += '<div class="meds">' + S.meta.medicines.filter(m => byMed[m.id]).map(m => { const rows = byMed[m.id], dueN = rows.filter(r => r.atRiskInHorizon).length;
    return '<button class="med' + (String(m.id) === String(S.search.medicineId) ? ' on' : '') + '" data-act="med" data-id="' + m.id + '"><b>' + esc(m.name) + '</b>' + mixBar(statusCounts(rows))
      + '<small class="' + (dueN ? 'o' : '') + '">' + (dueN ? dueN + ' facilit' + (dueN === 1 ? 'y' : 'ies') + ' may run out in ' + S.horizon + ' days' : 'No stock-out expected') + '</small></button>'; }).join('') + '</div>';
  const d = S.search.data, m = med(S.search.medicineId);
  if (!d || !m) return h + '<p class="pad">Loading...</p>';
  const rows = d.risks, unit = m.unit, sel = rows.find(r => String(r.facilityId) === String(S.search.facilityId)) || rows[0];
  if (!sel) return h + '<p class="pad">No facilities in this district.</p>';
  h += '<div class="cols c2">' + panel(esc(m.name) + ' <span>by facility, ' + esc(unit) + '</span>', '<div class="scroll tall"><table><thead><tr><th>Facility</th><th class="n">In stock</th><th class="n">Use per day</th><th class="n">Days left</th><th>Status</th><th>Risk</th></tr></thead><tbody>'
    + rows.map(r => '<tr class="pick' + (r === sel ? ' sel' : '') + '" data-act="pick" data-id="' + r.facilityId + '"><td>' + esc(fname(r.facilityName)) + '</td><td class="n">' + num(r.currentStock) + '</td><td class="n">' + one(r.forecastDailyDemand)
      + '</td><td class="n">' + days(r.daysToStockout) + '</td><td>' + st(r.stockStatus) + '</td><td>' + rk(r.riskLevel) + '</td></tr>').join('') + '</tbody></table></div>');
  h += '<section class="panel"><h2><div>' + esc(sel.facilityName) + '</div>' + rk(sel.riskLevel) + '</h2>' + tabs('tab', [['summary', 'Summary'], ['demand', 'Demand'], ['batches', 'Batches']], S.search.tab) + facilityTab(sel, unit, d) + '</section></div>';
  return h;
}
function facilityTab(r, unit, d) {
  if (S.search.tab === 'batches') {
    return '<div class="scroll"><table><thead><tr><th>Batch</th><th class="n">Qty</th><th>Made</th><th>Expires</th><th>Status</th></tr></thead><tbody>'
      + (r.batches.map(b => '<tr><td>' + esc(b.batchNo) + '</td><td class="n">' + num(b.quantity) + '</td><td>' + dmy(b.manufactureDate) + '</td><td>' + dmy(b.expiryDate) + '</td><td>' + tag(b.status) + '</td></tr>').join('') || none(5, 'No batches on record.'))
      + '</tbody></table></div>';
  }
  if (S.search.tab === 'demand') {
    return '<div class="pad"><div class="key"><span><i style="border-color:#2766b0"></i>Issued (weekly average)</span><span><i class="d" style="border-color:#c0362c"></i>Forecast</span>'
      + (r.linkedDisease ? '<span><i class="d" style="border-color:#2f8a3f"></i>' + esc(r.linkedDisease.toLowerCase()) + ' cases (scaled)</span>' : '') + '</div><canvas id="cDemand" style="height:220px"></canvas>'
      + '<table class="kv" style="margin-top:10px"><tbody><tr><th>Average per day, last 7 days</th><td>' + one(mean(r.history.slice(-7))) + ' ' + esc(unit) + '</td></tr><tr><th>Average per day, last 30 days</th><td>' + one(mean(r.history.slice(-30))) + ' ' + esc(unit)
      + '</td></tr><tr><th>Last 14 days against the 14 before</th><td>' + (r.trendPct > 0 ? '+' : '') + r.trendPct + '%</td></tr></tbody></table></div>';
  }
  const mine = d.transfers.filter(t => t.toFacilityId === r.facilityId || t.fromFacilityId === r.facilityId);
  return '<div class="pad"><table class="kv"><tbody><tr><th>Days left</th><td>' + days(r.daysToStockout) + (r.stockoutDate ? ' (runs out ' + dmy(r.stockoutDate) + ')' : '') + '</td></tr><tr><th>Chance of running out in 14 days</th><td>' + r.stockoutProbabilityPct + '%</td></tr>'
    + '<tr><th>Status</th><td>' + st(r.stockStatus) + '</td></tr></tbody></table>'
    + (r.keyFactors.length ? '<h3 style="font-size:13.5px;margin:14px 0 4px">Why</h3><table class="kv"><tbody>' + kvRows(r.keyFactors) + '</tbody></table>' : '')
    + '<h3 style="font-size:13.5px;margin:14px 0 8px">What to do</h3>' + (mine.length ? mine.map(adviceBlock).join('') : '<p>' + esc(r.suggestedAction) + '</p>') + '</div>';
}
function searchCharts() {
  const d = S.search.data; if (!d || S.search.tab !== 'demand') return;
  const sel = d.risks.find(r => String(r.facilityId) === String(S.search.facilityId)) || d.risks[0]; if (!sel) return;
  const W = 14, hist = weekly(sel.history, W), fc = [0, 1, 2, 3].map(w => mean(sel.forecast.slice(w * 7, w * 7 + 7))), n = W + 4, series = [];
  const cases = sel.linkedDisease && d.caseTrends[sel.linkedDisease];
  if (cases) { const cw = weekly(cases, W), k = mean(hist) / Math.max(mean(cw), 1e-9); series.push({values: cw.map(v => v * k), color: '#2f8a3f', dashed: true}); }
  series.push({values: hist, color: '#2766b0', dots: true, fill: 'rgba(39,102,176,.10)'});
  series.push({values: Array(W - 1).fill(null).concat([hist[W - 1]], fc), color: '#c0362c', dashed: true, dots: true});
  const label = i => dm(addDays(d.asOf, (i - W) * 7));
  chart('cDemand', {n, series, labels: [0, 4, 8, 12, 16].map(i => ({i, text: label(i)})), marker: {i: W, text: 'today'},
    tip: i => 'Week from ' + label(i) + ': ' + one(i < W ? hist[i] : fc[i - W]) + ' ' + sel.unit + '/day' + (i < W ? '' : ' (forecast)')});
}

// ------------------------------------------------------------------ transfer card (used on Medicines and Transfers)
function adviceBlock(t) {
  const top = Math.max(30, t.donorDaysBefore || 0, t.receiverDaysAfter || 0), w = v => Math.min(100, Math.max(2, (v || 0) / top * 100));
  const fac = (role, name, before, after) => '<div class="fac"><small>' + role + '</small><b>' + esc(name) + '</b><small>' + one(before) + ' &rarr; ' + one(after) + ' days of stock</small>'
    + '<div class="bar"><i class="before" style="width:' + w(Math.max(before, after)) + '%"></i><i class="after" style="width:' + w(after) + '%"></i></div></div>';
  return '<div class="advice"><div class="h"><b>' + num(t.quantity) + ' ' + esc(t.unit) + ' of ' + esc(t.medicineName) + '</b><span>' + rk(t.urgency) + ' ' + tag(t.status) + '</span></div>'
    + '<div class="route">' + fac('From', t.fromName, t.donorDaysBefore, t.donorDaysAfter) + '<div class="arrow"><svg viewBox="0 0 40 16"><path d="M2 8h34M30 3l6 5-6 5" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>' + one(t.distanceKm) + ' km</div>'
    + fac('To', t.toName, t.receiverDaysBefore, t.receiverDaysAfter) + '</div>'
    + '<details><summary>Why this transfer?</summary><table class="kv"><tbody>' + kvRows(t.keyFactors.concat(t.batches.map(b => 'Batch to send: ' + b))) + '</tbody></table></details>'
    + (t.status === 'PROPOSED' ? '<div class="acts"><button class="go" data-act="accept" data-id="' + t.id + '">Approve transfer</button><button class="no" data-act="reject" data-id="' + t.id + '">Reject</button></div>' : '') + '</div>';
}

// ------------------------------------------------------------------ Risk register
function riskPage() {
  const o = S.overview, f = S.fr, medOk = r => !f.medicineId || String(r.medicineId) === String(f.medicineId);
  let h = head('Risk register', 'Every medicine at every facility, most urgent first.', '<select id="fMed" aria-label="Medicine"><option value="">All medicines</option>' + opts(S.meta.medicines, f.medicineId, x => x.name, x => x.id) + '</select>');
  const alerts = o.expiryAlerts.filter(medOk);
  h += '<section class="panel">' + tabs('frtab', [['risk', 'Stock-out risk'], ['expiry', 'Expiry watch (' + alerts.length + ')']], f.tab);
  if (f.tab === 'expiry') {
    return h + '<div class="scroll"><table><thead><tr><th>Facility</th><th>Medicine</th><th>Batch</th><th>Expires</th><th class="n">Days left</th><th class="n">Likely to expire</th><th class="n">Value (Rs)</th><th>Advice</th></tr></thead><tbody>'
      + (alerts.map(a => '<tr><td>' + esc(a.facilityName) + '</td><td>' + esc(a.medicineName) + '</td><td>' + esc(a.batchNo) + '</td><td>' + dmy(a.expiryDate) + '</td><td class="n">' + a.daysToExpiry + '</td><td class="n"><b>' + num(a.unitsLikelyToExpire)
        + '</b> of ' + num(a.batchQuantity) + '</td><td class="n">' + num(a.valueAtRisk) + '</td><td>' + esc(a.action) + '</td></tr>').join('') || none(8, 'No batch is expected to expire before it is used.')) + '</tbody></table></div></section>';
  }
  const base = o.risks.filter(medOk), rows = base.filter(r => f.level === 'ALL' || r.riskLevel === f.level), count = l => base.filter(r => r.riskLevel === l).length;
  h += '<div class="pad chips">' + [['ALL', 'All', base.length]].concat(RISK_ORDER.map(l => [l, RISK[l], count(l)])).map(c => '<button class="chip' + (f.level === c[0] ? ' on' : '') + '" data-act="level" data-id="' + c[0] + '">' + c[1] + '<i>' + c[2] + '</i></button>').join('') + '</div>';
  return h + '<div class="scroll"><table><thead><tr><th>Facility</th><th>Medicine</th><th class="n">In stock</th><th class="n">Use per day</th><th class="n">Days left</th><th>Runs out</th><th class="n">Chance in 14 d</th><th>Risk</th><th>Advice</th><th></th></tr></thead><tbody>'
    + (rows.map(r => '<tr><td>' + esc(fname(r.facilityName)) + '</td><td>' + esc(r.medicineName) + '</td><td class="n">' + num(r.currentStock) + '</td><td class="n">' + one(r.forecastDailyDemand) + '</td><td class="n"><b>' + days(r.daysToStockout) + '</b></td><td>' + dm(r.stockoutDate)
      + '</td><td class="n">' + r.stockoutProbabilityPct + '%</td><td>' + rk(r.riskLevel) + '</td><td>' + esc(r.suggestedAction) + '</td><td><button class="lk" data-act="open" data-med="' + r.medicineId + '" data-fac="' + r.facilityId + '">View</button></td></tr>').join('')
      || none(10, 'No medicine lines match.')) + '</tbody></table></div></section>';
}

// ------------------------------------------------------------------ Transfers & indents
function transfersPage() {
  const o = S.overview, pend = pendingTransfers(), tab = S.tr.tab;
  let h = head('Transfers & indents', 'Move stock between facilities first; raise an indent only where a transfer cannot cover the gap.');
  h += '<section class="panel">' + tabs('trtab', [['approve', 'To approve (' + pend.length + ')'], ['indents', 'Indents (' + o.procurement.length + ')'], ['history', 'History (' + S.history.length + ')']], tab);
  if (tab === 'indents') {
    return h + '<div class="scroll"><table><thead><tr><th>Facility</th><th>Medicine</th><th class="n">Quantity</th><th>Indent by</th><th>Arrives about</th><th class="n">Value (Rs)</th><th>Status</th><th>Basis</th></tr></thead><tbody>'
      + (o.procurement.map(r => '<tr><td>' + esc(r.facilityName) + '</td><td>' + esc(r.medicineName) + '</td><td class="n"><b>' + num(r.quantity) + '</b> ' + esc(r.unit) + '</td><td>' + dmy(r.orderBy) + '</td><td>' + dmy(r.expectedDelivery) + '</td><td class="n">' + num(r.estimatedCost)
        + '</td><td>' + tag(r.status) + '</td><td class="muted">' + esc(r.rationale) + '</td></tr>').join('') || none(8, 'No indent is due in the next 3 weeks.')) + '</tbody></table></div></section>';
  }
  if (tab === 'history') {
    return h + '<div class="scroll"><table><thead><tr><th class="n">No.</th><th>Medicine</th><th>From</th><th>To</th><th class="n">Quantity</th><th class="n">Distance</th><th>Status</th><th>Advised</th><th>Decided</th></tr></thead><tbody>'
      + (S.history.map(t => '<tr><td class="n">' + t.id + '</td><td>' + esc(t.medicineName) + '</td><td>' + esc(t.fromName) + '</td><td>' + esc(t.toName) + '</td><td class="n">' + num(t.quantity) + '</td><td class="n">' + one(t.distanceKm) + ' km</td><td>' + tag(t.status)
        + '</td><td>' + stamp(t.createdAt) + '</td><td>' + (t.decidedAt ? stamp(t.decidedAt) : '-') + '</td></tr>').join('') || none(9, 'No transfer has been advised yet.')) + '</tbody></table></div></section>';
  }
  return h + '<div class="pad">' + (pend.map(adviceBlock).join('') || '<p class="muted">Nothing to approve. Every facility has enough stock or no donor is in reach.</p>') + '</div></section>';
}

// ------------------------------------------------------------------ Scenario planning
function scenarioPage() {
  const c = S.sc, r = c.result, sign = v => (v > 0 ? '+' : '') + v;
  let h = head('Scenario planning', 'Try "what if" changes in disease cases or demand. Nothing is saved.');
  h += panel('Scenario', '<form class="pad filters" data-form="scenario"><label><span>Medicine</span><select name="medicineId"><option value="">All medicines</option>' + opts(S.meta.medicines, c.medicineId, x => x.name, x => x.id) + '</select></label>'
    + '<label><span>Disease</span><select name="disease">' + [['', 'All diseases'], ['DENGUE', 'Dengue'], ['DIARRHOEA', 'Diarrhoea'], ['ARI', 'Respiratory infection'], ['MALARIA', 'Malaria']].map(x => '<option value="' + x[0] + '"' + (c.disease === x[0] ? ' selected' : '') + '>' + x[1] + '</option>').join('') + '</select></label>'
    + '<label><span>Cases this week: <b id="scCase">' + sign(c.casePct) + '%</b></span><input type="range" name="casePct" min="-50" max="200" step="10" value="' + c.casePct + '" data-out="scCase"></label>'
    + '<label><span>Demand: <b id="scDemand">' + sign(c.demandPct) + '%</b></span><input type="range" name="demandPct" min="-30" max="60" step="5" value="' + c.demandPct + '" data-out="scDemand"></label>'
    + '<div class="act"><button class="go">Run scenario</button></div></form>');
  if (!r) return h;
  const rows = [['Stock-outs expected in ' + r.horizonDays + ' days', 'linesDueInHorizon'], ['Lines at critical risk', 'critical'], ['Lines at high risk', 'high'], ['Transfers needed', 'transfers'], ['Units to transfer', 'transferUnits'],
    ['Lines with no donor in reach', 'linesWithoutDonor'], ['Indents due in 3 weeks', 'reorders'], ['Expected use, next 30 days', 'forecastUnits30Days'], ['Units that may expire', 'unitsAtExpiryRisk']];
  h += '<div class="cols c2">' + panel('Today against the scenario', '<div class="scroll"><table><thead><tr><th>Measure</th><th class="n">Today</th><th class="n">Scenario</th><th class="n">Change</th></tr></thead><tbody>' + rows.map(x => { const b = r.baseline[x[1]], s = r.scenario[x[1]], d = s - b, good = x[1] === 'unitsAtExpiryRisk' ? d > 0 : d < 0;
      return '<tr><td>' + x[0] + '</td><td class="n">' + num(b) + '</td><td class="n"><b>' + num(s) + '</b></td><td class="n">' + (d === 0 ? '0' : '<span class="delta ' + (x[1] === 'forecastUnits30Days' ? '' : good ? 'down' : 'up') + '">' + (d > 0 ? '+' : '') + num(d) + '</span>') + '</td></tr>'; }).join('') + '</tbody></table></div>')
    + panel('Transfers needed in the scenario <span>(' + r.scenarioTransfers.length + ')</span>', '<div class="scroll tall"><table><thead><tr><th>Medicine</th><th>From</th><th>To</th><th class="n">Quantity</th><th>Risk</th></tr></thead><tbody>'
      + (r.scenarioTransfers.map(t => '<tr><td>' + esc(t.medicineName) + '</td><td>' + esc(shortName(t.fromName)) + '</td><td>' + esc(shortName(t.toName)) + '</td><td class="n">' + num(t.quantity) + '</td><td>' + rk(t.urgency) + '</td></tr>').join('') || none(5, 'No transfer would be needed.')) + '</tbody></table></div>') + '</div>';
  h += panel('Lines that change <span>(' + r.changes.length + ')</span>', '<div class="scroll"><table><thead><tr><th>Facility</th><th>Medicine</th><th class="n">Use per day now</th><th class="n">In scenario</th><th class="n">Days left now</th><th class="n">In scenario</th><th>Risk now</th><th>In scenario</th></tr></thead><tbody>'
    + (r.changes.map(x => '<tr><td>' + esc(x.facilityName) + '</td><td>' + esc(x.medicineName) + '</td><td class="n">' + one(x.baseForecastPerDay) + '</td><td class="n">' + one(x.scenarioForecastPerDay) + '</td><td class="n">' + days(x.baseDays) + '</td><td class="n"><b>' + days(x.scenarioDays)
      + '</b></td><td>' + rk(x.baseRisk) + '</td><td>' + rk(x.scenarioRisk) + '</td></tr>').join('') || none(8, 'No line changes risk or days left in this scenario.')) + '</tbody></table></div>');
  return h;
}

// ------------------------------------------------------------------ Stores & ledger
function batchStatus(b, asOf) { const left = Math.round((new Date(b.expiryDate) - new Date(asOf)) / 864e5); return left <= 0 ? 'EXPIRED' : left <= 90 ? 'EXPIRING_SOON' : 'USABLE'; }
function storesPage() {
  const asOf = S.overview.asOf, list = S.inv.batches.filter(b => b.quantity > 0 && (!S.inv.medicineId || String(b.medicineId) === String(S.inv.medicineId)));
  const usable = sum(list.filter(b => batchStatus(b, asOf) !== 'EXPIRED').map(b => b.quantity)), mo = opts(S.meta.medicines, S.inv.medicineId, x => x.name, x => x.id);
  return head('Stores & ledger', 'Batches held at a facility, and every receipt and issue.', '<select id="iFac" aria-label="Facility">' + opts(myFacilities(), S.inv.facilityId, f => f.name, f => f.id) + '</select><select id="iMed" aria-label="Medicine"><option value="">All medicines</option>' + mo + '</select>')
    + panel('Batches on hand <span>(' + list.length + ' batches, ' + num(usable) + ' usable units)</span>', '<div class="scroll tall"><table><thead><tr><th>Medicine</th><th>Batch</th><th class="n">Qty</th><th>Made</th><th>Expires</th><th>Received</th><th>Status</th></tr></thead><tbody>'
      + (list.map(b => '<tr><td>' + esc((med(b.medicineId) || {}).name || b.medicineId) + '</td><td>' + esc(b.batchNo) + '</td><td class="n">' + num(b.quantity) + '</td><td>' + dmy(b.manufactureDate) + '</td><td>' + dmy(b.expiryDate) + '</td><td>' + dmy(b.receivedDate) + '</td><td>' + tag(batchStatus(b, asOf)) + '</td></tr>').join('') || none(7, 'No batches for this selection.'))
      + '</tbody></table></div>')
    + '<div class="cols c2">' + panel('Record a receipt', '<form class="pad filters" data-form="batch"><label><span>Medicine</span><select name="medicineId">' + mo + '</select></label><label><span>Batch no.</span><input type="text" name="batchNo" required></label><label><span>Quantity</span><input type="number" name="quantity" min="1" required></label>'
      + '<label><span>Made on</span><input type="date" name="manufactureDate"></label><label><span>Expires on</span><input type="date" name="expiryDate" required></label><div class="act"><button class="go">Save receipt</button></div></form>')
    + panel('Record an issue', '<form class="pad filters" data-form="consumption"><label><span>Medicine</span><select name="medicineId">' + mo + '</select></label><label><span>Quantity asked for</span><input type="number" name="demanded" min="0" required></label><label><span>Quantity given</span><input type="number" name="dispensed" min="0" required></label>'
      + '<div class="act"><button class="go">Save issue</button></div></form>') + '</div>'
    + panel('Latest ledger entries', '<div class="scroll"><table><thead><tr><th>Time</th><th>Medicine</th><th>Entry</th><th class="n">Qty</th><th class="n">Balance</th></tr></thead><tbody id="liveRows"></tbody></table></div>');
}
async function loadBatches() {
  const facs = myFacilities();
  if (!facs.find(f => String(f.id) === String(S.inv.facilityId))) S.inv.facilityId = facs.length ? facs[0].id : null;
  S.inv.batches = S.inv.facilityId ? await api('/api/inventory/batches?facilityId=' + S.inv.facilityId) : [];
}

// ------------------------------------------------------------------ Analytics & reports
function serviceCharts() {
  const d = S.analytics.days, l = S.live; if (!d) return;
  const fill = d.map(x => x.fillRatePct).concat(l && l.demandedUnitsToday ? [l.fillRateTodayPct] : []), names = d.map(x => dm(x.date)).concat(['today']);
  chart('cFill', {n: fill.length, zoom: true, max: 100, unit: '%', series: [{values: fill, color: '#2f8a3f', dots: true, fill: 'rgba(47,138,63,.10)'}],
    labels: names.slice(0, fill.length).map((t, i) => ({i, text: t})).filter((_, i) => i % 5 === 0), tip: i => names[i] + ': fill rate ' + fill[i] + '%' + (d[i] ? ', ' + num(d[i].unmetUnits) + ' units not supplied' : '')});
  chart('cShort', {n: d.length, bars: true, series: [{values: d.map(x => x.linesShort), color: '#c0362c', bars: true}],
    labels: d.map((x, i) => ({i, text: dm(x.date)})).filter((_, i) => i % 5 === 0), tip: i => dm(d[i].date) + ': ' + d[i].linesShort + ' lines short, ' + num(d[i].unmetUnits) + ' units not supplied'});
}
function analyticsPage() {
  const a = S.analytics, rc = a.recommendationCounts;
  const kpi = (cls, ic, v, t) => '<div class="kpi ' + cls + '"><div class="ic">' + icon(ic, 20) + '</div><div><b>' + v + '</b><span>' + t + '</span></div></div>';
  let h = head('Analytics & reports', esc(a.district) + ' district, last 30 days unless stated.');
  h += '<div class="kpis">' + kpi('k', 'chart', a.fillRate30Pct + '%', 'fill rate (90 days: ' + a.fillRate90Pct + '%)') + kpi('o', 'alert', a.stockoutRate30Pct + '%', 'line-days short (' + num(a.unmetUnits30) + ' units not supplied)')
    + kpi('u', 'clock', num(a.expiredUnitsOnHand), 'expired units on shelf (Rs ' + num(a.expiredValue) + ')') + '</div>';
  h += '<div class="cols c2">' + panel('Fill rate by day', '<div class="pad"><div class="key"><span><i style="border-color:#2f8a3f"></i>Units given / units asked for, %</span></div><canvas id="cFill"></canvas></div>')
    + panel('Lines short by day', '<div class="pad"><div class="key"><span><i class="b" style="border-color:#c0362c"></i>Medicine lines that could not be fully supplied</span></div><canvas id="cShort"></canvas></div>') + '</div>';
  h += '<div class="cols c2">' + panel('Effect of transfers', '<table class="kv"><tbody><tr><th>Transfers approved</th><td>' + a.transfersExecuted + '</td></tr><tr><th>Units moved</th><td>' + num(a.unitsRedistributed)
    + '</td></tr><tr><th>Days of stock added</th><td>' + one(a.coverDaysAdded) + '</td></tr><tr><th>Units saved from expiry</th><td>' + num(a.expirySavedUnits) + ' (Rs ' + num(a.expirySavedValue) + ')</td></tr>'
    + '<tr><th>Advice approved, of decided</th><td>' + a.acceptanceRatePct + '%</td></tr><tr><th>Advice so far</th><td>' + Object.keys(rc).map(k => tag(k) + ' ' + rc[k]).join(' &nbsp;') + '</td></tr></tbody></table>') + accuracyPanel() + '</div>';
  h += panel('Facility performance', '<div class="scroll"><table><thead><tr><th>Facility</th><th class="n">Fill rate</th><th class="n">Line-days short</th><th class="n">Lines at high or critical risk</th><th class="n">Units that may expire</th></tr></thead><tbody>'
    + a.facilities.map(f => '<tr><td>' + esc(f.name) + '</td><td class="n">' + f.fillRatePct + '%</td><td class="n">' + f.stockoutDays + '</td><td class="n">' + f.criticalItems + '</td><td class="n">' + num(f.unitsAtExpiryRisk) + '</td></tr>').join('') + '</tbody></table></div>');
  h += panel('Download data', '<div class="scroll"><table><thead><tr><th>Report</th><th>Contents</th><th class="n">Rows</th><th></th></tr></thead><tbody>'
    + Object.keys(REPORTS).map(k => '<tr><td>' + REPORTS[k][0] + '</td><td class="muted">' + REPORTS[k][1] + '</td><td class="n">' + REPORTS[k][3]().length + '</td><td><button data-act="csv" data-id="' + k + '">CSV</button></td></tr>').join('')
    + '</tbody></table></div>', '<button data-act="print">Print page</button>');
  return h;
}
function accuracyPanel() {
  const by = {}; S.overview.risks.forEach(r => { if (r.backtestErrorPct >= 0) (by[r.medicineName] = by[r.medicineName] || []).push(r.backtestErrorPct); });
  const median = v => { const s = v.slice().sort((x, y) => x - y), n = s.length; return n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2; };
  const all = Object.values(by).flat(), top = Math.max(20, ...all);
  return panel('Forecast accuracy <span>(tested on the last 14 days)</span>', '<div class="scroll"><table><thead><tr><th>Medicine</th><th>Typical error</th><th class="n">Best</th><th class="n">Worst</th></tr></thead><tbody>'
    + Object.keys(by).map(k => { const v = by[k], md = median(v); return '<tr><td>' + esc(k) + '</td><td><span class="meter"><i style="width:' + Math.min(100, md / top * 100) + '%"></i></span>' + one(md) + '%</td><td class="n">' + one(Math.min(...v)) + '%</td><td class="n">' + one(Math.max(...v)) + '%</td></tr>'; }).join('')
    + (all.length ? '<tr><td><b>All medicines</b></td><td><b>' + one(median(all)) + '%</b></td><td class="n">' + one(Math.min(...all)) + '%</td><td class="n">' + one(Math.max(...all)) + '%</td></tr>' : none(4, 'Not enough history to test.'))
    + '</tbody></table></div>');
}
const REPORTS = {
  risk: ['Stock and stock-out risk', 'One row per facility and medicine.', ['Facility', 'Medicine', 'Unit', 'In stock', 'Avg monthly use', 'Months of stock', 'Use per day', 'Trend %', 'Days left', 'Runs out', 'Chance % (14 days)', 'Status', 'Risk', 'Advice'],
    () => S.overview.risks.map(r => [r.facilityName, r.medicineName, r.unit, r.currentStock, r.avgMonthlyConsumption, r.monthsOfStock ?? '', r.forecastDailyDemand, r.trendPct, r.daysToStockout ?? '180+', r.stockoutDate ?? '', r.stockoutProbabilityPct, STATUS[r.stockStatus], r.riskLevel, r.suggestedAction])],
  transfers: ['Transfers', 'All transfer advice with status and basis.', ['No.', 'Medicine', 'From', 'To', 'Qty', 'Unit', 'Distance km', 'Urgency', 'Status', 'Advised', 'Decided', 'Basis'],
    () => S.history.map(t => [t.id, t.medicineName, t.fromName, t.toName, t.quantity, t.unit, t.distanceKm, t.urgency, t.status, t.createdAt, t.decidedAt ?? '', t.rationale])],
  expiry: ['Expiry watch', 'Batches expected to expire before use.', ['Facility', 'Medicine', 'Batch', 'Expires', 'Days left', 'Batch qty', 'Likely to expire', 'Value Rs', 'Advice'],
    () => S.overview.expiryAlerts.map(a => [a.facilityName, a.medicineName, a.batchNo, a.expiryDate, a.daysToExpiry, a.batchQuantity, a.unitsLikelyToExpire, a.valueAtRisk, a.action])],
  indents: ['Indents', 'Re-order quantities and dates.', ['Facility', 'Medicine', 'Qty', 'Unit', 'Indent by', 'Arrives', 'Lead time days', 'Re-order level', 'Safety stock', 'Value Rs', 'Status', 'Basis'],
    () => S.overview.procurement.map(r => [r.facilityName, r.medicineName, r.quantity, r.unit, r.orderBy, r.expectedDelivery, r.leadTimeDays, r.reorderPoint, r.safetyStock, r.estimatedCost, r.status, r.rationale])],
  facilities: ['Facility performance', 'Fill rate and shortages per facility.', ['Code', 'Facility', 'Fill rate %', 'Line-days short', 'Lines at high or critical risk', 'Units that may expire'],
    () => S.analytics.facilities.map(f => [f.code, f.name, f.fillRatePct, f.stockoutDays, f.criticalItems, f.unitsAtExpiryRisk])],
  daily: ['Daily service level', 'Fill rate and lines short by day.', ['Date', 'Fill rate %', 'Lines short', 'Units issued', 'Units not supplied'],
    () => S.analytics.days.map(d => [d.date, d.fillRatePct, d.linesShort, d.issuedUnits, d.unmetUnits])]
};
function downloadCsv(key) {
  const r = REPORTS[key], cell = v => '"' + String(v ?? '').replace(/"/g, '""') + '"';
  const csv = [r[2]].concat(r[3]()).map(row => row.map(cell).join(',')).join('\r\n'), a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob(['﻿' + csv], {type: 'text/csv;charset=utf-8'}));
  a.download = 'medichain-' + key + '-' + S.district.toLowerCase() + '-' + S.overview.asOf + '.csv';
  document.body.appendChild(a); a.click(); a.remove();
}

// ------------------------------------------------------------------ Administration
function adminPage() {
  const tab = S.adm.tab;
  let h = head('Administration', isAdmin() ? 'Facilities, medicines and settings.' : 'Master lists are maintained by the state administrator.');
  h += '<section class="panel">' + tabs('admtab', [['facilities', 'Facilities'], ['medicines', 'Medicines'], ['settings', 'Settings']], tab);
  if (tab === 'medicines') {
    const soh = {}, due = {};
    S.overview.risks.forEach(r => { soh[r.medicineId] = (soh[r.medicineId] || 0) + r.currentStock; if (r.atRiskInHorizon) due[r.medicineId] = (due[r.medicineId] || 0) + 1; });
    h += '<div class="scroll"><table><thead><tr><th>Code</th><th>Medicine</th><th>Category</th><th>Unit</th><th>Seasonal disease</th><th class="n">Lead time</th><th class="n">Rate (Rs)</th><th class="n">In stock, ' + esc(S.district) + '</th><th class="n">May run out</th></tr></thead><tbody>'
      + S.meta.medicines.map(m => '<tr><td>' + esc(m.code) + '</td><td>' + esc(m.name) + '</td><td>' + esc(m.category) + '</td><td>' + esc(m.unit) + '</td><td>' + esc(m.linkedDisease ? m.linkedDisease.charAt(0) + m.linkedDisease.slice(1).toLowerCase() : '-') + '</td><td class="n">' + m.leadTimeDays + ' d</td><td class="n">' + m.unitCost
        + '</td><td class="n">' + num(soh[m.id]) + '</td><td class="n">' + (due[m.id] || 0) + '</td></tr>').join('') + '</tbody></table></div>';
    if (isAdmin()) h += '<form class="pad filters" data-form="medicine" style="border-top:1px solid var(--line-soft)"><label><span>Code</span><input type="text" name="code" required maxlength="8"></label><label><span>Name and strength</span><input type="text" name="name" required></label><label><span>Category</span><input type="text" name="category"></label>'
      + '<label><span>Unit</span><input type="text" name="unit" required value="tablets"></label><label><span>Seasonal disease</span><select name="linkedDisease"><option value="">None</option><option>DENGUE</option><option>DIARRHOEA</option><option>ARI</option><option>MALARIA</option></select></label>'
      + '<label><span>Lead time (days)</span><input type="number" name="leadTimeDays" min="1" max="120" value="14" required></label><label><span>Rate (Rs)</span><input type="number" name="unitCost" min="0" step="0.01" value="1" required></label><div class="act"><button class="go">Add medicine</button></div></form>';
    return h + '</section>';
  }
  if (tab === 'settings') {
    const b = S.meta.riskBands, hl = S.health, s = S.session, up = v => v ? st(v === 'UP' ? 'ADEQUATE' : 'STOCKED_OUT').replace(/>(Adequate|Stocked out)</, '>' + esc(v) + '<') : 'checking...';
    return h + '<div class="cols c2 pad" style="margin:0"><div><table class="kv"><tbody><tr><th>Signed in as</th><td>' + esc(s.displayName) + ' (' + esc(s.username) + ')</td></tr><tr><th>Office</th><td>' + esc(s.designation) + '</td></tr><tr><th>Access</th><td>'
      + (isAdmin() ? 'All districts' : esc(s.homeDistrict) + ' district') + '</td></tr><tr><th>Session ends</th><td>' + stamp(s.expiresAt) + '</td></tr><tr><th>forecast-service</th><td>' + up(hl && hl.forecastService) + '</td></tr><tr><th>inventory-service</th><td>' + up(hl && hl.inventoryService)
      + '</td></tr></tbody></table><form class="filters" data-form="settings" style="margin-top:12px"><label><span>Look-ahead for stock-outs (days)</span><input type="number" name="horizon" min="3" max="60" value="' + S.horizon + '" required></label><div class="act"><button class="go">Save</button></div></form></div>'
      + '<div><table class="kv"><tbody>' + [['Stock status', 'Out at 0. Understocked below ' + b.MIN_STOCK_DAYS + ' days. Overstocked above ' + b.MAX_STOCK_DAYS + ' days.'],
        ['Risk', 'Critical up to ' + b.CRITICAL + ' days, high up to ' + b.HIGH + ', moderate up to ' + b.MODERATE + '.'], ['Forecast', 'Damped-trend exponential smoothing, weekday pattern, disease cases.'],
        ['Transfer', 'Receiver to 30 days; donor keeps 45 days; 21 days shelf life; within 120 km.'], ['Indent', 'Lead-time demand plus safety stock at 95% service level.']].map(r => '<tr><th>' + r[0] + '</th><td style="font-weight:400">' + r[1] + '</td></tr>').join('') + '</tbody></table></div></div></section>';
  }
  const list = S.facilities.filter(f => !S.adm.district || f.district === S.adm.district), risk = {};
  S.overview.risks.forEach(r => { if (r.riskLevel === 'CRITICAL' || r.riskLevel === 'HIGH') risk[r.facilityId] = (risk[r.facilityId] || 0) + 1; });
  h += (isAdmin() ? '<div class="pad" style="padding-bottom:0"><select id="facDist" style="width:220px"><option value="">All districts (' + S.facilities.length + ')</option>' + opts(S.meta.districts, S.adm.district) + '</select></div>' : '')
    + '<div class="pad scroll"><table><thead><tr><th>Code</th><th>Facility</th><th>Type</th><th>District</th><th class="n">Latitude</th><th class="n">Longitude</th><th class="n">Lines at high or critical risk</th></tr></thead><tbody>'
    + list.map(f => '<tr><td>' + esc(f.code) + '</td><td>' + esc(f.name) + '</td><td>' + esc(f.type) + '</td><td>' + esc(f.district) + '</td><td class="n">' + f.latitude + '</td><td class="n">' + f.longitude + '</td><td class="n">' + (f.district === S.district ? (risk[f.id] || 0) : '-') + '</td></tr>').join('') + '</tbody></table></div>';
  if (isAdmin()) h += '<form class="pad filters" data-form="facility" style="border-top:1px solid var(--line-soft)"><label><span>Code</span><input type="text" name="code" required maxlength="12"></label><label><span>Facility name</span><input type="text" name="name" required></label><label><span>Type</span><select name="type"><option>PHC</option><option>CHC</option><option>GH</option></select></label>'
    + '<label><span>District</span><input type="text" name="district" required list="dl" value="' + esc(S.district) + '"><datalist id="dl">' + S.meta.districts.map(d => '<option value="' + esc(d) + '">').join('') + '</datalist></label><label><span>Latitude</span><input type="number" name="latitude" step="0.0001" min="-90" max="90" required></label>'
    + '<label><span>Longitude</span><input type="number" name="longitude" step="0.0001" min="-180" max="180" required></label><div class="act"><button class="go">Add facility</button></div></form>';
  return h + '</section>';
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
    if (r === 'redistribution' || r === 'analytics') S.history = await api('/api/recommendations');
    if (r === 'admin') { S.health = null; api('/api/health').then(h => { S.health = h; if (route() === 'admin') render(); }).catch(() => {}); }
  } catch (e) { if (S.session) toast(e.message, true); }
  render(); if ($('main')) $('main').scrollTop = 0;
}
async function changeDistrict(value) {
  S.district = value; S.sc.result = null; S.search.facilityId = ''; S.search.data = null; S.inv.facilityId = null; S.ask.result = null; S.overview = null; render();
  await busy(async () => { await refreshAll(); if (route() === 'inventory') await loadBatches(); });
}
function go(page) { if (route() === page) render(); else location.hash = '#/' + page; }
document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el || !S.session) return;
  const act = el.dataset.act, id = el.dataset.id;
  if (act === 'signout') { api('/api/auth/logout', {method: 'POST'}).catch(() => {}).finally(() => signedOut('You have signed out.')); }
  else if (act === 'pick') { S.search.facilityId = id; render(); }
  else if (act === 'tab') { S.search.tab = id; render(); }
  else if (act === 'med') { S.search.medicineId = +id; S.search.facilityId = ''; busy(loadSearch); }
  else if (act === 'open') { S.search.medicineId = +el.dataset.med; S.search.facilityId = el.dataset.fac; S.search.tab = 'summary'; busy(loadSearch).then(() => go('search')); }
  else if (act === 'frtab') { S.fr.tab = id; if (el.tagName !== 'A') render(); }
  else if (act === 'level') { S.fr.level = id; render(); }
  else if (act === 'trtab') { S.tr.tab = id; if (el.dataset.go) go(el.dataset.go); else if (el.tagName !== 'A') render(); }
  else if (act === 'admtab') { S.adm.tab = id; if (id === 'settings') enter(); else render(); }
  else if (act === 'example') { S.ask.text = el.dataset.q; const f = document.querySelector('[data-form="ask"]'); if (f) { f.q.value = el.dataset.q; f.requestSubmit(); } }
  else if (act === 'accept' || act === 'reject') { el.disabled = true; let done = null;
    busy(async () => { const t = await post('/api/recommendations/' + id + '/' + act); done = 'Transfer rejected.'; if (act === 'accept') done = 'Transfer approved: ' + num(t.quantity) + ' ' + t.unit + ' moved, ' + one(t.coverDaysGained) + ' days of stock added'
      + (t.expirySavedUnits ? ', ' + num(t.expirySavedUnits) + ' saved from expiry' : '') + '.'; await refreshAll(); S.history = await api('/api/recommendations'); }).then(() => { if (done) toast(done); }); }
  else if (act === 'csv') downloadCsv(id);
  else if (act === 'print') window.print();
});
document.addEventListener('change', e => {
  const t = e.target; if (!S.session) return;
  if (t.id === 'gDistrict') changeDistrict(t.value);
  else if (t.id === 'iFac') { S.inv.facilityId = t.value; busy(loadBatches); }
  else if (t.id === 'iMed') { S.inv.medicineId = t.value; render(); }
  else if (t.id === 'fMed') { S.fr.medicineId = t.value; render(); }
  else if (t.id === 'facDist') { S.adm.district = t.value; render(); }
  else if (t.id === 'mapMed') { S.mapMed = t.value; render(); }
});
document.addEventListener('submit', e => {
  const form = e.target.closest('[data-form]'); if (!form) return;
  e.preventDefault();
  const v = Object.fromEntries(new FormData(form).entries()), kind = form.dataset.form;
  if (kind === 'scenario') { Object.assign(S.sc, {medicineId: v.medicineId, disease: v.disease, casePct: +v.casePct, demandPct: +v.demandPct});
    busy(async () => { S.sc.result = await api('/api/scenario?' + qs({district: S.district, medicineId: v.medicineId, horizonDays: S.horizon, casePct: v.casePct, demandPct: v.demandPct, disease: v.disease})); }); }
  else if (kind === 'ask') { S.ask.text = v.q; busy(async () => { S.ask.result = await post('/api/query', {query: v.q, district: S.district}); const dist = S.ask.result.interpreted.district; if (dist !== S.district) S.district = dist; await refreshAll(); }); }
  else if (kind === 'settings') { S.horizon = Math.max(3, Math.min(60, +v.horizon || 10)); store.set('mc.horizon', S.horizon); busy(refreshAll, 'Saved.'); }
  else if (kind === 'facility') busy(async () => { await post('/api/inventory/facilities', {code: v.code.trim(), name: v.name.trim(), type: v.type, district: v.district.trim(), latitude: +v.latitude, longitude: +v.longitude}); await loadMeta(); await refreshAll(); }, 'Facility added.');
  else if (kind === 'medicine') busy(async () => { await post('/api/inventory/medicines', {code: v.code.trim().toUpperCase(), name: v.name.trim(), category: v.category.trim(), unit: v.unit.trim(), aliases: v.name.trim().split(' ')[0].toLowerCase(), linkedDisease: v.linkedDisease || null, leadTimeDays: +v.leadTimeDays, unitCost: +v.unitCost}); await loadMeta(); await refreshAll(); }, 'Medicine added.');
  else if (kind === 'batch') busy(async () => { await post('/api/inventory/batches', {facilityId: +S.inv.facilityId, medicineId: +v.medicineId, batchNo: v.batchNo, quantity: +v.quantity, manufactureDate: v.manufactureDate || null, expiryDate: v.expiryDate}); await loadBatches(); await refreshAll(); }, 'Receipt saved.');
  else if (kind === 'consumption') busy(async () => { await post('/api/inventory/consumption', {facilityId: +S.inv.facilityId, medicineId: +v.medicineId, demanded: +v.demanded, dispensed: +v.dispensed}); await loadBatches(); await refreshAll(); }, 'Issue saved.');
});
document.addEventListener('input', e => { const out = e.target.dataset && e.target.dataset.out; if (out && $(out)) $(out).textContent = (e.target.value > 0 ? '+' : '') + e.target.value + '%'; });
window.addEventListener('hashchange', enter);
let resizeTimer = null;
window.addEventListener('resize', () => { clearTimeout(resizeTimer); resizeTimer = setTimeout(() => { if (S.session && S.overview && !typing()) render(); }, 250); });

// ------------------------------------------------------------------ refresh timers
const LIVE_MS = 5000, FULL_MS = 30000;
let timers = [];
function stopTimers() { timers.forEach(clearInterval); timers = []; }
const typing = () => { const a = document.activeElement; return a && ['INPUT', 'SELECT', 'TEXTAREA'].includes(a.tagName); };
function startTimers() {
  stopTimers();
  timers.push(setInterval(async () => {
    if (!S.session || document.hidden) return;
    try { S.live = await api('/api/live?' + qs({district: S.district, minutes: 30})); S.lastRefresh = S.live.serverTime; paintLive(false); } catch (e) { /* the next manual action reports it */ }
  }, LIVE_MS));
  timers.push(setInterval(async () => {
    if (!S.session || document.hidden || !S.overview) return;
    try {
      await loadOverview(); if (route() === 'search') await loadSearch(); if (route() === 'inventory') await loadBatches();
      if (['dashboard', 'search', 'forecast', 'analytics'].includes(route()) && !typing() && !S.popupOpen) render();
    } catch (e) { /* keep the last figures */ }
  }, FULL_MS));
}

// ------------------------------------------------------------------ start
async function start() {
  S.district = S.session.workingDistrict; S.horizon = store.get('mc.horizon') || 10;
  $('app').innerHTML = '<div class="waiting"><div><div class="spinner"></div><p>Loading ' + esc(S.district) + '...</p></div></div>';
  try { await loadMeta(); await refreshAll(); startTimers(); await enter(); }
  catch (e) { if (S.session) { $('app').innerHTML = '<div class="waiting"><div><div class="warn">' + esc(e.message) + '</div><button class="go" onclick="location.reload()">Reload</button></div></div>'; } }
}
// On free hosting the inventory service may still be waking up: wait for it instead of showing errors.
async function waitForServices() {
  const began = Date.now();
  for (;;) {
    try {
      const res = await fetch('/api/health', {cache: 'no-store'});
      if (res.ok) { const h = await res.json(); if (h.inventoryService === 'UP') return; }
    } catch (e) { /* not reachable yet */ }
    const secs = Math.round((Date.now() - began) / 1000);
    if (secs > 300) return;
    $('app').innerHTML = '<div class="waiting"><div><div class="spinner"></div><h2>MediChain</h2><p>Starting the stock services... ' + secs + ' s</p><p class="muted">After a quiet period this takes up to 2 minutes. The page opens by itself.</p></div></div>';
    await new Promise(r => setTimeout(r, 4000));
  }
}
(async function init() {
  await waitForServices();
  try { const res = await fetch('/api/auth/regions'); if (res.ok) S.regions = await res.json(); } catch (e) { /* services down: the sign-in page says so */ }
  const saved = store.get('mc.session');
  if (saved && saved.token) {
    S.session = saved;
    try { S.session = await api('/api/auth/me'); await start(); return; } catch (e) { S.session = null; store.set('mc.session', null); }
  }
  signinPage();
})();
