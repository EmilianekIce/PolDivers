// PolDivers website: a port of the Android app's screens (same texts, icons, colours and logic),
// laid out for large screens.
import * as D from "./data.js";
import { GalaxyMap, buildModel } from "./map.js";
import * as W from "./wiki.js";

const { war, settings } = D;
/** Interface language: follows the data language setting (switching it reloads the page). */
const EN = settings.lang === "en";
const T = (pl, en) => (EN ? en : pl);
const LOCALE = EN ? "en-US" : "pl-PL";
let A; // bundled assets

// ---------- palette & labels (ui/theme/Color.kt, ui/common/Faction.kt) ----------
const C = { yellow: "#FFC400", human: "#4FA3E0", terminid: "#E8A33D", automaton: "#E0483E", illuminate: "#8A5CF6", green: "#3FBF6A", red: "#E0483E", muted: "#9AA0A8", orange: "#FF7A45" };
const factionColor = (o) => ({ Humans: C.human, Terminids: C.terminid, Automaton: C.automaton, Illuminate: C.illuminate }[o] || C.muted);
const factionLabel = (o) => ((EN ? { Humans: "Super Earth", Terminids: "Terminids", Automaton: "Automatons", Illuminate: "Illuminate" } : { Humans: "Super Ziemia", Terminids: "Terminidzi", Automaton: "Automatony", Illuminate: "Iluminaci" })[o] || o || T("Nieznana", "Unknown"));
const EFFECT_COLOR = { enemy: C.orange, hazard: "#B58CFF", support: "#58C4FF", site: "#B0B6BE", other: "#B0B6BE" };

