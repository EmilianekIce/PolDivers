import * as D from "./data.js";
import { GalaxyMap } from "./map.js";
import * as W from "./wiki.js";

const { war, settings } = D;
let A; // bundled assets (loaded once)

// ---------- i18n ----------
const T = {
  pl: {
    planets: "Planety", campaigns: "Kampanie", news: "Newsy", dss: "DSS", archive: "Archiwum",
    fronts: "Fronty", all: "Wszystkie", search: "Szukaj planety lub sektora", players: "Helldiverów",
    liberation: "Wyzwolenie", defense: "Obrona", liberated: "✓ WYZWOLONA — pod kontrolą Super Ziemi",
    pacePending: "tempo: liczę…", orders: "Rozkazy", warCampaigns: "Kampanie wojenne", noOrders: "Brak aktywnych rozkazów. Czekaj na instrukcje Dowództwa.",
    older: "POKAŻ STARSZE", live: "na żywo", offline: "brak połączenia", loading: "Łączenie z Dowództwem",
    reward: "Nagroda", expires: "Koniec za", conditions: "Warunki środowiskowe", biome: "Biom", modifiers: "Modyfikatory planety",
    regions: "Miasta i regiony", stats: "Statystyki", hp: "HP planety", hpDefense: "HP obrony", ends: "Koniec obrony za",
    showOurs: "Pokaż nasze planety", hideOurs: "Ukryj nasze planety", reset: "Resetuj widok", source: "Źródło: Helldivers Wiki (CC BY-SA)",
    archiveHint: "Szukaj w wiki Helldivers albo wybierz kategorię. Artykuły ładują się dopiero po kliknięciu.",
    eta: "Wyzwolenie za", noDss: "Stacja DSS nie jest teraz rozmieszczona.",
  },
  en: {
    planets: "Planets", campaigns: "Campaigns", news: "News", dss: "DSS", archive: "Archive",
    fronts: "Fronts", all: "All", search: "Search planet or sector", players: "Helldivers",
    liberation: "Liberation", defense: "Defense", liberated: "✓ LIBERATED — held by Super Earth",
    pacePending: "pace: measuring…", orders: "Orders", warCampaigns: "War campaigns", noOrders: "No active orders. Await instructions from High Command.",
    older: "SHOW OLDER", live: "live", offline: "offline", loading: "Contacting High Command",
    reward: "Reward", expires: "Ends in", conditions: "Environmental conditions", biome: "Biome", modifiers: "Planet modifiers",
    regions: "Cities and regions", stats: "Statistics", hp: "Planet HP", hpDefense: "Defense HP", ends: "Defense ends in",
    showOurs: "Show our planets", hideOurs: "Hide our planets", reset: "Reset view", source: "Source: Helldivers Wiki (CC BY-SA)",
    archiveHint: "Search the Helldivers wiki or pick a category. Articles load only when opened.",
    eta: "Liberated in", noDss: "The DSS is not deployed right now.",
  },
};
const t = (k) => T[settings.lang][k] ?? k;
const FACTION = { pl: { Humans: "Super Ziemia", Terminids: "Terminidzi", Automaton: "Automatony", Illuminate: "Iluminaci" }, en: { Humans: "Super Earth", Terminids: "Terminids", Automaton: "Automatons", Illuminate: "Illuminate" } };
const factionLabel = (f) => FACTION[settings.lang][f] || f || "?";
const FCOLOR = { Humans: "var(--human)", Terminids: "var(--terminid)", Automaton: "var(--automaton)", Illuminate: "var(--illuminate)" };

const TABS = [
  ["planets", "Locations_Icon"], ["campaigns", "Liberation_Campaign_Icon"], ["news", "Ministry_of_Truth_Icon"],
  ["dss", "DSS_Icon"], ["archive", "Ministry_of_Science_Icon"],
];

// ---------- helpers ----------
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const $ = (sel, root = document) => root.querySelector(sel);
const num = (n) => new Intl.NumberFormat(settings.lang === "pl" ? "pl-PL" : "en-US").format(n ?? 0);
const pct = (v, d = 2) => (v ?? 0).toLocaleString(settings.lang === "pl" ? "pl-PL" : "en-US", { minimumFractionDigits: d, maximumFractionDigits: d });
function duration(ms) {
  if (ms == null || !Number.isFinite(ms)) return "?";
  if (ms <= 0) return settings.lang === "pl" ? "zakończone" : "ended";
  const m = Math.floor(ms / 60000), d = Math.floor(m / 1440), h = Math.floor((m % 1440) / 60), mm = m % 60;
  return d ? `${d} d ${h} h` : h ? `${h} h ${mm} min` : `${mm} min`;
}
function ago(date) {
  if (!date) return "";
  const m = Math.round((Date.now() - date) / 60000);
  if (settings.lang === "pl") return m < 60 ? `${m} min temu` : m < 1440 ? `${Math.round(m / 60)} h temu` : `${Math.round(m / 1440)} dni temu`;
  return m < 60 ? `${m} min ago` : m < 1440 ? `${Math.round(m / 60)} h ago` : `${Math.round(m / 1440)} days ago`;
}
/** Game markup (<i=1>…</i>) -> HTML, planet names clickable. */
function gameText(raw) {
  let html = esc(raw || "").replace(/&lt;i=(\d)&gt;(.*?)&lt;\/i&gt;/gs, (_, k, body) => (k === "3" ? `<b>${body}</b>` : `<span class="hl">${body}</span>`));
  const names = war.planets.map((p) => p.name).filter((n) => n.length >= 3).sort((a, b) => b.length - a.length);
  if (names.length) {
    const re = new RegExp(`(?<![\\p{L}\\d])(${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})(?![\\p{L}\\d])`, "giu");
    html = html.replace(re, (m) => `<span class="hl link" data-planet-name="${esc(m.toLowerCase())}">${m}</span>`);
  }
  return html;
}
function toast(msg) {
  const el = $("#toast");
  el.textContent = msg;
  el.classList.add("on");
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.remove("on"), 3200);
}
const art = (stem) => D.icon(A, stem) || "";
const loader = () => `<div class="loader"><div class="radar"></div>${t("loading").toUpperCase()}…</div>`;

