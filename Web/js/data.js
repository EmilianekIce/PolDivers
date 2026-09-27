// Data layer: live war state, names and trends. Mirrors the Android app's repository:
// Arrowhead's raw status (via Helldivers Training Manual's CORS-enabled mirror, every 10 s),
// the war layout / news / orders from the community API, names from the bundled war_static.json.

const COMMUNITY = "https://api.helldivers2.dev";
const HDTM = "https://helldiverstrainingmanual.com";
const CLIENT = { "X-Super-Client": "poldivers-web", "X-Super-Contact": "github.com/EmilianekIce/PolDivers" };
const RACES = { 1: "Humans", 2: "Terminids", 3: "Automaton", 4: "Illuminate" };
const REGION_SIZES = ["Settlement", "Town", "City", "MegaCity"];

export const settings = {
  get lang() { return localStorage.getItem("lang") || "pl"; },
  set lang(v) { localStorage.setItem("lang", v); },
  get anim() { return localStorage.getItem("anim") !== "0"; },
  set anim(v) { localStorage.setItem("anim", v ? "1" : "0"); },
};
const acceptLanguage = () => (settings.lang === "pl" ? "pl-PL" : "en-US");

async function getJSON(url, { headers = {}, timeout = 25000 } = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeout);
  try {
    const res = await fetch(url, { headers, signal: ctrl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status} — ${url.split("/").slice(2, 3)}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}
const community = (path) => getJSON(COMMUNITY + path, { headers: { ...CLIENT, "Accept-Language": acceptLanguage() } });

/** Tiny cache: memory + localStorage, per key, with a max age. */
function stored(key, maxAgeMs) {
  try {
    const raw = localStorage.getItem("c:" + key);
    if (!raw) return null;
    const { t, v } = JSON.parse(raw);
    return Date.now() - t < maxAgeMs ? v : null;
  } catch { return null; }
}
function store(key, value) {
  try { localStorage.setItem("c:" + key, JSON.stringify({ t: Date.now(), v: value })); } catch { /* quota */ }
}

// ---------- bundled data ----------
let assetsPromise;
export function loadAssets() {
  assetsPromise ??= Promise.all([
    fetch("assets/war_static.json").then((r) => r.json()),
    fetch("assets/index.json").then((r) => r.json()),
    fetch("assets/planet_effects.json").then((r) => r.json()),
    fetch("assets/game_terms.json").then((r) => r.json()),
    fetch("assets/planet_conditions.json").then((r) => r.json()),
    fetch("assets/sector_regions.json").then((r) => r.json()),
  ]).then(([stat, index, effects, terms, conditions, sectors]) => {
    const norm = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
    const byNorm = (files) => Object.fromEntries(files.map((f) => [norm(f.replace(/\.webp$/, "")), f]));
    return { stat, index, effects, terms, conditions, sectors, norm, icons: byNorm(index.icons), planetArt: byNorm(index.planets), campaignArt: byNorm(index.campaigns) };
  });
  return assetsPromise;
}

let dictPromise;
/** Official EN->PL game dictionary (large, only loaded for Polish). */
export function loadDictionary() {
  if (settings.lang !== "pl") return Promise.resolve(null);
  dictPromise ??= fetch("assets/game_dict_pl.json").then((r) => r.json()).catch(() => null);
  return dictPromise;
}

// ---------- trends (pace) ----------
const TREND_KEEP = 4 * 3600e3, MIN_GAP = 8e3, MIN_SPAN = 9e3, WINDOW = 3600e3;
const trends = (() => {
  try { return JSON.parse(localStorage.getItem("trends") || "{}"); } catch { return {}; }
})();
let trendsDirty = 0;
function record(key, value, t = Date.now()) {
  const list = (trends[key] ??= []);
  const last = list[list.length - 1];
  if (last && t - last[0] < MIN_GAP) last[1] = value;
  else list.push([t, value]);
  while (list.length && t - list[0][0] > TREND_KEEP) list.shift();
  if (list.length > 900) list.splice(0, list.length - 900);
  if (Date.now() - trendsDirty > 30e3) {
    trendsDirty = Date.now();
    try { localStorage.setItem("trends", JSON.stringify(trends)); } catch { /* quota */ }
  }
}
function seed(key, samples) {
  const merged = [...(trends[key] || []), ...samples].sort((a, b) => a[0] - b[0]);
  const out = [];
  for (const s of merged) {
    if (Date.now() - s[0] > TREND_KEEP) continue;
    const last = out[out.length - 1];
    if (last && s[0] - last[0] < MIN_GAP) out[out.length - 1] = s; else out.push(s);
  }
  trends[key] = out;
}
/** Change per hour (least squares over the last hour), null until there is enough history. */
export function ratePerHour(key, now = Date.now()) {
  const all = trends[key];
  if (!all || all.length < 2) return null;
  const recent = all.filter((s) => now - s[0] <= WINDOW);
  const pts = recent.length >= 2 && recent[recent.length - 1][0] - recent[0][0] >= MIN_SPAN ? recent : all;
  if (pts.length < 2 || pts[pts.length - 1][0] - pts[0][0] < MIN_SPAN) return null;
  const t0 = pts[0][0];
  const xs = pts.map((p) => p[0] - t0), ys = pts.map((p) => p[1]);
  const mx = xs.reduce((a, b) => a + b) / xs.length, my = ys.reduce((a, b) => a + b) / ys.length;
  let num = 0, den = 0;
  xs.forEach((x, i) => { num += (x - mx) * (ys[i] - my); den += (x - mx) ** 2; });
  return den === 0 ? 0 : (num / den) * 3600e3;
}
/** Value now, extrapolated from the last sample at the current pace (ticks every second). */
export function liveValue(key, measured, rate, now = Date.now()) {
  const last = trends[key]?.[trends[key].length - 1];
  if (rate == null || !last) return measured;
  const ahead = Math.min(Math.max(now - last[0], 0), 120e3);
  return Math.min(100, Math.max(0, measured + (rate * ahead) / 3600e3));
}
export const keys = {
  planet: (i) => `planet:${i}`,
  event: (i) => `event:${i}`,
  region: (p, r) => `region:${p}:${r}`,
  task: (a, t) => `mo:${a}:${t}`,
};

// ---------- mapping (same as the app's OfficialSource) ----------
export const liberation = (health, max) => (max > 0 ? Math.min(100, Math.max(0, (1 - health / max) * 100)) : 0);

function buildPlanets(A, info, status, summary) {
  const st = new Map(status.planetStatus.map((p) => [p.index, p]));
  const events = new Map(status.planetEvents.map((e) => [e.planetIndex, e]));
  const attacks = {};
  status.planetAttacks.forEach((a) => (attacks[a.source] ??= []).push(a.target));
  const regionStatus = new Map((status.planetRegions || []).map((r) => [`${r.planetIndex}:${r.regionIndex}`, r]));
  const stats = new Map((summary?.planets_stats || []).map((s) => [s.planetIndex, s]));
  const now = Date.now();
  const iso = (warSeconds) => new Date(now + (warSeconds - status.time) * 1000);
  const pl = settings.lang === "pl";
  const planets = [];
  for (const p of info.planetInfos) {
    const s = A.stat.planets[p.index];
    if (!s || !s.sector) continue;
    const ps = st.get(p.index) || {};
    const e = events.get(p.index);
    planets.push({
      index: p.index,
      name: (pl ? s.pl : s.en) || s.en,
      english: s.en,
      sector: s.sector,
      biome: s.biome,
      position: p.position,
      waypoints: p.waypoints || [],
      maxHealth: p.maxHealth,
      health: ps.health ?? p.maxHealth,
      regenPerSecond: ps.regenPerSecond || 0,
      owner: RACES[ps.owner] || "",
      initialOwner: RACES[p.initialOwner] || "",
      players: ps.players || 0,
      attacking: attacks[p.index] || [],
      event: e && { id: e.id, faction: RACES[e.race] || "", health: e.health, maxHealth: e.maxHealth, start: iso(e.startTime), end: iso(e.expireTime) },
      stats: stats.get(p.index),
      regions: (info.planetRegions || []).filter((r) => r.planetIndex === p.index).map((r) => {
        const rs = regionStatus.get(`${p.index}:${r.regionIndex}`) || {};
        return {
          id: r.regionIndex,
          name: A.stat.regions[BigInt.asUintN(64, BigInt(r.settingsHash)).toString()] || null,
          size: REGION_SIZES[r.regionSize] || "",
          health: rs.health, maxHealth: r.maxHealth, available: !!rs.isAvailable, players: rs.players || 0,
          owner: RACES[rs.owner] || "",
        };
      }),
    });
  }
  return planets;
}

function resolveEffects(A, dict, ids) {
  const polishMap = Object.entries(A.index.effectPolish);
  const kinds = A.index.effectKinds;
  const out = [];
  const seen = new Set();
  for (const id of ids) {
    const entry = A.effects[id];
    if (!entry) continue;
    const original = entry.name.replace(/\s*\(enemies\)/i, "").trim();
    if (/marker/i.test(original) || kinds.hidden.includes(original)) continue;
    const upper = original.toUpperCase();
    const kind = kinds.enemy.some((k) => upper.includes(k)) ? "enemy"
      : kinds.hazard.some((k) => upper.includes(k)) ? "hazard"
      : kinds.support.some((k) => upper.includes(k)) ? "support"
      : kinds.site.some((k) => upper.includes(k)) ? "site" : "other";
    let name = original;
    if (settings.lang === "pl") {
      const official = dict?.en2pl?.[original.toLowerCase()];
      name = official ? sentenceCase(official) : (polishMap.find(([k]) => upper.includes(k))?.[1] ?? original);
    }
    if (seen.has(name.toLowerCase())) continue;
    seen.add(name.toLowerCase());
    const description = settings.lang === "pl" ? (dict?.en2pl?.[entry.description?.toLowerCase()] ?? entry.description) : entry.description;
    out.push({ id, name, original, description, kind });
  }
  const order = ["enemy", "hazard", "support", "site", "other"];
  return out.sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind));
}
export const sentenceCase = (t) => (t !== t.toUpperCase() || !/[a-zA-ZĄ-Żą-ż]/.test(t) ? t : t.charAt(0) + t.slice(1).toLowerCase());