// ---------- formatting (ui/common/NumberFormat.kt, TimeFormat.kt) ----------
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const $ = (sel, root = document) => root.querySelector(sel);
const formatNumber = (n) => new Intl.NumberFormat(LOCALE).format(Math.round(n ?? 0));
const formatPercent = (v, d = 2) => (v ?? 0).toLocaleString(LOCALE, { minimumFractionDigits: d, maximumFractionDigits: d });
function formatCompact(v) {
  const f = (x) => x.toLocaleString(LOCALE, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return v >= 1e9 ? `${f(v / 1e9)} ${T("mld", "B")}` : v >= 1e6 ? `${f(v / 1e6)} ${T("mln", "M")}` : v >= 1e4 ? `${f(v / 1e3)} ${T("tys.", "K")}` : formatNumber(v);
}
function formatSeconds(s) {
  if (s == null || !Number.isFinite(s) || s < 0) return "0s";
  s = Math.floor(s);
  const d = Math.floor(s / 86400), h = Math.floor(s / 3600) % 24, m = Math.floor(s / 60) % 60, sec = s % 60;
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}min` : m > 0 ? `${m}min ${sec}s` : `${sec}s`;
}
const formatRemaining = (ms) => (ms == null ? "?" : ms - Date.now() < 0 ? T("zakończone", "ended") : formatSeconds((ms - Date.now()) / 1000));
function formatClockIn(seconds) {
  const t = new Date(Date.now() + seconds * 1000);
  const hm = t.toLocaleTimeString(LOCALE, { hour: "2-digit", minute: "2-digit" });
  return t.toDateString() === new Date().toDateString() ? hm : `${t.toLocaleDateString(LOCALE, { weekday: "short" })} ${hm}`;
}
function formatAgo(ms) {
  if (!ms) return "";
  const e = Date.now() - ms;
  if (e < 0) return T("teraz", "now");
  const d = Math.floor(e / 86400e3), h = Math.floor(e / 3600e3) % 24, m = Math.floor(e / 60e3) % 60;
  return d > 0 ? (EN ? `${d}d ago` : `${d} d temu`) : h > 0 ? (EN ? `${h}h ago` : `${h} godz. temu`) : (EN ? `${m} min ago` : `${m} min temu`);
}

// ---------- art ----------
const iconUrl = (stem) => D.icon(A, stem);
/** Absolute URL for CSS masks (a relative url() inside a custom property resolves against the stylesheet). */
const maskIcon = (stem) => `--icon:url('${new URL(iconUrl(stem) || "", location.href).href}')`;
const gameIcon = (stem, size = 24, extra = "") => { const u = iconUrl(stem); return u ? `<img class="gi" src="${u}" style="width:${size}px;height:${size}px" alt="" ${extra}>` : ""; };
const factionIconUrl = (o) => ({ Humans: "assets/faction_humans.webp", Terminids: "assets/faction_terminids.webp", Automaton: "assets/faction_automaton.webp", Illuminate: "assets/faction_illuminate.webp" }[o]);
const factionDot = (o, size = 22) => (factionIconUrl(o) ? `<img src="${factionIconUrl(o)}" style="width:${size}px;height:${size}px" alt="${esc(factionLabel(o))}" title="${esc(factionLabel(o))}">` : `<span class="dot" style="width:${size / 2}px;height:${size / 2}px;background:${factionColor(o)}"></span>`);
const playerCount = (n, color = "", small = false) => `<span class="pc${small ? " small" : ""}" style="color:${color}">${gameIcon("Helmet_Currency_Icon", small ? 12 : 15)}${formatCompact(n)}</span>`;
const tag = (text, color) => `<span class="tag" style="background:${color}">${esc(text)}</span>`;
function taskIconStem(type, faction) {
  if (type === 3) return { Terminids: "Eradicate_Terminid_Swarm_Mission_Icon", Automaton: "Eradicate_Automaton_Forces_Mission_Icon", Illuminate: "Destroy_Illuminate_Warp_Ships_Mission_Icon" }[faction] || "Eradicate_Terminid_Swarm_Mission_Icon";
  return { 12: "Defense_Campaign_Icon", 11: "Liberation_Campaign_Icon", 15: "Liberation_Campaign_Icon", 13: "Locations_Icon", 2: "Common_Sample_Icon" }[type] || "Operation_Icon";
}
const rewardIconStem = (t) => ({ 1: "Medal", 2: "Super_Credit", 3: "Common_Sample_Icon", 4: "Requisition_Slip" }[t] || "Medal");
const wikiRewardIconStem = (t) => ({ medal: "Medal", medals: "Medal", stratagem: "Stratagem_Permit", "primary-weapon": "Muzzle_Icon", "secondary-weapon": "Muzzle_Icon", weapon: "Muzzle_Icon", armor: "Helldiver_Icon", helmet: "Helldiver_Icon", "super-credits": "Super_Credit", requisition: "Requisition_Slip" }[t] || "Badge");
function dssActionIconStem(name) {
  const n = (name || "").toUpperCase();
  return n.includes("EAGLE") ? "DSS_Eagle_Icon" : n.includes("BLOCKADE") ? "DSS_Orbital_Blockade_Icon" : n.includes("BOMBARDMENT") ? "DSS_Planetary_Bombardment_Icon" : n.includes("ORDNANCE") ? "DSS_Heavy_Ordnance_Distribution_Icon" : "DSS_Action_Fallback_Icon";
}

/** Game markup (<i=1>…</i>) -> HTML; planet names become links to their details. */
function gameText(raw) {
  let html = esc(raw || "").replace(/&lt;i=(\d)&gt;([\s\S]*?)&lt;\/i&gt;/g, (_, k, body) => (k === "3" ? `<b>${body}</b>` : `<span class="hl">${body}</span>`));
  const names = war.planets.map((p) => p.name).filter((n) => n.length >= 3).sort((a, b) => b.length - a.length);
  if (names.length) {
    const re = new RegExp(`(?<![\\p{L}\\d])(${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})(?![\\p{L}\\d])`, "giu");
    html = html.replace(re, (m) => `<span class="hl link" data-planet-name="${esc(m.toLowerCase())}">${m}</span>`);
  }
  return html;
}

// ---------- projections (core/trends/Projection.kt) ----------
const EPS = 0.01;
function projection(percent, rate, secondsLeft) {
  const p = { percent, rate, secondsLeft };
  p.eta = rate == null ? null : percent >= 100 ? 0 : rate <= EPS ? null : ((100 - percent) / rate) * 3600;
  p.lossEta = rate == null || rate >= -EPS || percent <= 0 ? null : (percent / -rate) * 3600;
  p.projected = rate == null || secondsLeft == null ? null : Math.max(0, percent + (rate * secondsLeft) / 3600);
  p.atDeadline = p.projected == null ? null : Math.min(100, p.projected);
  p.required = secondsLeft > 0 ? Math.max(0, 100 - percent) / (secondsLeft / 3600) : null;
  p.outcome = percent >= 100 ? "done" : rate == null ? "unknown" : secondsLeft == null ? (rate > EPS ? "ontrack" : "stalled") : (p.atDeadline ?? 0) >= 100 ? "ontrack" : "failing";
  return p;
}
function planetProjection(p) {
  if (p.event) {
    const key = D.keys.event(p.index), rate = D.ratePerHour(key);
    return projection(D.liveValue(key, D.liberation(p.event.health, p.event.maxHealth), rate), rate, Math.max(0, (p.event.end - Date.now()) / 1000));
  }
  const key = D.keys.planet(p.index), rate = D.ratePerHour(key);
  return projection(D.liveValue(key, D.liberation(p.health, p.maxHealth), rate), rate, null);
}
const resistancePerHour = (p) => (p.maxHealth > 0 ? (p.regenPerSecond * 3600) / p.maxHealth * 100 : 0);
function leadingRegion(p) {
  const named = p.regions.filter((r) => r.name);
  const pool = named.filter((r) => r.available).length ? named.filter((r) => r.available) : named.filter((r) => r.health != null && D.liberation(r.health, r.maxHealth) > 0);
  return pool.sort((a, b) => D.liberation(b.health ?? b.maxHealth, b.maxHealth) - D.liberation(a.health ?? a.maxHealth, a.maxHealth) || b.players - a.players)[0];
}

// ---------- planet components (feature/planets/PlanetComponents.kt) ----------
const small = (text, color = C.muted, extra = "") => `<div class="lbl" style="color:${color}" ${extra}>${text}</div>`;
function rateText(rate) {
  if (rate == null) return `<span class="lbl" style="color:${C.muted}">${T("tempo: liczę…", "pace: calculating…")}</span>`;
  if (rate > EPS) return `<span class="lbl b" style="color:${C.green}">+${formatPercent(rate)}%/h</span>`;
  if (rate < -EPS) return `<span class="lbl b" style="color:${C.red}">${formatPercent(rate)}%/h</span>`;
  return `<span class="lbl b" style="color:${C.muted}">0%/h</span>`;
}
const bar = (percent, color, track) => `<div class="bar" style="--fill:${color};--track:${track}"><i style="width:${Math.max(0, Math.min(100, percent))}%"></i></div>`;
function liberationOutlook(pr, what = T("Wyzwolenie", "Liberation"), lost = T("Front się cofa — wróg odbije wszystko", "Front is falling back — enemy retakes it all")) {
  if (pr.rate == null || pr.percent >= 100) return "";
  if (pr.eta != null) return small(`${what} ${T("za", "in")} ~${formatSeconds(pr.eta)} (≈ ${formatClockIn(pr.eta)})`, C.green);
  if (pr.lossEta != null) return small(`${lost} ${T("za", "in")} ~${formatSeconds(pr.lossEta)} (≈ ${formatClockIn(pr.lossEta)})`, C.red);
  if (pr.rate < -EPS) return small(T("Wróg odbija teren — front się cofa", "Enemy is retaking ground — front falling back"), C.red);
  return small(T("Front stoi w miejscu", "Front is holding still"));
}
function resistanceLine(p) {
  if (p.owner === "Humans" || p.maxHealth <= 0) return "";
  const r = resistancePerHour(p);
  const [label, color] = r < 0 ? [T("ODCIĘTY (planeta sama się wyzwala)", "CUT OFF (planet liberates itself)"), C.green] : r >= 4 ? [T("BARDZO WYSOKI", "VERY HIGH"), C.red] : r >= 2.5 ? [T("WYSOKI", "HIGH"), C.orange] : r >= 1.5 ? [T("ŚREDNI", "MEDIUM"), C.yellow] : r > 0 ? [T("NISKI", "LOW"), C.green] : [T("BRAK", "NONE"), C.muted];
  return `<div class="row between">${small(`${T("Opór wroga", "Enemy resistance")}: ${label}`, color)}${small(r < 0 ? `+${formatPercent(-r)}%/h` : `−${formatPercent(r)}%/h`, color)}</div>`;
}
function defenseOutlook(pr) {
  const left = pr.secondsLeft;
  let out = small(left > 0 ? `${T("Koniec obrony za", "Defense ends in")} ${formatSeconds(left)} (≈ ${formatClockIn(left)})` : T("Obrona dobiega końca", "Defense is ending"));
  if (pr.outcome === "ontrack") out += small(EN ? `Defense won in ~${pr.eta != null ? formatSeconds(pr.eta) : "?"} — we will make it` : `Obrona utrzymana za ~${pr.eta != null ? formatSeconds(pr.eta) : "?"} — zdążymy`, C.green);
  else if (pr.outcome === "failing") {
    out += small(`${EN ? `At this pace: ${formatPercent(pr.atDeadline ?? pr.percent, 1)}% at the end — the planet falls` : `Przy tym tempie: ${formatPercent(pr.atDeadline ?? pr.percent, 1)}% na koniec — planeta padnie`}${left > 0 ? ` ${T("za", "in")} ${formatSeconds(left)}` : ""}${pr.required != null ? `${T(". Potrzeba ≥ ", ". Needs ≥ ")}${formatPercent(pr.required)}%/h` : ""}`, C.red);
  } else if (pr.outcome === "unknown" && pr.required != null) out += small(`${T("Potrzebne tempo", "Required pace")}: ${formatPercent(pr.required)}%/h`);
  return out;
}
function gambitOutlook(defended, defense) {
  const attackers = war.planets.filter((a) => a.attacking.includes(defended.index) && a.owner !== "Humans");
  if (!attackers.length || defense.secondsLeft == null) return "";
  return attackers.map((a) => {
    const lib = planetProjection(a);
    const required = defense.secondsLeft > 0 ? (100 - lib.percent) / (defense.secondsLeft / 3600) : null;
    const [text, color] = defense.outcome === "ontrack" ? [T("niepotrzebny — obrona utrzyma się sama", "not needed — the defense will hold"), C.muted]
      : lib.eta != null && lib.eta < defense.secondsLeft ? [EN ? `WORTH IT — liberation in ~${formatSeconds(lib.eta)}, before the defense ends` : `MA SENS — wyzwolenie za ~${formatSeconds(lib.eta)}, przed końcem obrony`, C.green]
      : lib.rate == null ? [EN ? `calculating ${esc(a.name)} liberation pace…` : `liczę tempo wyzwolenia ${esc(a.name)}…`, C.muted]
      : [EN ? `too slow — needs ≥ ${formatPercent(required ?? 0)}%/h on ${esc(a.name)}` : `za wolno — potrzeba ≥ ${formatPercent(required ?? 0)}%/h na ${esc(a.name)}`, C.red];
    return small(EN ? `GAMBIT: liberate ${esc(a.name)} (${formatPercent(lib.percent, 1)}%), where the attack comes from` : `GAMBIT: wyzwól ${esc(a.name)} (${formatPercent(lib.percent, 1)}%), skąd idzie atak`, C.yellow) + small(text, color);
  }).join("");
}
/** Liberation or defense meter with pace and outlook -- re-rendered every second. */
function planetProgress(p, showRegion = true) {
  const pr = planetProjection(p);
  if (p.event) {
    return bar(pr.percent, C.human, factionColor(p.event.faction) + "73") +
      `<div class="row between">${small(`${T("Obrona", "Defense")}: ${formatPercent(pr.percent, pr.rate != null ? 4 : 2)}%`)}${rateText(pr.rate)}</div>` +
      defenseOutlook(pr) + gambitOutlook(p, pr);
  }
  if (p.owner === "Humans") return `<div class="lbl-l" style="color:${C.human}">${T("✓ WYZWOLONA — pod kontrolą Super Ziemi", "✓ LIBERATED — under Super Earth control")}</div>`;
  let html = bar(pr.percent, C.human, factionColor(p.owner) + "73") +
    `<div class="row between">${small(`${T("Wyzwolenie", "Liberation")}: ${formatPercent(pr.percent, pr.rate != null ? 4 : 2)}%`)}${rateText(pr.rate)}</div>` +
    liberationOutlook(pr) + resistanceLine(p);
  const region = leadingRegion(p);
  if (showRegion && region && region.health != null && D.liberation(region.health, region.maxHealth) > 0 && D.liberation(p.health, p.maxHealth) < 0.01) {
    const key = D.keys.region(p.index, region.id), rate = D.ratePerHour(key);
    const rp = projection(D.liveValue(key, D.liberation(region.health, region.maxHealth), rate), rate, null);
    html += `<div class="row between">${small(`Region ${esc(region.name)}: ${formatPercent(rp.percent)}%`)}${rateText(rate)}</div>` + liberationOutlook(rp, T("Zdobycie regionu", "Region captured"), T("Wróg odbije region", "Enemy retakes the region"));
  }
  return html;
}
const progressSlot = (p, showRegion = true) => `<div class="prog" data-progress="${p.index}" data-region="${showRegion ? 1 : 0}">${planetProgress(p, showRegion)}</div>`;
function effectTags(effects) {
  const shown = effects.filter((e) => e.kind !== "support" && e.kind !== "other");
  return shown.length ? `<div class="flow">${shown.map((e) => `<span class="row gap3">${D.effectIcon(A, e) ? `<img class="gi" src="${D.effectIcon(A, e)}" style="width:18px;height:18px">` : ""}${tag(e.name.split(":").pop().trim().toUpperCase(), EFFECT_COLOR[e.kind])}</span>`).join("")}</div>` : "";
}
const isMoTarget = (i) => moTargets().has(i);
function moTargets() {
  return new Set((war.assignments || []).flatMap((a) => (a.tasks || []).map((t) => targetPlanet(t)).filter((v) => v != null)));
}
function targetPlanet(t) {
  if (![11, 12, 13].includes(t.type)) return null;
  const i = t.valueTypes?.indexOf(12);
  if (i == null || i < 0) return null;
  const idx = Number(t.values[i]);
  const lt = t.valueTypes.indexOf(11);
  if (idx === 0 && (lt < 0 || Number(t.values[lt]) === 0)) return null;
  return idx;
}
function planetCard(p, i = 0) {
  const effects = war.effects.get(p.index) || [];
  const art = D.planetArt(A, p, effects);
  const accent = factionColor(p.event?.faction || p.owner);
  const mo = isMoTarget(p.index), dss = war.dss?.planet === p.index;
  return `<div class="card click appear${p.event || mo ? " glow-box" : ""}" style="--i:${Math.min(i, 12)};--accent:${accent}" data-planet="${p.index}">
    <div class="row gap10">
      <div class="art46">${art ? `<img src="${art}" alt=""><span class="fd">${factionDot(p.owner, 18)}</span>` : factionDot(p.owner, 30)}</div>
      <div class="grow"><div class="title-m ellipsis">${esc(p.name)}</div><div class="body-m muted">${esc(p.sector)}</div></div>
      <div class="col-end">${playerCount(p.players, factionColor(p.owner))}
        <div class="row gap4">${p.event ? tag(T("OBRONA", "DEFENSE"), C.red) : ""}${mo ? tag(T("ROZKAZ", "ORDER"), C.yellow) : ""}${dss ? tag("DSS", C.yellow) : ""}</div></div>
    </div>
    ${effectTags(effects)}
    ${p.owner !== "Humans" || p.event ? progressSlot(p) : ""}
  </div>`;
}

// ---------- planet details (PlanetDetailSheet) ----------
const REGION_SIZE = EN ? { settlement: "Settlement", town: "Town", city: "City", megacity: "Megacity" } : { settlement: "Osada", town: "Miasteczko", city: "Miasto", megacity: "Megamiasto" };
const section = (t) => `<div class="section"><hr><div class="lbl-l" style="color:${C.yellow}">${t}</div></div>`;
const statLine = (l, v) => `<div class="stat"><span>${l}</span><b>${v}</b></div>`;
function planetDetails(p) {
  const effects = war.effects.get(p.index) || [];
  const art = D.planetArt(A, p, effects);
  const conds = D.conditions(A, p.index);
  const biome = D.biome(A, p.index);
  const s = p.stats;
  const KIND = EN ? { enemy: "ENEMY", hazard: "HAZARD", support: "SUPPORT", site: "SITE", other: "EFFECT" } : { enemy: "WRÓG", hazard: "ZAGROŻENIE", support: "WSPARCIE", site: "OBIEKT", other: "EFEKT" };
  return `
    <div class="row gap12">
      <div class="sheet-art zoom-in">${factionDot(p.owner, 40)}${art ? `<img class="spin" src="${art}" alt="">` : ""}</div>
      <div class="appear" style="--i:1"><div class="headline-m">${esc(p.name.toUpperCase())}</div><div class="body-m muted">${T("Sektor", "Sector")} ${esc(p.sector)} · ${factionLabel(p.owner)}</div></div>
    </div>
    <div class="flow" style="margin-top:12px">${p.event ? tag(`${T("OBRONA przed", "DEFENSE vs")}: ${factionLabel(p.event.faction)}`, C.red) : ""}${isMoTarget(p.index) ? tag(T("CEL ROZKAZU", "ORDER TARGET"), C.yellow) : ""}${war.dss?.planet === p.index ? tag(T("DSS NA ORBICIE", "DSS IN ORBIT"), C.yellow) : ""}</div>
    ${effects.length ? section(T("MODYFIKATORY PLANETY", "PLANET MODIFIERS")) + effects.map((e) => `<div class="effect">
        <div class="row gap8">${D.effectIcon(A, e) ? `<img class="gi" src="${D.effectIcon(A, e)}" style="width:32px;height:32px">` : ""}${tag(KIND[e.kind], EFFECT_COLOR[e.kind])}<b>${esc(e.name)}</b></div>
        ${e.name !== e.original ? small(esc(e.original)) : ""}
        ${e.description ? `<div class="body-m muted">${gameText(e.description)}</div>` : ""}</div>`).join("") + section(T("STAN", "STATUS")) : ""}
    ${statLine(T("Helldiverów na planecie", "Helldivers on planet"), formatNumber(p.players))}
    ${p.maxHealth > 0 ? statLine(T("HP planety", "Planet HP"), `${formatNumber(p.health)} / ${formatNumber(p.maxHealth)}`) : ""}
    ${p.event?.maxHealth > 0 ? statLine(T("HP obrony", "Defense HP"), `${formatNumber(p.event.health)} / ${formatNumber(p.event.maxHealth)}`) : ""}
    ${progressSlot(p)}
    ${biome ? section(T("BIOM", "BIOME")) + `<div class="body-l">${esc(biome.name)}</div><div class="body-m muted">${gameText(biome.description || "")}</div>` : ""}
    ${conds.length ? section(T("WARUNKI ŚRODOWISKOWE", "ENVIRONMENTAL CONDITIONS")) + conds.map((c) => `<div class="row gap10 cond">${c.icon ? `<img class="gi" src="${c.icon}" style="width:32px;height:32px">` : ""}<div class="grow"><div class="body-l">${esc(c.name)}</div><div class="body-m muted">${gameText(c.description || "")}</div></div></div>`).join("") : ""}
    ${p.regions.length ? section(T("MIASTA I REGIONY", "CITIES AND REGIONS")) + [...p.regions].sort((a, b) => D.liberation(b.health ?? b.maxHealth, b.maxHealth) - D.liberation(a.health ?? a.maxHealth, a.maxHealth)).map((r) => {
      const v = r.health == null ? null : D.liberation(r.health, r.maxHealth);
      const captured = v != null && v >= 99.95;
      const sub = [REGION_SIZE[(r.size || "").toLowerCase()], !r.available && !captured ? T("zablokowany", "locked") : null].filter(Boolean).join(" · ");
      return `<div class="row gap8 region"><div class="grow"><div class="body-m">${esc(r.name || "Region " + r.id)}</div>${small(sub)}</div>
        ${captured ? tag(T("ZDOBYTE", "CAPTURED"), C.green) : small([v != null ? `${formatPercent(v, 1)}%` : null, r.players > 0 ? `${formatCompact(r.players)} ${T("graczy", "players")}` : null].filter(Boolean).join(" · "))}</div>`;
    }).join("") : ""}
    ${s ? section(T("STATYSTYKI", "STATISTICS")) + statLine(T("Misje wygrane / przegrane", "Missions won / lost"), `${formatNumber(s.missionsWon)} / ${formatNumber(s.missionsLost)}`) + statLine(T("Skuteczność misji", "Mission success rate"), `${s.missionSuccessRate}%`)
      + statLine(T("Zabici wrogowie", "Enemies killed"), formatNumber(s.bugKills + s.automatonKills + s.illuminateKills)) + statLine(T("Poległi Helldiverzy", "Helldivers lost"), formatNumber(s.deaths))
      + statLine(T("Ogień bratobójczy", "Friendly fire"), formatNumber(s.friendlies)) + statLine(T("Celność", "Accuracy"), `${s.accurracy}%`) : ""}`;
}

// ---------- drawer / sheet ----------
function openDrawer(html) {
  $("#drawer-body").innerHTML = html;
  $("#drawer").classList.add("open");
  $("#scrim").classList.add("on");
  $("#drawer").scrollTop = 0;
}
function closeDrawer() {
  $("#drawer").classList.remove("open");
  $("#scrim").classList.remove("on");
  map?.deselect();
}
const openPlanet = (p) => { if (p) { map?.focus(p.index); openDrawer(planetDetails(p)); } };

// ---------- live refresh every second (projections, countdowns) ----------
setInterval(() => {
  document.querySelectorAll("[data-progress]").forEach((el) => {
    const p = war.byIndex.get(Number(el.dataset.progress));
    if (p) el.innerHTML = planetProgress(p, el.dataset.region === "1");
  });
  document.querySelectorAll("[data-until]").forEach((el) => (el.textContent = formatRemaining(Number(el.dataset.until))));
  document.querySelectorAll("[data-order]").forEach((el) => {
    const a = war.assignments.find((x) => String(x.id) === el.dataset.order);
    if (a) el.innerHTML = orderBody(a, el.dataset.text === "1");
  });
}, 1000);

// ---------- state ----------
const ui = Object.assign({ tab: "planets", view: "list", map3d: true, sort: "players", activeOnly: false, hideOurs: true, query: "", campTab: "orders", shownNews: 6 },
  JSON.parse(sessionStorage.getItem("ui") || "{}"));
const save = () => sessionStorage.setItem("ui", JSON.stringify(ui));
let map = null;

// ---------- 3D map (assets/map3d/galaxy3d.js, the same renderer the app uses) ----------
let G3D = null;
const g3dReady = import("../assets/map3d/galaxy3d.js")
  .then((m) => (G3D = m.webglAvailable() ? m : null))
  .catch((e) => { console.warn("3D map unavailable", e); return null; });
/** Wraps the shared renderer in the 2D map's interface; planets go in and out as the app's. */
class Map3D {
  constructor(el, _assets, { onSelect, onHover }) {
    this.g = new G3D.Galaxy3D(el, {
      anim: settings.anim,
      onSelect: (mp) => onSelect(war.byIndex.get(mp.i)),
      onHover: (mp, pos) => onHover(mp ? war.byIndex.get(mp.i) : null, pos),
    });
  }
  set hideOurs(v) { this.g.hideOurs = v; }
  setHideOurs(v) { this.g.setHideOurs(v); }
  setData(war) { this.g.setModel(buildModel(war, A)); }
  focus(i) { this.g.focus(i); }
  reset() { this.g.reset(); }
  deselect() { this.g.deselect(); }
  destroy() { this.g.destroy(); }
}
let unsub = null;

const quietOurs = (p) => p.index !== 0 && p.owner === "Humans" && !p.event && !war.campaigns.some((c) => c.planet.index === p.index);
const SORTS = {
  players: [T("Gracze", "Players"), (a, b) => b.players - a.players],
  liberation: [T("Wyzwolenie", "Liberation"), (a, b) => (b.event ? D.liberation(b.event.health, b.event.maxHealth) : D.liberation(b.health, b.maxHealth)) - (a.event ? D.liberation(a.event.health, a.event.maxHealth) : D.liberation(a.health, a.maxHealth)) || b.players - a.players],
  resistance: [T("Opór", "Resistance"), (a, b) => (b.owner === "Humans" ? -Infinity : resistancePerHour(b)) - (a.owner === "Humans" ? -Infinity : resistancePerHour(a))],
  name: [T("Nazwa", "Name"), (a, b) => a.name.localeCompare(b.name, LOCALE)],
  sector: [T("Sektor", "Sector"), (a, b) => a.sector.localeCompare(b.sector, LOCALE) || a.name.localeCompare(b.name, LOCALE)],
};

// ---------- Planety ----------
function planetsView(el) {
  el.innerHTML = `
    <div class="toggle-row">
      <button class="toggle" data-view="list">${T("LISTA", "LIST")}</button>
      <button class="toggle" data-view="map">${T("MAPA", "MAP")}</button>
      <button class="se-btn" id="se" title="${T("Pokaż / ukryj nasze planety", "Show / hide our planets")}"><span class="mask" style="${maskIcon("Super_Earth_Icon")}"></span></button>
    </div>
    <div class="planets-body" id="pbody"></div>`;
  const body = $("#pbody", el);
  const se = $("#se", el);
  const syncToggles = () => {
    el.querySelectorAll("[data-view]").forEach((b) => b.classList.toggle("on", b.dataset.view === ui.view));
    se.classList.toggle("on", !ui.hideOurs);
  };
  el.querySelectorAll("[data-view]").forEach((b) => (b.onclick = () => { ui.view = b.dataset.view; save(); syncToggles(); draw(true); }));
  se.onclick = () => {
    ui.hideOurs = !ui.hideOurs; save(); syncToggles();
    se.classList.remove("turn"); void se.offsetWidth; se.classList.add("turn");
    map?.setHideOurs(ui.hideOurs);
    if (ui.view === "list") draw(true);
  };
  syncToggles();
  let listKey = "";
  function draw(force) {
    if (!war.planets.length) { body.innerHTML = loader(); map = null; return; }
    if (ui.view === "map") {
      if (!map || force) {
        map?.destroy(); map = null;
        if (ui.map3d && !G3D) {
          // The 3D module is still loading (or unavailable: then fall back to 2D).
          body.innerHTML = loader();
          g3dReady.then((m) => { if (!m) ui.map3d = false; if (ui.view === "map" && body.isConnected) draw(true); });
          return;
        }
        const is3d = ui.map3d && !!G3D;
        body.innerHTML = `<div class="map-wrap view-fade${is3d ? " is3d" : ""}" id="map">
          <button class="fab" id="legend-btn" title="${T("Legenda", "Legend")}">i</button>
          <div class="fab-col">
            <button class="fab" id="reset" title="${T("Resetuj widok", "Reset view")}">⤢</button>
            ${G3D ? `<button class="fab dim" id="dim" title="${T("Przełącz 2D / 3D", "Switch 2D / 3D")}">${is3d ? "2D" : "3D"}</button>` : ""}
            ${is3d ? `<button class="fab" id="rotl" title="${T("Obróć w lewo", "Rotate left")}">⟲</button>
            <button class="fab" id="rotr" title="${T("Obróć w prawo", "Rotate right")}">⟳</button>
            <button class="fab" id="tiltu" title="${T("Pochyl (bardziej z boku)", "Tilt (more from the side)")}">◢</button>
            <button class="fab" id="tiltd" title="${T("Widok z góry", "Top-down view")}">◤</button>` : ""}
          </div>
          <div class="legend card" id="legend">${legend(is3d)}</div>
          <div class="map-tip card" id="tip"></div></div>`;
        const tip = $("#tip", body);
        map = new (is3d ? Map3D : GalaxyMap)($("#map", body), A, {
          onSelect: openPlanet,
          onHover: (p, pos) => {
            if (!p) return tip.classList.remove("on");
            tip.innerHTML = `<div class="title-s">${esc(p.name)}</div><div class="lbl" style="color:${C.muted}">${esc(p.sector)} · ${factionLabel(p.event?.faction || p.owner)}</div>
              <div class="row gap8">${playerCount(p.players, factionColor(p.owner), true)}${p.event ? tag(T("OBRONA", "DEFENSE"), C.red) : ""}</div>
              ${p.owner !== "Humans" || p.event ? `<div class="lbl">${p.event ? T("Obrona", "Defense") : T("Wyzwolenie", "Liberation")}: ${formatPercent(planetProjection(p).percent)}%</div>` : ""}`;
            tip.style.left = pos[0] + "px"; tip.style.top = pos[1] + "px";
            tip.classList.add("on");
          },
        });
        map.hideOurs = ui.hideOurs;
        window.PolDiversMap = map; // handy for debugging from the console
        $("#legend-btn", body).onclick = () => $("#legend", body).classList.toggle("on");
        $("#reset", body).onclick = () => map.reset();
        const dim = $("#dim", body);
        if (dim) dim.onclick = () => { ui.map3d = !is3d; save(); draw(true); };
        if (is3d) {
          $("#rotl", body).onclick = () => map.g.rotateBy(-Math.PI / 4);
          $("#rotr", body).onclick = () => map.g.rotateBy(Math.PI / 4);
          $("#tiltu", body).onclick = () => map.g.tiltBy(0.35);
          $("#tiltd", body).onclick = () => map.g.tiltBy(-0.35);
        }
      }
      map.setData(war);
      return;
    }
    map?.destroy(); map = null;
    const visible = war.planets.filter((p) => (!ui.activeOnly || war.campaigns.some((c) => c.planet.index === p.index)) && (!ui.hideOurs || !quietOurs(p)) &&
      (!ui.query || p.name.toLowerCase().includes(ui.query.toLowerCase()) || p.sector.toLowerCase().includes(ui.query.toLowerCase()))).sort(SORTS[ui.sort][1]);
    const key = [ui.sort, ui.activeOnly, ui.hideOurs, ui.query, visible.map((p) => p.index).join()].join("|");
    if (!force && key === listKey && $("#plist", body)) {
      $("#total", body).innerHTML = playerCount(war.planets.reduce((a, p) => a + p.players, 0), C.yellow);
      return;
    }
    listKey = key;
    const header = $("#plist-head", body);
    if (!header || force) {
      body.innerHTML = `<div class="list-head" id="plist-head">
          <div class="search-box"><svg class="search-ico" viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14"/></svg><input id="q" placeholder="${T("Szukaj planety lub sektora", "Search planet or sector")}" value="${esc(ui.query)}"><button id="qx" class="${ui.query ? "" : "hidden"}">×</button></div>
          <div class="chips"><span class="lbl muted">${T("SORTUJ:", "SORT:")}</span>${Object.entries(SORTS).map(([k, [l]]) => `<button class="chip${ui.sort === k ? " on" : ""}" data-sort="${k}">${l}</button>`).join("")}</div>
          <div class="chips"><button class="chip${!ui.activeOnly ? " on" : ""}" data-active="0">${T("Wszystkie", "All")}</button><button class="chip${ui.activeOnly ? " on" : ""}" data-active="1">${T("Aktywne fronty", "Active fronts")} (${war.campaigns.length})</button><span class="grow"></span><span id="total"></span></div>
        </div><div class="cards" id="plist"></div>`;
      $("#q", body).oninput = (e) => { ui.query = e.target.value; save(); $("#qx", body).classList.toggle("hidden", !ui.query); draw(false); };
      $("#qx", body).onclick = () => { ui.query = ""; $("#q", body).value = ""; save(); $("#qx", body).classList.add("hidden"); draw(false); };
      body.querySelectorAll("[data-sort]").forEach((b) => (b.onclick = () => { ui.sort = b.dataset.sort; save(); body.querySelectorAll("[data-sort]").forEach((x) => x.classList.toggle("on", x === b)); draw(false); }));
      body.querySelectorAll("[data-active]").forEach((b) => (b.onclick = () => { ui.activeOnly = b.dataset.active === "1"; save(); body.querySelectorAll("[data-active]").forEach((x) => x.classList.toggle("on", x === b)); draw(false); }));
    }
    $("#total", body).innerHTML = playerCount(war.planets.reduce((a, p) => a + p.players, 0), C.yellow);
    $("#plist", body).innerHTML = visible.length ? visible.map(planetCard).join("") : `<p class="muted center">${T("Brak planet spełniających kryteria.", "No planets match the filters.")}</p>`;
  }
  draw(true);
  unsub = D.subscribe(() => draw(false));
}
function legend(is3d) {
  const item = (color, label, ring) => `<span class="li"><i style="${ring ? `border:2px solid ${color}` : `background:${color}`}"></i>${label}</span>`;
  return `<div class="lbl-l" style="color:${C.yellow}">${T("LEGENDA", "LEGEND")}</div><div class="flow">
    ${item(C.human, factionLabel("Humans"))}${item(C.terminid, factionLabel("Terminids"))}${item(C.automaton, factionLabel("Automaton"))}${item(C.illuminate, factionLabel("Illuminate"))}
    ${item(C.red, T("Obrona", "Defense"), true)}${item("#fff", T("Aktywny front", "Active front"), true)}${item(C.yellow, T("Cel rozkazu", "Order target"), true)}${item(C.orange, T("Wariant wroga", "Enemy variant"))}${item("#D8A945", T("Mrok (mgła)", "Gloom (fog)"))}${item(C.yellow, "DSS")}</div>
    <div class="lbl muted">${T("Sektor ma kolor wroga, jeśli ten ma w nim choć jedną planetę; nasze sektory są przezroczyste. Linie: niebieskie = nasze, kolor wroga = jego szlaki, przejście kolorów = linia frontu. Przerywana linia = atak. Czarna dziura i gruz to zniszczone światy (Meridia, Angel's Venture, Moradesh, Ivis). Kółko myszy / szczypnięcie = przybliżenie." + (is3d ? " Mapa 3D: przeciągnij = przesuwanie, prawy przycisk albo dwa palce = obrót i pochylenie, dwuklik = przybliżenie w tym miejscu. Łuki to ataki wroga." : ""),
      "A sector takes the enemy's color if it holds at least one planet there; our sectors are transparent. Lines: blue = ours, enemy color = its routes, color blend = front line. Dashed line = attack. The black hole and rubble are destroyed worlds (Meridia, Angel's Venture, Moradesh, Ivis). Mouse wheel / pinch = zoom." + (is3d ? " 3D map: drag = pan, right button or two fingers = rotate and tilt, double click = zoom in there. Arcs are enemy attacks." : ""))}</div>`;
}

// ---------- Kampanie: Rozkazy (CampaignsScreen.kt) ----------
function taskViews(a) {
  const get = (t, vt) => { const i = t.valueTypes?.indexOf(vt); return i >= 0 ? Number(t.values[i]) : null; };
  const RACE = { 1: "Humans", 2: "Terminids", 3: "Automaton", 4: "Illuminate" };
  return (a.tasks || []).map((t, i) => {
    const raw = Number(a.progress?.[i] ?? 0);
    const goal = get(t, 3) > 0 ? get(t, 3) : null;
    const tp = targetPlanet(t);
    const planet = tp != null ? war.byIndex.get(tp) : null;
    const progress = planet && t.type !== 12 && planet.owner === "Humans" && !planet.event ? Math.max(raw, 1) : raw;
    const race = RACE[get(t, 1)];
    const faction = race || (planet ? planet.event?.faction || planet.owner : null);
    const against = race ? ` (${factionLabel(race)})` : "";
    const diff = get(t, 9) > 0 ? `${T(", poziom trudności", ", difficulty")} ${get(t, 9)}+` : "";
    const label = (EN ? {
      11: planet ? `Liberate ${planet.name}` : `Liberate planets${against}`,
      12: planet ? `Defend ${planet.name}` : `Defend planets${against}`,
      13: planet ? `Hold ${planet.name}` : "Hold the planets",
      3: `Kill enemies${against}${diff}`, 2: `Extract with resources${against}${diff}`, 7: `Complete missions${against}${diff}`,
      9: `Complete operations${against}${diff}`, 15: `Expand Super Earth territory${against}`,
    } : {
      11: planet ? `Wyzwól planetę ${planet.name}` : `Wyzwól planety${against}`,
      12: planet ? `Obroń planetę ${planet.name}` : `Obroń planety${against}`,
      13: planet ? `Utrzymaj kontrolę nad ${planet.name}` : "Utrzymaj kontrolę nad planetami",
      3: `Zlikwiduj wrogów${against}${diff}`, 2: `Ewakuuj się z zasobami${against}${diff}`, 7: `Ukończ misje${against}${diff}`,
      9: `Ukończ operacje${against}${diff}`, 15: `Poszerz terytorium Super Ziemi${against}`,
    })[t.type] || `${T("Cel", "Objective")} #${i + 1}${against}`;
    const g = planet && t.type !== 12 ? null : goal;
    const done = g ? progress >= g : progress >= 1;
    return { label, planet, progress, goal: g, task: t, faction, done, fraction: g ? Math.min(1, progress / g) : done ? 1 : 0 };
  });
}
function orderOutlook(a, views) {
  const secondsLeft = Math.max(0, (a.expiration - Date.now()) / 1000);
  const pr = views.map((v, i) => {
    if (v.done) return projection(100, 0, secondsLeft);
    if (v.planet && v.task.type !== 12) {
      const p = planetProjection(v.planet);
      return projection(v.planet.event ? D.liberation(v.planet.health, v.planet.maxHealth) : p.percent, v.planet.event ? null : p.rate, secondsLeft);
    }
    if (v.planet) { const p = planetProjection(v.planet); return projection(p.percent, p.rate, Math.min(...[p.secondsLeft, secondsLeft].filter((x) => x != null))); }
    if (v.goal) {
      const rate = D.ratePerHour(D.keys.task(a.id, i));
      return projection(Math.min(100, (v.progress / v.goal) * 100), rate == null ? null : (rate / v.goal) * 100, secondsLeft);
    }
    return projection(v.done ? 100 : 0, null, secondsLeft);
  });
  const known = pr.every((p) => p.percent >= 100 || p.rate != null);
  const predicted = !pr.length || !known ? null : pr.reduce((s, p) => s + (p.percent >= 100 ? 100 : p.projected ?? p.percent), 0) / pr.length;
  const verdict = pr.length && pr.every((p) => p.outcome === "done") ? "complete" : pr.some((p) => p.outcome === "failing") ? "risk" : pr.some((p) => p.outcome === "unknown") ? "unknown" : "ontrack";
  return { pr, predicted, verdict };
}
function orderBody(a, showText) {
  const views = taskViews(a);
  const out = orderOutlook(a, views);
  const V = { complete: [T("ROZKAZ WYKONANY", "ORDER COMPLETE"), C.green], ontrack: [T("PRZEWIDYWANY SUKCES", "SUCCESS PROJECTED"), C.green], risk: [T("ROZKAZ ZAGROŻONY", "ORDER AT RISK"), C.red], unknown: [T("ZBIERAM DANE DO PROGNOZY", "GATHERING DATA FOR FORECAST"), C.muted] }[out.verdict];
  const rewards = (a.rewards?.length ? a.rewards : [a.reward]).filter(Boolean);
  const taskRow = (v, i) => {
    const p = out.pr[i];
    let under = "";
    if (v.planet) {
      under = `<div class="card click sub" style="--accent:${v.done ? C.green : factionColor(v.planet.event?.faction || v.planet.owner)}" data-planet="${v.planet.index}">
        <div class="row gap8"><span class="lbl-l grow" style="color:${factionColor(v.planet.owner)}">${esc(v.planet.name)}</span>${playerCount(v.planet.players, "", true)}</div>
        ${v.done ? `<div class="lbl-l" style="color:${C.green}">${T("✓ CEL WYKONANY", "✓ OBJECTIVE DONE")}</div>` : planetProgress(v.planet)}</div>`;
    } else if (v.goal) {
      let outlook = "";
      if (!v.done && p) {
        const color = p.outcome === "ontrack" || p.outcome === "done" ? C.green : p.outcome === "failing" ? C.red : C.muted;
        const text = p.rate == null ? T("tempo: liczę…", "pace: calculating…") : p.rate <= EPS ? T("Brak postępu w ostatnich minutach", "No progress in the last minutes")
          : `+${formatCompact(Math.round((p.rate / 100) * v.goal))}/h${p.eta != null ? ` · ${T("cel za", "goal in")} ~${formatSeconds(p.eta)} (≈ ${formatClockIn(p.eta)})` : ""}${p.projected != null ? ` · ${T("prognoza", "forecast")} ${formatPercent(p.projected, 2)}%` : ""}`;
        outlook = small(text, color);
      }
      under = `<div class="indent">${bar(v.fraction * 100, C.yellow, "#2A2E35")}${outlook}</div>`;
    }
    const prog = v.planet && !v.done && p?.rate != null && p.projected != null ? small(`${T("Prognoza", "Forecast")}: ${formatPercent(p.projected, 2)}%`, p.projected >= 100 ? C.green : C.red, 'style="padding-left:26px"') : "";
    return `<div class="task"><div class="row gap8">
      <span class="check" style="color:${v.done ? C.green : C.muted}">${v.done ? "✔" : "○"}</span>${gameIcon(taskIconStem(v.task.type, v.faction), 26)}
      <span class="body-m grow">${esc(v.label)}</span>${v.goal ? small(`${formatNumber(v.progress)} / ${formatNumber(v.goal)}`) : ""}</div>${under}${prog}</div>`;
  };
  return `<div class="lbl-l" style="color:${C.yellow}">${esc(showText ? a.title || T("ROZKAZ GŁÓWNY", "MAJOR ORDER") : T("CELE ROZKAZU", "ORDER OBJECTIVES"))}</div>
    ${showText && a.briefing ? `<div class="body-l">${gameText(a.briefing)}</div>` : ""}
    ${showText && a.description && a.description !== a.briefing ? `<div class="body-m muted">${gameText(a.description)}</div>` : ""}
    ${views.length ? `<div class="lbl-l muted">${T("CELE", "OBJECTIVES")}: ${views.filter((v) => v.done).length} / ${views.length}</div>${views.map(taskRow).join("")}
      <div class="outlook" style="--c:${V[1]}"><div class="row between"><span class="lbl-l" style="color:${V[1]}">${V[0]}</span>${out.predicted != null ? `<span class="headline-m" style="color:${V[1]}">${formatPercent(out.predicted, 2)}%</span>` : ""}</div>
      ${out.verdict === "unknown" ? small(T("Pierwsza prognoza po kilkunastu sekundach z otwartą stroną.", "First forecast after a few seconds with the page open.")) : ""}</div>` : ""}
    <div class="row between"><span class="lbl-l muted">${T("Koniec za", "Ends in")}: ${formatRemaining(a.expiration)}</span>
      <span class="row gap10">${rewards.map((r) => `<span class="row gap4">${gameIcon(rewardIconStem(r.type), 22)}<span class="lbl-l" style="color:${C.yellow}">×${formatNumber(r.amount)}</span></span>`).join("")}</span></div>`;
}
const orderCard = (a, i = 0, showText = true) => `<div class="card glow-box order appear shine" style="--i:${i};--accent:${C.yellow}" data-order="${esc(a.id)}" data-text="${showText ? 1 : 0}">${orderBody(a, showText)}</div>`;
const isOrderDispatch = (d) => /major order|rozkaz|orders|objective/i.test(d.message);
function dispatchCard(d, i = 0) {
  const m = d.message.match(/^\s*<i=[13]>([\s\S]*?)<\/i>\s*\n+([\s\S]*)$/);
  return `<div class="card dispatch appear" style="--i:${i}">${m ? `<div class="headline-s glow" style="color:${C.yellow}">${esc(m[1])}</div>` : ""}
    <div class="body-m pre">${gameText(m ? m[2] : d.message)}</div><div class="lbl muted">${formatAgo(d.date)}</div></div>`;
}