// ---------- live meters (tick every second) ----------
function meter(key, measured, { color = "var(--human)", track = "rgba(255,255,255,.1)", label = t("liberation") } = {}) {
  return `<div class="meter" data-key="${key}" data-v="${measured}">
    <div class="bar" style="--fill:${color};--track:${track}"><i></i></div>
    <div class="row between" style="font-size:13px;margin-top:3px"><span class="muted"><span>${label}</span>: <b class="mv">${pct(measured)}</b>%</span><span class="rate"></span></div>
  </div>`;
}
function tick() {
  document.querySelectorAll(".meter").forEach((el) => {
    const key = el.dataset.key, measured = Number(el.dataset.v);
    const rate = D.ratePerHour(key);
    const v = D.liveValue(key, measured, rate);
    el.querySelector(".bar > i").style.width = `${v}%`;
    el.querySelector(".mv").textContent = pct(v, rate != null ? 4 : 2);
    const r = el.querySelector(".rate");
    if (rate == null) { r.textContent = t("pacePending"); r.className = "rate muted"; }
    else { r.textContent = `${rate > 0 ? "+" : ""}${pct(rate)}%/h`; r.className = "rate " + (rate > 0.01 ? "up" : rate < -0.01 ? "down" : "muted"); }
    const eta = el.parentElement.querySelector(".eta");
    if (eta) eta.textContent = rate > 0.01 && v < 100 ? `${t("eta")} ~${duration(((100 - v) / rate) * 3600e3)}` : "";
  });
  document.querySelectorAll("[data-until]").forEach((el) => (el.textContent = duration(Number(el.dataset.until) - Date.now())));
}
setInterval(tick, 1000);

function planetMeter(p) {
  if (p.event) {
    return `${meter(D.keys.event(p.index), D.liberation(p.event.health, p.event.maxHealth), { track: "color-mix(in srgb," + FCOLOR[p.event.faction] + " 45%, transparent)", label: t("defense") })}
      <div class="muted" style="font-size:13px">${t("ends")} <b data-until="${+p.event.end}"></b></div>`;
  }
  if (p.owner === "Humans") return `<div style="color:var(--human);font-weight:600">${t("liberated")}</div>`;
  return `${meter(D.keys.planet(p.index), D.liberation(p.health, p.maxHealth), { track: "color-mix(in srgb," + FCOLOR[p.owner] + " 45%, transparent)" })}<div class="eta muted" style="font-size:12px"></div>`;
}

// ---------- drawer ----------
function openDrawer(html, wide = false) {
  const d = $("#drawer");
  $("#drawer-body").innerHTML = html;
  d.classList.toggle("wide", wide);
  d.classList.add("open");
  d.setAttribute("aria-hidden", "false");
  d.scrollTop = 0;
  tick();
}
function closeDrawer() {
  $("#drawer").classList.remove("open");
  $("#drawer").setAttribute("aria-hidden", "true");
  currentMap?.reset();
}
$("#drawer-close").onclick = closeDrawer;
document.addEventListener("keydown", (e) => e.key === "Escape" && closeDrawer());