// ---------- live store ----------
export const war = {
  planets: [], byIndex: new Map(), campaigns: [], effects: new Map(), dss: null,
  assignments: [], dispatches: [], stations: [], statusTime: 0, updatedAt: 0, error: null,
};
const listeners = new Set();
export const subscribe = (fn) => (listeners.add(fn), () => listeners.delete(fn));
const emit = () => listeners.forEach((fn) => fn(war));

async function warInfo() {
  const cached = stored("info", 6 * 3600e3);
  if (cached) return cached;
  // Helldivers Training Manual mirrors Arrowhead's WarInfo without the community API's 5 req/10 s limit.
  const info = await getJSON(HDTM + "/api/v1/war/info", { timeout: 15000 }).catch(() => community("/raw/api/WarSeason/801/WarInfo"));
  store("info", info);
  return info;
}
async function warStatus() {
  try {
    return await getJSON(HDTM + "/api/v1/war/status", { timeout: 15000 });
  } catch {
    return community("/raw/api/WarSeason/801/Status");
  }
}
let summaryCache = null, summaryAt = 0;
function refreshSummary() {
  if (Date.now() - summaryAt < 5 * 60e3) return;
  if (!summaryAt) { summaryAt = Date.now() - 5 * 60e3 + 15e3; return; } // first one a bit later: spread the community API calls
  summaryAt = Date.now();
  community("/raw/api/Stats/war/801/summary").then((s) => { summaryCache = s; }).catch(() => {});
}