function campaignsView(el) {
  el.innerHTML = `<div class="tabrow"><button data-sub="orders">${T("ROZKAZY", "ORDERS")}</button><button data-sub="wiki">${T("KAMPANIE WOJENNE", "WAR CAMPAIGNS")}</button></div><div id="sub"></div>`;
  const tabs = el.querySelectorAll("[data-sub]");
  const draw = () => {
    tabs.forEach((b) => b.classList.toggle("on", b.dataset.sub === ui.campTab));
    const box = $("#sub", el);
    if (ui.campTab === "orders") {
      if (!war.ordersLoaded && !war.ordersError) { box.innerHTML = loader(); return; }
      const news = war.dispatches.filter(isOrderDispatch).slice(0, 6);
      box.innerHTML = `<div class="lbl-l section-h">${T("ROZKAZY DOWÓDZTWA", "HIGH COMMAND ORDERS")}</div>
        ${war.assignments.length ? `<div class="cards wide">${war.assignments.map((a, i) => orderCard(a, i)).join("")}</div>` : `<p class="body-m muted">${T("Brak aktywnych rozkazów. Czekaj na instrukcje Dowództwa.", "No active orders. Await instructions from High Command.")}</p>`}
        ${news.length ? `<div class="lbl-l section-h">${T("KOMUNIKATY DO ROZKAZÓW", "ORDER DISPATCHES")}</div><div class="cards">${news.map((d, i) => dispatchCard(d, i + 2)).join("")}</div>` : ""}`;
    } else if (!box.dataset.wiki) {
      box.dataset.wiki = "1";
      box.innerHTML = loader();
      W.getCampaigns().then((list) => warCampaigns(box, list)).catch((e) => (box.innerHTML = `<p class="muted">${esc(e.message)}</p>`));
    }
  };
  tabs.forEach((b) => (b.onclick = () => { ui.campTab = b.dataset.sub; save(); $("#sub", el).dataset.wiki = ""; $("#sub", el).className = "view-fade"; draw(); }));
  draw();
  unsub = D.subscribe(() => ui.campTab === "orders" && draw());
}