function planetDetails(p) {
  const effects = war.effects.get(p.index) || [];
  const artUrl = D.planetArt(A, p, effects);
  const conds = D.conditions(A, p.index);
  const biome = D.biome(A, p.index);
  const tags = [
    p.event && `<span class="tag" style="background:var(--red)">${settings.lang === "pl" ? "OBRONA przed" : "DEFENSE vs"}: ${factionLabel(p.event.faction)}</span>`,
    war.dss?.planet === p.index && `<span class="tag" style="background:var(--yellow)">DSS</span>`,
  ].filter(Boolean).join(" ");
  const s = p.stats;
  return `
    <div class="row" style="gap:18px;margin-bottom:10px">
      ${artUrl ? `<img class="planet-art big" src="${artUrl}" alt="">` : ""}
      <div><h2 style="margin:0;font-size:28px" class="yellow glow">${esc(p.name)}</h2>
        <div class="muted">Sektor ${esc(p.sector)} · ${factionLabel(p.owner)}</div><div style="margin-top:6px">${tags}</div></div>
    </div>
    ${planetMeter(p)}
    <div class="section-title">${t("stats").toUpperCase()}</div>
    <div class="stat"><span>${t("players")}</span><b>${num(p.players)}</b></div>
    <div class="stat"><span>${t("hp")}</span><b>${num(p.health)} / ${num(p.maxHealth)}</b></div>
    ${p.event ? `<div class="stat"><span>${t("hpDefense")}</span><b>${num(p.event.health)} / ${num(p.event.maxHealth)}</b></div>` : ""}
    ${s ? `<div class="stat"><span>${settings.lang === "pl" ? "Misje wygrane / przegrane" : "Missions won / lost"}</span><b>${num(s.missionsWon)} / ${num(s.missionsLost)}</b></div>
    <div class="stat"><span>${settings.lang === "pl" ? "Zabici wrogowie" : "Enemies killed"}</span><b>${num(s.bugKills + s.automatonKills + s.illuminateKills)}</b></div>
    <div class="stat"><span>${settings.lang === "pl" ? "Poległi Helldiverzy" : "Helldivers lost"}</span><b>${num(s.deaths)}</b></div>` : ""}
    ${effects.length ? `<div class="section-title">${t("modifiers").toUpperCase()}</div>` + effects.map((e, i) => `
      <div class="row appear" style="--i:${i};align-items:flex-start;margin-bottom:10px">
        ${D.effectIcon(A, e) ? `<img class="icon" style="width:32px;height:32px" src="${D.effectIcon(A, e)}">` : ""}
        <div><b>${esc(e.name)}</b>${e.name !== e.original ? ` <span class="muted" style="font-size:12px">${esc(e.original)}</span>` : ""}
        <div class="muted" style="font-size:14px">${gameText(e.description || "")}</div></div></div>`).join("") : ""}
    ${biome ? `<div class="section-title">${t("biome").toUpperCase()}</div><b>${esc(biome.name)}</b><div class="muted">${gameText(biome.description || "")}</div>` : ""}
    ${conds.length ? `<div class="section-title">${t("conditions").toUpperCase()}</div>` + conds.map((c) => `
      <div class="row" style="align-items:flex-start;margin-bottom:10px">${c.icon ? `<img class="icon" style="width:32px;height:32px" src="${c.icon}">` : ""}
      <div><b>${esc(c.name)}</b><div class="muted" style="font-size:14px">${gameText(c.description || "")}</div></div></div>`).join("") : ""}
    ${p.regions.length ? `<div class="section-title">${t("regions").toUpperCase()}</div>` + p.regions.map((r) => {
      const v = r.health == null ? null : D.liberation(r.health, r.maxHealth);
      return `<div style="margin-bottom:10px"><b>${esc(r.name || "Region " + r.id)}</b> <span class="muted" style="font-size:12px">${esc(r.size)}${r.available ? "" : settings.lang === "pl" ? " · zablokowany" : " · locked"}</span>
        ${v == null ? "" : v >= 99.95 || r.owner === "Humans" ? `<div style="color:var(--human);font-size:13px">${settings.lang === "pl" ? "ZDOBYTE" : "CAPTURED"}</div>` : meter(D.keys.region(p.index, r.id), v, { label: settings.lang === "pl" ? "Zdobycie" : "Capture" })}</div>`;
    }).join("") : ""}
    <p><a href="${W.pageUrl(p.english)}" target="_blank" rel="noopener">${settings.lang === "pl" ? "Artykuł na wiki" : "Wiki article"} ↗</a></p>`;
}
const openPlanet = (p) => p && openDrawer(planetDetails(p));
document.addEventListener("click", (e) => {
  const el = e.target.closest("[data-planet], [data-planet-name]");
  if (!el) return;
  const p = el.dataset.planet != null ? war.byIndex.get(Number(el.dataset.planet)) : war.planets.find((q) => q.name.toLowerCase() === el.dataset.planetName);
  if (p) { currentMap?.focus(p.index); openPlanet(p); }
});

// ---------- views ----------
let currentMap = null;
let unsub = null;
const views = {};