const seededAt = new Map();
/** Last hours of 5-minute health snapshots for liberation fronts: a pace right away. */
function seedHistory(planets) {
  for (const p of planets) {
    if (p.event || Date.now() - (seededAt.get(p.index) || 0) < 10 * 60e3) continue;
    seededAt.set(p.index, Date.now());
    getJSON(`${HDTM}/api/v1/war/history/${p.index}`, { timeout: 15000 }).then((snaps) => {
      const samples = snaps
        .map((s) => [Date.parse(s.created_at), liberation(s.current_health, s.max_health)])
        .filter(([t]) => Number.isFinite(t) && Date.now() - t < 3 * 3600e3);
      seed(keys.planet(p.index), samples);
      emit();
    }).catch(() => seededAt.delete(p.index));
  }
}

export async function refreshStatus() {
  const A = await loadAssets();
  const dict = await loadDictionary();
  try {
    const [info, status] = await Promise.all([warInfo(), warStatus()]);
    refreshSummary();
    const planets = buildPlanets(A, info, status, summaryCache);
    const byIndex = new Map(planets.map((p) => [p.index, p]));
    const campaigns = status.campaigns.map((c) => ({ id: c.id, type: c.type, faction: RACES[c.race] || "", planet: byIndex.get(c.planetIndex) })).filter((c) => c.planet);
    const effIds = {};
    (status.planetActiveEffects || []).forEach((e) => (effIds[e.index] ??= []).push(e.galacticEffectId));
    const effects = new Map(Object.entries(effIds).map(([i, ids]) => [Number(i), resolveEffects(A, dict, ids)]).filter(([, l]) => l.length));
    const station = (status.spaceStations || []).find((s) => s.planetIndex >= 0);
    const now = Date.now();
    // Record trend samples only when the game clock moved (it ticks every 10 s).
    if (status.time !== war.statusTime) {
      for (const c of campaigns) {
        const p = c.planet;
        record(keys.planet(p.index), liberation(p.health, p.maxHealth), now);
        if (p.event) record(keys.event(p.index), liberation(p.event.health, p.event.maxHealth), now);
        p.regions.forEach((r) => r.health != null && record(keys.region(p.index, r.id), liberation(r.health, r.maxHealth), now));
      }
    }
    Object.assign(war, {
      planets, byIndex, campaigns, effects, statusTime: status.time, updatedAt: now, error: null,
      dss: station ? { planet: station.planetIndex, electionEnd: new Date(now + (station.currentElectionEndWarTime - status.time) * 1000) } : null,
    });
    seedHistory(campaigns.map((c) => c.planet));
  } catch (e) {
    war.error = e.message || String(e);
  }
  emit();
}