// ---------- Kampanie wojenne (WarCampaignsTab.kt) ----------
const OUT = { success: [C.green, T("SUKCES", "SUCCESS")], failure: [C.red, T("PORAŻKA", "FAILURE")], progress: [C.yellow, T("W TOKU", "IN PROGRESS")], unknown: [C.muted, "?"] };
function campaignArt(key, phase) {
  const base = A.norm(key);
  for (const c of phase ? [base + String.fromCharCode(96 + phase), base + "phase" + phase, base] : [base]) if (A.campaignArt[c]) return `assets/campaigns/${A.campaignArt[c]}`;
  return null;
}
function wikiImage(name) {
  if (!name) return null;
  const own = A.campaignArt[A.norm(name.replace(/\.[^.]+$/, "").replace(/^Galactic[ _]War[ _]Campaigns[ _]Header[ _]/i, ""))];
  return own ? `assets/campaigns/${own}` : W.fileUrl(name);
}
const succeeded = (c) => c.phases.filter((p) => p.outcome === "success").length * 2 > c.phases.length;
const chip = (text, color, lead) => `<span class="cchip${lead ? " lead" : ""}" style="--c:${color}">${esc(text)}</span>`;
function rewardBox(caption, stem, title, amount) {
  return `${small(esc(caption))}<div class="reward card" style="--accent:${C.human}">${gameIcon(stem, 36)}<div>${title ? `<div class="lbl-m">${esc(title.toUpperCase())}</div>` : ""}<div class="title-m" style="color:${C.human}">×${amount}</div></div></div>`;
}
function warCampaigns(box, list) {
  const active = list.find((c) => c.active);
  const detail = (c) => {
    const accent = c.faction ? factionColor(c.faction) : C.yellow;
    const slots = Math.max(3, ...c.phases.map((p) => p.number));
    const by = Object.fromEntries(c.phases.map((p) => [p.number, p]));
    const status = c.active ? [T("KAMPANIA W TOKU", "CAMPAIGN IN PROGRESS"), C.yellow] : succeeded(c) ? [T("KAMPANIA UDANA", "CAMPAIGN SUCCEEDED"), C.green] : [T("KAMPANIA NIEUDANA", "CAMPAIGN FAILED"), C.red];
    const reached = Math.max(0, ...c.phases.map((p) => p.number)) - 1;
    const banner = wikiImage(c.banner) || campaignArt(c.name);
    return `<div class="zoom-in">
      <div class="display-s glow" style="color:${accent}">${esc(c.name.toUpperCase())}</div>
      <div class="row gap8" style="margin:10px 0">${chip(c.faction ? `FRONT: ${factionLabel(c.faction).toUpperCase()}` : "FRONT", accent, true)}${c.active ? chip(T("AKTYWNA KAMPANIA", "ACTIVE CAMPAIGN"), C.yellow) : succeeded(c) ? chip(T("UDANA KAMPANIA", "SUCCESSFUL CAMPAIGN"), C.green) : chip(T("NIEUDANA KAMPANIA", "FAILED CAMPAIGN"), C.red)}</div>
      ${banner ? `<img class="banner" src="${banner}" style="border-color:${accent}99" alt="">` : ""}
      ${c.description ? `<div class="body-m">${gameText(c.description)}</div>` : ""}
      ${rewardBox(T("Wykonaj większość rozkazów tej kampanii, by zdobyć nagrodę kampanii", "Complete most orders of this campaign to earn the campaign reward"), wikiRewardIconStem(c.rewardType), c.rewardText || c.rewardType, c.rewardAmount)}
    </div>
    <div class="timeline appear" style="--i:1"><div class="row gap6"><span style="color:${status[1]}">◉</span><span class="lbl-l glow" style="color:${status[1]}">${status[0]}</span></div>
      <div class="tl"><div class="tl-line"></div><div class="tl-done" style="width:${slots > 1 ? (reached / (slots - 1)) * 100 : 0}%"></div>
      ${Array.from({ length: slots }, (_, i) => `<i style="left:${slots > 1 ? (i / (slots - 1)) * 100 : 50}%;background:${by[i + 1] ? OUT[by[i + 1].outcome][0] : C.muted + "66"}"></i>`).join("")}</div>
      <div class="tl-labels">${Array.from({ length: slots }, (_, i) => `<span>${esc(by[i + 1]?.name?.toUpperCase() || `${T("FAZA", "PHASE")} ${i + 1}`)}</span>`).join("")}</div></div>
    ${[...c.phases].sort((a, b) => b.number - a.number).map((ph, i) => {
      const live = ph.outcome === "progress" ? war.assignments[0] : null;
      const img = wikiImage(ph.image || c.banner) || campaignArt(c.name, ph.number);
      return `<div class="phase appear" style="--i:${i + 2}"><hr>
        <div class="lbl" style="color:${C.yellow}">${esc(c.name.toUpperCase())} · ${T("FAZA", "PHASE")} ${ph.number}</div>
        <div class="headline-m glow" style="color:${accent}">${esc(ph.name.toUpperCase())}</div>
        <div class="row"><span class="lbl-l">${T("WYNIK", "RESULT")} – </span><span class="lbl-l" style="color:${OUT[ph.outcome][0]}">${OUT[ph.outcome][1]}</span>${ph.dateStart ? `<span class="lbl muted">&nbsp;&nbsp;&nbsp;${esc(ph.dateStart)}${ph.dateEnd ? " – " + esc(ph.dateEnd) : ""}</span>` : ""}</div>
        ${img ? `<img class="banner" src="${img}" style="border-color:${accent}80" alt="" loading="lazy">` : ""}
        ${live?.briefing ? `<div class="body-m">${gameText(live.briefing)}</div>` : ph.briefing ? `<div class="body-m">${gameText(ph.briefing)}</div>` : ""}
        ${ph.debrief ? `<div class="lbl-l muted">${T("PODSUMOWANIE", "SUMMARY")}</div><div class="body-m">${gameText(ph.debrief)}</div>` : ""}
        ${ph.rewardAmount > 0 ? rewardBox(T("Wykonaj rozkaz, by zdobyć nagrodę rozkazu", "Complete the order to earn the order reward"), wikiRewardIconStem(ph.rewardType), null, ph.rewardAmount) : ""}
        ${live ? orderCard(live, 0, false) : ""}</div>`;
    }).join("")}`;
  };
  const archiveRow = (c, i) => {
    const accent = c.faction ? factionColor(c.faction) : C.yellow;
    const img = wikiImage(c.banner) || campaignArt(c.name);
    return `<div class="archive-row click appear" style="--i:${Math.min(i, 12)}" data-camp="${esc(c.name)}"><hr><div class="row gap12">
      <div class="thumb" style="border-color:${accent}99">${img ? `<img src="${img}" alt="" loading="lazy">` : ""}</div>
      <div class="grow"><div class="headline-s glow" style="color:${accent}">${esc(c.name.toUpperCase())}</div>
      ${small([c.faction ? `FRONT: ${factionLabel(c.faction).toUpperCase()}` : null, c.phases[0]?.dateStart].filter(Boolean).map(esc).join(" · "))}
      <div class="lbl-m" style="color:${succeeded(c) ? C.green : C.red}">${succeeded(c) ? T("UDANA KAMPANIA", "SUCCESSFUL CAMPAIGN") : T("NIEUDANA KAMPANIA", "FAILED CAMPAIGN")}</div>
      <div class="row gap4">${c.phases.map((p) => `<span class="pdot" style="background:${OUT[p.outcome][0]}"></span>`).join("")}</div></div></div></div>`;
  };
  const draw = (opened) => {
    const c = opened || active;
    box.innerHTML = `${opened ? `<button class="text-btn" id="back">← ${T("Wszystkie kampanie", "All campaigns")}</button>` : ""}
      <div class="campaign">${c ? detail(c) : `<p class="body-m muted">${T("Wiki nie odnotowała jeszcze trwającej kampanii. Aktualne rozkazy są w zakładce Rozkazy.", "The wiki has not recorded an ongoing campaign yet. Current orders are in the Orders tab.")}</p>`}</div>
      ${opened ? "" : `<hr><div class="headline-l glow" style="color:#fff;margin-top:10px">${T("ARCHIWUM KAMPANII", "CAMPAIGN ARCHIVE")}</div><div class="archive-grid">${list.filter((x) => x !== active).map(archiveRow).join("")}</div>`}
      <a class="outlined-btn" href="${W.pageUrl("Campaigns")}" target="_blank" rel="noopener">📖 ${T("Źródło", "Source")}: Helldivers Wiki (CC BY-SA)</a>`;
    box.querySelectorAll("[data-camp]").forEach((b) => (b.onclick = () => { draw(list.find((x) => x.name === b.dataset.camp)); $("#view").scrollTop = 0; }));
    const back = $("#back", box);
    if (back) back.onclick = () => draw(null);
  };
  draw(null);
}