views.planets = (el) => {
  el.classList.add("full");
  el.innerHTML = `
    <div class="planets-layout">
      <div class="map-wrap" id="map">
        <div class="map-tools">
          <button class="chip se-toggle" id="se"><img class="icon" src="${art("Super_Earth_Icon")}" alt=""> ${t("showOurs")}</button>
          <button class="chip" id="reset">${t("reset")}</button>
        </div>
        <div class="map-tip panel pad" id="tip"></div>
      </div>
      <div class="side">
        <div class="row" style="margin-bottom:10px"><button class="chip on" data-list="fronts">${t("fronts")}</button><button class="chip" data-list="all">${t("all")}</button></div>
        <input class="search" id="q" placeholder="${t("search")}">
        <div class="row between" style="margin:10px 0"><span class="muted" id="total"></span></div>
        <div id="list"></div>
      </div>
    </div>`;
  const tip = $("#tip", el);
  currentMap = new GalaxyMap($("#map", el), A, {
    onSelect: openPlanet,
    onHover: (p, pos) => {
      if (!p) return tip.classList.remove("on");
      tip.innerHTML = `<b class="yellow">${esc(p.name)}</b><div class="muted" style="font-size:12px">${esc(p.sector)} · ${factionLabel(p.owner)} · ${num(p.players)} 🪖</div>
        ${p.event ? `<div style="color:var(--red)">${t("defense")}: ${pct(D.liberation(p.event.health, p.event.maxHealth))}%</div>` : p.owner !== "Humans" ? `<div>${t("liberation")}: ${pct(D.liberation(p.health, p.maxHealth))}%</div>` : ""}`;
      tip.style.left = pos[0] + "px"; tip.style.top = pos[1] + "px";
      tip.classList.add("on");
    },
  });
  const se = $("#se", el);
  se.onclick = () => {
    const show = !se.classList.contains("on");
    se.classList.toggle("on", show);
    se.lastChild.textContent = " " + (show ? t("hideOurs") : t("showOurs"));
    currentMap.setHideOurs(!show);
  };
  $("#reset", el).onclick = () => currentMap.reset();
  let mode = "fronts";
  el.querySelectorAll("[data-list]").forEach((b) => (b.onclick = () => {
    mode = b.dataset.list;
    el.querySelectorAll("[data-list]").forEach((x) => x.classList.toggle("on", x === b));
    renderList(true);
  }));
  $("#q", el).oninput = () => renderList(true);
  let lastKey = "";
  function renderList(force) {
    const q = $("#q", el).value.trim().toLowerCase();
    let items = mode === "fronts" ? war.campaigns.map((c) => c.planet) : war.planets;
    items = items.filter((p) => !q || p.name.toLowerCase().includes(q) || p.sector.toLowerCase().includes(q));
    items = [...items].sort((a, b) => (!!b.event - !!a.event) || b.players - a.players);
    const key = mode + q + items.map((p) => p.index).join(",");
    $("#total", el).textContent = `${t("players")}: ${num(war.planets.reduce((a, p) => a + p.players, 0))}`;
    if (!force && key === lastKey) {
      // Same list: only refresh the measured values the meters extrapolate from.
      items.forEach((p) => el.querySelectorAll(`.meter[data-key$=":${p.index}"]`).forEach((m) => {
        m.dataset.v = m.dataset.key.startsWith("event") ? D.liberation(p.event?.health ?? 0, p.event?.maxHealth ?? 1) : D.liberation(p.health, p.maxHealth);
      }));
      return;
    }
    lastKey = key;
    $("#list", el).innerHTML = items.slice(0, 120).map((p, i) => {
      const a = D.planetArt(A, p, war.effects.get(p.index) || []);
      const effects = (war.effects.get(p.index) || []).filter((e) => e.kind !== "support" && e.kind !== "other");
      return `<div class="panel click front appear" style="--i:${Math.min(i, 12)};--accent:${FCOLOR[p.event?.faction || p.owner] || "var(--yellow)"}" data-planet="${p.index}">
        <div class="row">${a ? `<img class="planet-art" src="${a}" alt="">` : ""}
          <div style="flex:1;min-width:0"><div class="row between"><span class="name">${esc(p.name)}</span><span class="muted" style="font-size:12px">${num(p.players)} 🪖</span></div>
          <div class="muted" style="font-size:12px">${esc(p.sector)} · ${factionLabel(p.event ? p.event.faction : p.owner)}</div>
          <div style="margin-top:4px">${effects.map((e) => `<span class="tag" style="background:#FF7A45;margin:0 3px 3px 0">${esc(e.name.split(":").pop().trim())}</span>`).join("")}</div></div></div>
        <div style="margin-top:6px">${planetMeter(p)}</div></div>`;
    }).join("");
    tick();
  }
  const update = () => { currentMap.setData(war); renderList(false); };
  if (war.planets.length) update(); else $("#list", el).innerHTML = loader();
  unsub = D.subscribe(update);
};