/** Orders in the app's shape, from the community API (localised) or Arrowhead's raw format. */
function normalizeOrder(a) {
  if (a.setting) {
    return {
      id: a.id32, progress: a.progress || [], title: a.setting.overrideTitle, briefing: a.setting.overrideBrief,
      description: a.setting.taskDescription, tasks: a.setting.tasks || [], reward: a.setting.reward,
      rewards: (a.setting.rewards || []).filter(Boolean), expiration: Date.now() + a.expiresIn * 1000,
    };
  }
  return { ...a, expiration: Date.parse(a.expiration) };
}
export async function refreshOrders() {
  try {
    let raw;
    try { raw = await community("/api/v1/assignments"); } catch { raw = await getJSON(HDTM + "/api/v1/war/major-orders"); }
    war.assignments = raw.map(normalizeOrder);
    war.assignments.forEach((a) => a.progress?.forEach((v, i) => record(keys.task(a.id, i), Number(v))));
    war.ordersLoaded = true;
  } catch (e) { war.ordersError = e.message; }
  emit();
}

/** News: shown at once from the browser's copy, refreshed in the background (one request). */
export async function refreshNews() {
  const key = "news:" + settings.lang;
  if (!war.dispatches.length) {
    const cached = stored(key, 7 * 86400e3);
    if (cached) { war.dispatches = cached; emit(); }
  }
  try {
    const feed = await community("/api/v1/dispatches");
    war.dispatches = feed
      .filter((d) => d.message)
      .map((d) => ({ id: d.id, date: Date.parse(d.published), message: d.message }))
      .sort((a, b) => b.date - a.date)
      .slice(0, 200);
    store(key, war.dispatches);
  } catch (e) { war.newsError = e.message; }
  war.newsLoaded = true;
  emit();
}