// ---------- Newsy ----------
function newsView(el) {
  const draw = () => {
    if (!war.dispatches.length) { el.innerHTML = war.newsLoaded ? `<p class="muted">${T("Brak komunikatów", "No dispatches")} (${esc(war.newsError || "")}).</p>` : loader(); return; }
    el.innerHTML = `<div class="cards">${war.dispatches.slice(0, ui.shownNews).map(dispatchCard).join("")}</div>
      ${war.dispatches.length > ui.shownNews ? `<button class="outlined-btn" id="more">${T("POKAŻ STARSZE", "SHOW OLDER")} (${war.dispatches.length - ui.shownNews})</button>` : ""}`;
    const more = $("#more", el);
    if (more) more.onclick = () => { ui.shownNews += 10; save(); draw(); };
  };
  draw();
  unsub = D.subscribe(draw);
}

// ---------- DSS (DssScreen.kt) ----------
function actionPhase(a) {
  const goals = (a.costs || []).filter((c) => c.targetValue > 0);
  const full = goals.length && goals.every((c) => c.currentValue >= c.targetValue);
  const timer = Date.parse(a.statusExpire) > Date.now();
  const donating = goals.some((c) => c.deltaPerSecond > 0 || (D.ratePerHour(`dss:${a.id32}:${c.id}`) ?? 0) > 0);
  if (goals.length && !full && donating) return "collecting";
  if (full && timer) return "active";
  if (timer && !full && !donating && goals.length && goals.every((c) => c.currentValue <= 0)) return "cooldown";
  if (goals.length && !full) return "paused";
  return timer ? "active" : "idle";
}
function actionCard(a, i) {
  const ph = actionPhase(a);
  const [label, color] = { active: [T("AKTYWNE", "ACTIVE"), C.green], collecting: [T("ZBIÓRKA ZASOBÓW", "COLLECTING RESOURCES"), C.yellow], paused: [T("ZBIÓRKA WSTRZYMANA", "COLLECTION PAUSED"), C.muted], cooldown: [T("ODNOWIENIE", "COOLDOWN"), C.muted], idle: [T("NIEAKTYWNE", "INACTIVE"), C.muted] }[ph];
  const exp = Date.parse(a.statusExpire);
  const costs = ph === "collecting" || ph === "paused" ? (a.costs || []).filter((c) => c.targetValue > 0).map((c) => {
    const f = Math.min(1, c.currentValue / c.targetValue);
    const perSec = c.deltaPerSecond > 0 ? c.deltaPerSecond : (D.ratePerHour(`dss:${a.id32}:${c.id}`) ?? 0) / 3600 || null;
    return bar(f * 100, C.yellow, "#2A2E35") + `<div class="row between">${small(`${formatNumber(c.currentValue)} / ${formatNumber(c.targetValue)} (${formatPercent(f * 100, 1)}%)`)}${perSec ? small(`+${formatCompact(perSec * 3600)}/h`, C.green) : ""}</div>` +
      (f < 1 ? (perSec ? `<div class="lbl-l" style="color:${C.yellow}">${T("Uzbierają za", "Collected in")} ~${formatSeconds((c.targetValue - c.currentValue) / perSec)} (≈ ${formatClockIn((c.targetValue - c.currentValue) / perSec)}) — ${T("wtedy działanie się aktywuje", "then the action activates")}</div>` : small(T("Czas zebrania: liczę tempo wpłat…", "Collection time: calculating donation pace…"))) : "");
  }).join("") : "";
  return `<div class="card appear${ph === "active" || ph === "collecting" ? " glow-box" : ""}" style="--i:${i};--accent:${ph === "active" ? C.green : ph === "collecting" ? C.yellow : C.muted}">
    <div class="row gap10">${gameIcon(dssActionIconStem(a.name), 40)}<span class="title-m grow">${esc(a.name)}</span>${tag(label, color)}</div>
    ${exp > Date.now() && ph !== "collecting" ? `<div class="lbl-l" style="color:${color}">${{ active: T("Działa jeszcze", "Active for"), cooldown: T("Dostępne ponownie za", "Available again in") }[ph] || T("Zmiana stanu za", "State changes in")}: <span data-until="${exp}"></span> (≈ ${formatClockIn((exp - Date.now()) / 1000)})</div>` : ""}
    ${a.strategicDescription ? `<div class="body-m">${gameText(a.strategicDescription)}</div>` : ""}
    ${a.description && a.description !== a.strategicDescription ? `<div class="body-m muted">${gameText(a.description)}</div>` : ""}
    ${costs}${ph === "paused" ? small(T("Brak wpłat — zwykle gdy inne działanie jest aktywne albo wszyscy wyczerpali dzienny limit.", "No donations — usually when another action is active or everyone has hit the daily limit.")) : ""}</div>`;
}
function dssView(el) {
  const draw = () => {
    const stations = war.stations || [];
    const header = (planet, end) => `<div class="card click glow-box zoom-in" style="--accent:${C.yellow}" data-planet="${planet.index}">
      <div class="row gap10">${gameIcon("DSS_Icon", 30)}<span class="headline-s" style="color:${C.yellow}">${T("DEMOKRATYCZNA STACJA KOSMICZNA", "DEMOCRACY SPACE STATION")}</span></div>
      <div class="row gap8">${factionDot(planet.owner)}<div class="grow"><div class="title-m">${T("Na orbicie", "In orbit")}: ${esc(planet.name)}</div><div class="body-m muted">${T("Sektor", "Sector")} ${esc(planet.sector)} · ${factionLabel(planet.owner)} · ${formatCompact(planet.players)} ${T("graczy", "players")}</div></div></div>
      ${planet.owner !== "Humans" || planet.event ? progressSlot(planet) : ""}
      <div class="center"><div class="lbl muted">${T("NASTĘPNY SKOK ZA", "NEXT JUMP IN")}</div><div class="title-l" style="color:${C.yellow}" data-until="${end}">${formatRemaining(end)}</div>
      <div class="lbl muted">${T("Cel kolejnego skoku wybierają głosy Helldiverów w grze.", "The next jump target is chosen by Helldiver votes in the game.")}</div></div></div>`;
    if (stations.length) {
      el.innerHTML = stations.map((s) => {
        const planet = war.byIndex.get(s.planet?.index) || s.planet;
        const acts = s.tacticalActions || [];
        const active = acts.filter((a) => actionPhase(a) === "active");
        const order = ["collecting", "paused", "cooldown", "idle"];
        const others = acts.filter((a) => actionPhase(a) !== "active").sort((a, b) => order.indexOf(actionPhase(a)) - order.indexOf(actionPhase(b)));
        return `${planet ? header(planet, Date.parse(s.electionEnd)) : ""}
          ${active.length ? `<div class="lbl-l section-h">${T("AKTYWNE EFEKTY", "ACTIVE EFFECTS")}</div><div class="cards">${active.map((a, i) => actionCard(a, i + 1)).join("")}</div>` : ""}
          ${others.length ? `<div class="lbl-l section-h">${T("DZIAŁANIA TAKTYCZNE", "TACTICAL ACTIONS")}</div><div class="cards">${others.map((a, i) => actionCard(a, i + 2)).join("")}</div>` : ""}`;
      }).join("");
    } else if (war.dss && war.byIndex.get(war.dss.planet)) {
      el.innerHTML = header(war.byIndex.get(war.dss.planet), +war.dss.electionEnd) +
        `<p class="lbl muted">${T("Działania taktyczne stacji są chwilowo niedostępne w API społeczności — pozycja i czas skoku pochodzą bezpośrednio ze statusu wojny.", "The station's tactical actions are temporarily unavailable in the community API — its position and jump time come straight from the war status.")}</p>`;
    } else {
      el.innerHTML = war.stationsLoaded && war.updatedAt ? `<p class="body-m muted">${T("Stacja DSS nie jest teraz rozmieszczona.", "The DSS is not deployed right now.")}</p>` : loader();
    }
  };
  draw();
  unsub = D.subscribe(draw);
}