// ---- orders & war campaigns ----
function taskViews(a) {
  const get = (task, vt) => { const i = task.valueTypes?.indexOf(vt); return i >= 0 ? Number(task.values[i]) : null; };
  const pl = settings.lang === "pl";
  return (a.tasks || []).map((task, i) => {
    const raw = Number(a.progress?.[i] ?? 0);
    const goal = get(task, 3) || null;
    const idx = [11, 12, 13].includes(task.type) ? get(task, 12) : null;
    const planet = idx != null && !(idx === 0 && !get(task, 11)) ? war.byIndex.get(idx) : null;
    const race = { 1: "Humans", 2: "Terminids", 3: "Automaton", 4: "Illuminate" }[get(task, 1)];
    const against = race ? ` (${factionLabel(race)})` : "";
    const diff = get(task, 9) ? (pl ? `, poziom trudności ${get(task, 9)}+` : `, difficulty ${get(task, 9)}+`) : "";
    const L = {
      11: planet ? (pl ? `Wyzwól planetę ${planet.name}` : `Liberate ${planet.name}`) : (pl ? "Wyzwól planety" : "Liberate planets") + against,
      12: planet ? (pl ? `Obroń planetę ${planet.name}` : `Defend ${planet.name}`) : (pl ? "Obroń planety" : "Defend planets") + against,
      13: planet ? (pl ? `Utrzymaj kontrolę nad ${planet.name}` : `Hold ${planet.name}`) : pl ? "Utrzymaj kontrolę nad planetami" : "Hold planets",
      3: (pl ? "Zlikwiduj wrogów" : "Eradicate enemies") + against + diff,
      2: (pl ? "Ewakuuj się z zasobami" : "Extract with samples") + against + diff,
      7: (pl ? "Ukończ misje" : "Complete missions") + against + diff,
      9: (pl ? "Ukończ operacje" : "Complete operations") + against + diff,
      15: (pl ? "Poszerz terytorium Super Ziemi" : "Expand Super Earth's territory") + against,
    };
    const progress = planet && task.type !== 12 && planet.owner === "Humans" && !planet.event ? Math.max(raw, 1) : raw;
    const g = planet && task.type !== 12 ? null : goal;
    return { label: L[task.type] || `#${i + 1}${against}`, planet, progress, goal: g, done: g ? progress >= g : progress >= 1, key: D.keys.task(a.id, i) };
  });
}
function orderCard(a, i) {
  const tasks = taskViews(a);
  const exp = Date.parse(a.expiration);
  const reward = a.reward || a.rewards?.[0];
  return `<div class="panel glow-box pad appear" style="--i:${i}">
    <div class="row between wrap"><h3 class="yellow" style="margin:0">${esc(a.title || (settings.lang === "pl" ? "ROZKAZ GŁÓWNY" : "MAJOR ORDER"))}</h3>
    <span class="muted">${t("expires")} <b data-until="${exp}"></b></span></div>
    ${a.briefing ? `<p>${gameText(a.briefing)}</p>` : ""}
    ${a.description && a.description !== a.briefing ? `<p class="muted">${gameText(a.description)}</p>` : ""}
    ${tasks.map((tk) => `<div style="margin:10px 0">
      <div class="row between"><span ${tk.planet ? `class="hl link" data-planet="${tk.planet.index}"` : ""}>${tk.done ? "✓ " : ""}${esc(tk.label)}</span>
      ${tk.goal ? `<span class="muted">${num(tk.progress)} / ${num(tk.goal)}</span>` : tk.done ? `<span style="color:var(--green)">✓</span>` : ""}</div>
      ${tk.goal ? `<div class="bar" style="--fill:var(--yellow)"><i style="width:${Math.min(100, (tk.progress / tk.goal) * 100)}%"></i></div>` : tk.planet && !tk.done ? planetMeter(tk.planet) : ""}
    </div>`).join("")}
    ${reward ? `<div class="row" style="margin-top:8px"><img class="icon" src="${art(reward.type === 1 ? "Medal" : "Super_Credit")}"> <b>${t("reward")}: ${num(reward.amount)}</b></div>` : ""}
  </div>`;
}
function dispatchCard(d, i) {
  const m = d.message.match(/^\s*<i=[13]>(.*?)<\/i>\s*\n+([\s\S]*)$/);
  return `<div class="panel pad dispatch appear" style="--i:${i}">
    ${m ? `<h3>${esc(m[1])}</h3>` : ""}<p>${gameText(m ? m[2] : d.message)}</p>
    <div class="muted" style="font-size:12px;margin-top:8px">${war.statusTime ? ago(new Date(war.updatedAt + (d.published - war.statusTime) * 1000)) : ""}</div></div>`;
}
const isOrderDispatch = (d) => /major order|rozkaz|orders|objective/i.test(d.message);