export async function refreshStations() {
  try {
    war.stations = await community("/api/v2/space-stations");
    war.stations.forEach((s) => (s.tacticalActions || []).forEach((a) => (a.costs || []).forEach((c) => record(`dss:${a.id32}:${c.id}`, c.currentValue))));
  } catch (e) { war.stationsError = e.message; }
  war.stationsLoaded = true;
  emit();
}

let timers = [];
export function start() {
  timers.forEach(clearInterval);
  refreshStatus();
  refreshNews();
  refreshOrders();
  refreshStations();
  timers = [
    setInterval(refreshStatus, 10e3),
    setInterval(refreshOrders, 60e3),
    setInterval(refreshNews, 5 * 60e3),
    setInterval(refreshStations, 60e3),
  ];
}

// ---------- art lookups (same rules as GameArt) ----------
export function icon(A, stem) {
  const f = A.icons[A.norm(stem)];
  return f ? `assets/icons/${f}` : null;
}
export function planetArt(A, planet, effects = []) {
  const base = A.norm((planet.english || "").replace(/\s*\(.*\)/, ""));
  const variants = effects.flatMap((e) => {
    const n = e.original.toUpperCase();
    const ex = n.match(/CLASS (\d) EXOSTORM/);
    if (ex) return ["exostormc" + ex[1]];
    if (n.includes("GLOOM")) return ["gloom"];
    if (n.includes("BLACK HOLE")) return ["blackhole"];
    if (n.includes("VOID")) return ["void"];
    return [];
  });
  for (const k of [...variants.map((v) => base + v), base]) if (A.planetArt[k]) return `assets/planets/${A.planetArt[k]}`;
  return null;
}
export function effectIcon(A, effect) {
  const name = effect.original.toUpperCase();
  const hit = Object.entries(A.index.effectIcons).find(([k]) => name.includes(k));
  if (hit) return icon(A, hit[1]);
  if (name.startsWith("ARSENAL AUGMENTATION:")) {
    const strat = name.split(":")[1].trim();
    const noModel = strat.replace(/^\S*\d\S*\s+/, "");
    return icon(A, strat + "_Stratagem_Icon") || icon(A, noModel + "_Stratagem_Icon") || icon(A, "Mission_Stratagem_Fallback_Icon");
  }
  return null;
}
export function conditions(A, index) {
  const pl = settings.lang === "pl";
  return (A.conditions[index]?.conditions || []).map((key) => {
    const t = A.terms.hazards[key];
    const text = (pl && t?.pl) || t?.en;
    if (!text) return null;
    const singular = (s) => (s.endsWith("oes") ? s.slice(0, -2) : s.endsWith("s") ? s.slice(0, -1) : s);
    const iconFile = Object.entries(A.icons).find(([k]) => k.endsWith("environmentalconditionicon") && singular(k.replace("environmentalconditionicon", "")) === singular(A.norm(t.en?.name || key)));
    return { key, name: text.name, description: text.description || t.en?.description, icon: iconFile ? `assets/icons/${iconFile[1]}` : null };
  }).filter(Boolean);
}
export function biome(A, index) {
  const key = A.conditions[index]?.biome;
  const t = key && A.terms.biomes[key];
  return t ? ((settings.lang === "pl" && t.pl) || t.en) : null;
}