// ---------- Archiwum: the wiki itself ----------
function archiveView(el) {
  el.innerHTML = `<div class="card glow-box archive-card zoom-in" style="--accent:${C.yellow}">
    ${gameIcon("Ministry_of_Science_Icon", 72)}
    <div class="headline-l glow" style="color:${C.yellow}">${T("ARCHIWUM", "ARCHIVE")}</div>
    <div class="body-m muted">${T("Encyklopedia Helldivers 2 prowadzona przez społeczność: stratagemy, bronie, wrogowie, planety, misje i historia wojny.", "The community-run Helldivers 2 encyclopedia: stratagems, weapons, enemies, planets, missions and the history of the war.")}</div>
    <a class="big-btn" href="https://helldivers.wiki.gg/" target="_blank" rel="noopener">${T("OTWÓRZ HELLDIVERS WIKI", "OPEN HELLDIVERS WIKI")} ↗</a></div>`;
}

// ---------- shell ----------
const TABS = [
  ["planets", T("Planety", "Planets"), "Locations_Icon", planetsView],
  ["campaigns", T("Kampanie", "Campaigns"), "Liberation_Campaign_Icon", campaignsView],
  ["news", T("Newsy", "News"), "Ministry_of_Truth_Icon", newsView],
  ["dss", "DSS", "DSS_Icon", dssView],
  ["archive", T("Archiwum", "Archive"), "Ministry_of_Science_Icon", archiveView],
];
const loader = () => `<div class="loader"><div class="radar"></div>${T("ŁĄCZENIE Z DOWÓDZTWEM…", "CONNECTING TO HIGH COMMAND…")}</div>`;
let lastTab = null;
function route() {
  const name = location.hash.replace("#/", "") || "planets";
  const tab = TABS.find((t) => t[0] === name) || TABS[0];
  unsub?.(); unsub = null;
  map?.destroy(); map = null;
  const from = TABS.findIndex((t) => t[0] === lastTab), to = TABS.indexOf(tab);
  lastTab = tab[0];
  document.querySelectorAll(".tab").forEach((a) => a.classList.toggle("active", a.dataset.tab === tab[0]));
  const title = $("#title");
  title.textContent = tab[1].toUpperCase();
  title.className = "headline-s glow " + (to >= from ? "slide-up" : "slide-down");
  const el = $("#view");
  el.className = "view " + (to >= from ? "enter-right" : "enter-left") + (tab[0] === "planets" ? " planets" : "");
  el.innerHTML = "";
  el.scrollTop = 0;
  tab[3](el);
}
function shell() {
  document.documentElement.lang = EN ? "en" : "pl";
  $("#settings").title = T("Ustawienia", "Settings");
  $("#drawer-close").setAttribute("aria-label", T("Zamknij", "Close"));
  $("#live-text").textContent = T("łączenie…", "connecting…");
  $("#tabs").innerHTML = TABS.map(([k, l, ic]) => `<a class="tab" data-tab="${k}" href="#/${k}"><span class="mask" style="${maskIcon(ic)}"></span><span>${l}</span></a>`).join("");
  document.body.classList.toggle("no-anim", !settings.anim);
  $("#settings").onclick = () => {
    const cog = $("#settings");
    cog.classList.remove("spin-once"); void cog.offsetWidth; cog.classList.add("spin-once");
    openDrawer(`<div class="headline-m" style="color:${C.yellow}">${T("USTAWIENIA", "SETTINGS")}</div>
      <div class="section"><hr><div class="lbl-l" style="color:${C.yellow}">${T("JĘZYK", "LANGUAGE")}</div></div>
      <div class="chips"><button class="chip${settings.lang === "pl" ? " on" : ""}" data-lang="pl">Polski</button><button class="chip${settings.lang === "en" ? " on" : ""}" data-lang="en">English</button></div>
      <div class="section"><hr></div>
      <label class="switch-row"><div><div class="lbl-l">${T("Animacje", "Animations")}</div>${small(T("Przejścia, odbicia przycisków, fale na mapie. Wyłącz na słabszym komputerze.", "Transitions, button bounces, map waves. Turn off on a slower computer."))}</div><input type="checkbox" id="anim-sw" ${settings.anim ? "checked" : ""}></label>
      <div class="section"><hr></div>
      ${small(T("Dane: Arrowhead (przez helldiverstrainingmanual.com i helldivers-2 API), kampanie: Helldivers Wiki (CC BY-SA). Nieoficjalna strona fanowska.", "Data: Arrowhead (via helldiverstrainingmanual.com and the helldivers-2 API), campaigns: Helldivers Wiki (CC BY-SA). Unofficial fan site."))}
      <p><a href="https://github.com/EmilianekIce/PolDivers/releases/latest" target="_blank" rel="noopener">${T("Pobierz aplikację na Androida", "Get the Android app")} ↗</a></p>`);
    document.querySelectorAll("[data-lang]").forEach((b) => (b.onclick = () => { settings.lang = b.dataset.lang; location.reload(); }));
    $("#anim-sw").onchange = (e) => { settings.anim = e.target.checked; document.body.classList.toggle("no-anim", !settings.anim); };
  };
  $("#drawer-close").onclick = closeDrawer;
  $("#scrim").onclick = closeDrawer;
  document.addEventListener("keydown", (e) => e.key === "Escape" && closeDrawer());
  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-planet], [data-planet-name]");
    if (!el) return;
    const p = el.dataset.planet != null ? war.byIndex.get(Number(el.dataset.planet)) : war.planets.find((q) => q.name.toLowerCase() === el.dataset.planetName);
    openPlanet(p);
  });
}
function liveBadge() {
  const box = $("#live");
  const ok = war.updatedAt && !war.error;
  box.className = "live " + (ok ? "ok" : war.error ? "err" : "");
  $("#live-text").textContent = ok ? `${T("na żywo", "live")} · ${new Date(war.updatedAt).toLocaleTimeString(LOCALE)}` : war.error ? `${T("brak połączenia", "no connection")}: ${war.error}` : T("łączenie…", "connecting…");
}

(async function main() {
  A = await D.loadAssets();
  shell();
  D.subscribe(liveBadge);
  window.addEventListener("hashchange", route);
  route();
  D.start();
})();