views.campaigns = (el) => {
  let sub = localStorage.getItem("camp-tab") || "orders";
  el.innerHTML = `<div class="tabs-inline"><button class="chip" data-sub="orders">${t("orders")}</button><button class="chip" data-sub="wiki">${t("warCampaigns")}</button></div><div id="sub"></div>`;
  const tabs = el.querySelectorAll("[data-sub]");
  const draw = () => {
    tabs.forEach((b) => b.classList.toggle("on", b.dataset.sub === sub));
    const box = $("#sub", el);
    box.className = "view-enter";
    if (sub === "orders") {
      const orders = war.assignments || [];
      const news = war.dispatches.filter(isOrderDispatch).slice(0, 6);
      box.innerHTML = `<div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(420px,1fr))">${orders.length ? orders.map(orderCard).join("") : `<p class="muted">${t("noOrders")}</p>`}</div>
        ${news.length ? `<div class="section-title">${settings.lang === "pl" ? "KOMUNIKATY DO ROZKAZÓW" : "ORDER DISPATCHES"}</div><div class="grid">${news.map((d, i) => dispatchCard(d, i + 2)).join("")}</div>` : ""}`;
      tick();
    } else {
      box.innerHTML = loader();
      W.getCampaigns().then((list) => renderCampaigns(box, list)).catch((e) => (box.innerHTML = `<p class="muted">${esc(e.message)}</p>`));
    }
  };
  tabs.forEach((b) => (b.onclick = () => { sub = b.dataset.sub; localStorage.setItem("camp-tab", sub); draw(); }));
  draw();
  unsub = D.subscribe(() => sub === "orders" && draw());
};
function campaignArt(key, phase) {
  const base = A.norm(key);
  const cands = phase ? [base + String.fromCharCode(96 + phase), base + "phase" + phase, base] : [base];
  for (const c of cands) if (A.campaignArt[c]) return `assets/campaigns/${A.campaignArt[c]}`;
  return null;
}
function wikiImage(name) {
  if (!name) return null;
  const stem = name.replace(/\.[^.]+$/, "").replace(/^Galactic[ _]War[ _]Campaigns[ _]Header[ _]/i, "");
  const own = A.campaignArt[A.norm(stem)];
  return own ? `assets/campaigns/${own}` : W.fileUrl(name);
}
const OUT = { success: ["var(--green)", "SUKCES", "SUCCESS"], failure: ["var(--red)", "PORAŻKA", "FAILURE"], progress: ["var(--yellow)", "W TOKU", "IN PROGRESS"], unknown: ["var(--muted)", "?", "?"] };
function renderCampaigns(box, list) {
  const pl = settings.lang === "pl";
  const detail = (c) => `
    <div class="campaign-hero panel" style="--accent:${FCOLOR[c.faction] || "var(--yellow)"}">
      ${campaignArt(c.name) || wikiImage(c.banner) ? `<img src="${campaignArt(c.name) || wikiImage(c.banner)}" alt="">` : ""}
      <div class="over"><div class="muted" style="letter-spacing:2px">${factionLabel(c.faction).toUpperCase()}</div>
      <h2 class="glow" style="margin:4px 0;font-size:34px;color:${FCOLOR[c.faction] || "var(--yellow)"}">${esc(c.name.toUpperCase())}</h2>
      <p style="max-width:900px">${gameText(c.description)}</p></div></div>
    <div class="timeline" style="margin:14px 0">${c.phases.map((ph) => `<div class="panel" style="--accent:${OUT[ph.outcome][0]}"><div class="muted" style="font-size:12px">${pl ? "FAZA" : "PHASE"} ${ph.number}</div><b style="color:${OUT[ph.outcome][0]}">${OUT[ph.outcome][pl ? 1 : 2]}</b></div>`).join("")}</div>
    ${[...c.phases].reverse().map((ph, i) => `<div class="panel pad phase appear" style="--i:${i};margin-bottom:14px;--accent:${OUT[ph.outcome][0]}">
      <div class="yellow" style="font-size:12px;letter-spacing:2px">${esc(c.name.toUpperCase())} · ${pl ? "FAZA" : "PHASE"} ${ph.number}</div>
      <h3 style="margin:4px 0;font-size:24px">${esc(ph.name.toUpperCase())}</h3>
      <div class="muted">${esc(ph.dateStart)}${ph.dateEnd ? " – " + esc(ph.dateEnd) : ""} · <b style="color:${OUT[ph.outcome][0]}">${OUT[ph.outcome][pl ? 1 : 2]}</b></div>
      ${wikiImage(ph.image) || campaignArt(c.name, ph.number) ? `<img class="phase-art" src="${wikiImage(ph.image) || campaignArt(c.name, ph.number)}" alt="" loading="lazy">` : ""}
      ${ph.outcome === "progress" && war.assignments?.length ? war.assignments.map(orderCard).join("") : ""}
      ${ph.briefing ? `<p>${gameText(ph.briefing)}</p>` : ""}
      ${ph.debrief ? `<div class="section-title">${pl ? "PODSUMOWANIE" : "DEBRIEF"}</div><p class="muted">${gameText(ph.debrief)}</p>` : ""}
    </div>`).join("")}`;
  const active = list.find((c) => c.active);
  const draw = (opened) => {
    const c = opened || active;
    box.innerHTML = `${opened ? `<button class="btn" id="back">← ${pl ? "Wszystkie kampanie" : "All campaigns"}</button><div style="height:12px"></div>` : ""}
      ${c ? detail(c) : `<p class="muted">${pl ? "Wiki nie odnotowała jeszcze trwającej kampanii." : "No ongoing campaign on the wiki yet."}</p>`}
      ${opened ? "" : `<div class="section-title">${pl ? "ARCHIWUM KAMPANII" : "CAMPAIGN ARCHIVE"}</div>
      <div class="grid small">${list.filter((x) => x !== active).map((x, i) => `<div class="panel click pad appear" style="--i:${Math.min(i, 12)};--accent:${FCOLOR[x.faction] || "var(--yellow)"}" data-camp="${esc(x.name)}">
        ${campaignArt(x.name) ? `<img src="${campaignArt(x.name)}" alt="" style="width:100%;height:90px;object-fit:cover;border-radius:3px;opacity:.8" loading="lazy">` : ""}
        <b style="color:${FCOLOR[x.faction] || "var(--yellow)"}">${esc(x.name.toUpperCase())}</b>
        <div class="muted" style="font-size:12px">${x.phases.map((p) => OUT[p.outcome][pl ? 1 : 2]).join(" · ")}</div></div>`).join("")}</div>`}
      <p style="margin-top:20px"><a href="${W.pageUrl("Campaigns")}" target="_blank" rel="noopener">${t("source")} ↗</a></p>`;
    box.querySelectorAll("[data-camp]").forEach((b) => (b.onclick = () => { draw(list.find((x) => x.name === b.dataset.camp)); $("#view").scrollTop = 0; }));
    const back = $("#back", box);
    if (back) back.onclick = () => draw(null);
    tick();
  };
  draw(null);
}

views.news = (el) => {
  let shown = 12;
  const draw = () => {
    if (!war.dispatches.length) { el.innerHTML = loader(); return; }
    el.innerHTML = `<div class="grid">${war.dispatches.slice(0, shown).map(dispatchCard).join("")}</div>
      ${war.dispatches.length > shown ? `<p style="text-align:center"><button class="btn" id="more">${t("older")} (${war.dispatches.length - shown})</button></p>` : ""}`;
    const more = $("#more", el);
    if (more) more.onclick = () => { shown += 12; draw(); };
  };
  draw();
  unsub = D.subscribe(draw);
};

views.dss = (el) => {
  const draw = () => {
    const pl = settings.lang === "pl";
    const st = war.stations || [];
    const loc = war.dss && war.byIndex.get(war.dss.planet);
    el.innerHTML = `
      ${loc ? `<div class="panel glow-box pad appear" style="margin-bottom:16px"><div class="row" style="gap:16px">
        <img src="${art("DSS_Icon")}" style="width:64px;height:64px;filter:drop-shadow(0 0 8px var(--yellow))">
        <div><div class="muted">${pl ? "STACJA DEMOKRACJI NA ORBICIE" : "DEMOCRACY SPACE STATION IN ORBIT OF"}</div>
        <h2 class="yellow glow hl link" style="margin:0" data-planet="${loc.index}">${esc(loc.name)}</h2>
        <div class="muted">${pl ? "Następny skok (głosowanie) za" : "Next jump vote ends in"} <b data-until="${+war.dss.electionEnd}"></b></div></div></div></div>` : `<p class="muted">${t("noDss")}</p>`}
      <div class="grid">${st.flatMap((s) => s.tacticalActions || []).map((a, i) => `
        <div class="panel pad appear" style="--i:${i}"><div class="row"><img class="icon" style="width:36px;height:36px" src="${art("DSS_" + a.name.replace(/ /g, "_") + "_Icon") || art("DSS_Icon")}">
        <h3 style="margin:0" class="yellow">${esc(a.name)}</h3></div>
        <p class="muted">${gameText(a.description)}</p>
        ${(a.costs || []).map((c) => `<div class="bar" style="--fill:var(--yellow)"><i style="width:${Math.min(100, (c.currentValue / c.targetValue) * 100)}%"></i></div>
        <div class="muted" style="font-size:12px">${num(Math.round(c.currentValue))} / ${num(c.targetValue)}</div>`).join("")}
        </div>`).join("")}</div>`;
    tick();
  };
  draw();
  unsub = D.subscribe(draw);
};

const CATEGORIES = [
  ["Stratagems", "Stratagemy", "Eagle_500kg_Bomb_Stratagem_Icon"], ["Primary Weapons", "Bronie główne", "Muzzle_Icon"],
  ["Secondary Weapons", "Bronie boczne", "Magazine_Icon"], ["Throwables", "Granaty", "Underbarrel_Icon"], ["Armor", "Pancerze", "Helldiver_Icon"],
  ["Boosters", "Wzmacniacze", "Armed_Resupply_Pods_Booster_Icon"], ["Terminids", "Terminidzi", "Terminid_Icon"], ["Automatons", "Automatony", "Automaton_Icon"],
  ["Illuminate", "Iluminaci", "Illuminate_Icon"], ["Warbonds", "Obligacje wojenne", "Medal"], ["Missions", "Misje", "Operation_Icon"], ["Planets", "Planety", "Locations_Icon"],
  ["Environmental Conditions", "Warunki środowiskowe", "Blizzards_Environmental_Condition_Icon"], ["Democracy Space Station", "Stacja DSS", "DSS_Icon"],
  ["Ship Modules", "Moduły niszczyciela", "Bridge_Ship_Module_Icon"], ["Campaigns", "Kampanie wojenne", "Liberation_Campaign_Icon"],
  ["Major Orders", "Rozkazy główne", "Defense_Campaign_Icon"], ["Ministry of Truth", "Ministerstwa", "Ministry_of_Truth_Icon"],
];
views.archive = (el) => {
  const pl = settings.lang === "pl";
  el.innerHTML = `<input class="search" id="wq" placeholder="${pl ? "Szukaj w wiki Helldivers…" : "Search the Helldivers wiki…"}">
    <p class="muted">${t("archiveHint")}</p><div id="wres" class="grid small"></div>`;
  const res = $("#wres", el);
  const cats = () => (res.innerHTML = CATEGORIES.map(([en, plName, ic], i) => `<div class="panel click pad appear row" style="--i:${Math.min(i, 12)}" data-article="${esc(en)}"><img class="icon" style="width:34px;height:34px" src="${art(ic)}"><b>${esc(pl ? plName : en)}</b></div>`).join(""));
  cats();
  let timer;
  $("#wq", el).oninput = (e) => {
    clearTimeout(timer);
    const q = e.target.value;
    if (!q.trim()) return cats();
    timer = setTimeout(async () => {
      res.innerHTML = loader();
      try {
        const r = await W.search(q);
        res.innerHTML = r.map((x, i) => `<div class="panel click pad appear" style="--i:${Math.min(i, 12)}" data-article="${esc(x.title)}">
          <div class="row">${x.thumbnail ? `<img src="${x.thumbnail}" style="width:48px;height:48px;object-fit:contain;${/\.svg/i.test(x.thumbnail) ? "filter:brightness(0) invert(1)" : ""}">` : ""}<b>${esc(x.title)}</b></div>
          <div class="muted" style="font-size:13px">${x.snippet.replace(/<[^>]*>/g, "")}</div></div>`).join("") || `<p class="muted">—</p>`;
      } catch (err) { res.innerHTML = `<p class="muted">${esc(err.message)}</p>`; }
    }, 350);
  };
};
document.addEventListener("click", (e) => {
  const el = e.target.closest("[data-article]");
  if (el) openArticle(el.dataset.article);
});
async function openArticle(title) {
  openDrawer(loader(), true);
  try {
    const a = await W.getArticle(title);
    openDrawer(`<h2 class="yellow glow" style="font-size:28px;margin-top:0">${esc(a.title)}</h2>
      <iframe class="article-frame" sandbox="allow-same-origin allow-popups" id="article"></iframe>
      <p class="muted" style="font-size:13px">${settings.lang === "pl" ? "Źródło" : "Source"}: <a href="${W.pageUrl(a.title)}" target="_blank" rel="noopener">Helldivers Wiki — ${esc(a.title)}</a> · CC BY-SA 4.0</p>`, true);
    const frame = $("#article");
    frame.srcdoc = `<!doctype html><html><head><meta charset="utf-8"><base href="https://helldivers.wiki.gg/"><style>${W.ARTICLE_CSS}</style></head><body>${a.html}</body></html>`;
    frame.onload = () => {
      const doc = frame.contentDocument;
      frame.style.height = doc.documentElement.scrollHeight + 40 + "px";
      doc.addEventListener("click", (ev) => {
        const im = ev.target.closest("img");
        const link = ev.target.closest("a");
        if (im && !(link && /\/wiki\/(?!File:)/.test(link.getAttribute("href") || ""))) {
          ev.preventDefault();
          const v = document.createElement("div");
          v.className = "viewer";
          const src = im.currentSrc || im.src;
          v.innerHTML = `<img src="${src.replace(/\/thumb\/(.*)\/[^/]+$/, "/$1")}" alt="">`;
          v.onclick = () => v.remove();
          document.body.appendChild(v);
          return;
        }
        if (!link) return;
        const href = link.getAttribute("href") || "";
        const m = href.match(/^(?:https:\/\/helldivers\.wiki\.gg)?\/wiki\/([^#?]+)/);
        ev.preventDefault();
        if (m && !/^(File|Special|Template|User|Talk):/i.test(decodeURIComponent(m[1]))) openArticle(decodeURIComponent(m[1]).replace(/_/g, " "));
        else if (href.startsWith("#")) doc.getElementById(href.slice(1))?.scrollIntoView();
        else window.open(link.href, "_blank", "noopener");
      });
    };
  } catch (e) {
    openDrawer(`<p class="muted">${esc(e.message)}</p>`, true);
  }
}

// ---------- shell ----------
function route() {
  const name = (location.hash.replace("#/", "") || "planets").split("/")[0];
  const view = views[name] ? name : "planets";
  unsub?.(); unsub = null;
  currentMap?.destroy(); currentMap = null;
  document.querySelectorAll(".tab").forEach((a) => a.classList.toggle("active", a.dataset.tab === view));
  $("#title").textContent = t(view).toUpperCase();
  const el = $("#view");
  el.className = "view view-enter";
  el.innerHTML = "";
  el.scrollTop = 0;
  views[view](el);
}
function shell() {
  $("#tabs").innerHTML = TABS.map(([k, ic]) => `<a class="tab" data-tab="${k}" href="#/${k}"><img src="${art(ic)}" alt=""><span>${t(k)}</span></a>`).join("");
  $("#lang").textContent = settings.lang.toUpperCase();
  $("#lang").onclick = () => { settings.lang = settings.lang === "pl" ? "en" : "pl"; location.reload(); };
  const anim = $("#anim");
  const syncAnim = () => { document.body.classList.toggle("no-anim", !settings.anim); anim.classList.toggle("on", settings.anim); };
  anim.onclick = () => { settings.anim = !settings.anim; syncAnim(); toast(settings.anim ? "Animacje: wł." : "Animacje: wył."); };
  syncAnim();
  document.documentElement.lang = settings.lang;
}
function liveBadge() {
  const box = $("#live");
  const ok = war.updatedAt && !war.error;
  box.className = "live " + (ok ? "ok" : war.error ? "err" : "");
  $("#live-text").textContent = ok ? `${t("live")} · ${new Date(war.updatedAt).toLocaleTimeString(settings.lang === "pl" ? "pl-PL" : "en-US")}` : war.error ? `${t("offline")}: ${war.error}` : `${t("loading")}…`;
}

(async function main() {
  A = await D.loadAssets();
  shell();
  D.subscribe(liveBadge);
  window.addEventListener("hashchange", route);
  route();
  D.start();
})();
