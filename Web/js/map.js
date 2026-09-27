// Galaxy map on a <canvas>: same layers and rules as the Android GalaxyMap.
import { settings, icon, planetArt, effectIcon } from "./data.js";

const COLORS = { Humans: "#3FA9FF", Terminids: "#FFB300", Automaton: "#FF3B30", Illuminate: "#B45CFF" };
const SUPPLY = { Humans: [79, 180, 255], Terminids: [255, 179, 0], Automaton: [255, 59, 48], Illuminate: [180, 92, 255] };
const DROP = { Terminids: "#FFA726", Automaton: "#FF3B30", Illuminate: "#B45CFF", Humans: "#3FA9FF", site: "#8C939C", none: "#B0B6BE" };
const GLOOM = "216,169,69";
const BLACK_HOLES = new Set(["Meridia"]);
const FRACTURED = new Set(["Angel's Venture", "Moradesh", "Ivis"]);
const KEYS = {
  Terminids: ["TERMINID", "PREDATOR", "SPORE", "RUPTURE", "DRAGONROACH", "HIVE", "RAMPAGE"],
  Automaton: ["AUTOMATON", "JET BRIGADE", "INCINERATION", "CYBORG", "SURGE", "FACTOR", "STRIDER"],
  Illuminate: ["ILLUMINATE", "MINDLESS", "APPROPRIATOR", "VOTE SNATCHER", "INVASION FLEET", "GREAT HOST", "OVERSHIP"],
};

const images = new Map();
function img(url, onload) {
  if (!url) return null;
  let im = images.get(url);
  if (!im) {
    im = new Image();
    im.onload = onload;
    im.src = url;
    images.set(url, im);
  }
  return im.complete && im.naturalWidth ? im : null;
}
const rgba = ([r, g, b], a = 1) => `rgba(${r},${g},${b},${a})`;
const hex2rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));

function sectorOwner(members) {
  const enemies = members.filter((p) => p.owner && p.owner !== "Humans");
  if (!enemies.length) return "Humans";
  const count = {};
  enemies.forEach((p) => (count[p.owner] = (count[p.owner] || 0) + 1));
  return Object.entries(count).sort((a, b) => b[1] - a[1])[0][0];
}
function pointInPolygon(x, y, pts) {
  let inside = false;
  for (let i = 0, j = pts.length / 2 - 1; i < pts.length / 2; j = i++) {
    const xi = pts[2 * i], yi = pts[2 * i + 1], xj = pts[2 * j], yj = pts[2 * j + 1];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}
/** Planet colours diffused over the supply graph: lines blend across the front. */
function supplyNodeColors(planets, byIndex) {
  const nb = new Map();
  const link = (a, b) => { (nb.get(a) || nb.set(a, new Set()).get(a)).add(b); };
  planets.forEach((p) => p.waypoints.forEach((w) => { if (byIndex.has(w)) { link(p.index, w); link(w, p.index); } }));
  const enemy = (p) => p.owner && p.owner !== "Humans";
  const enemyOf = new Map(planets.map((p) => {
    if (enemy(p)) return [p.index, p.owner];
    const near = [...(nb.get(p.index) || [])].flatMap((n) => [n, ...(nb.get(n) || [])]).map((i) => byIndex.get(i)).filter((q) => q && enemy(q));
    const c = {};
    near.forEach((q) => (c[q.owner] = (c[q.owner] || 0) + 1));
    return [p.index, Object.entries(c).sort((a, b) => b[1] - a[1])[0]?.[0] || p.event?.faction || null];
  }));
  let heat = new Map(planets.map((p) => [p.index, enemy(p) ? 1 : p.event ? 0.35 : 0]));
  for (let k = 0; k < 2; k++) {
    heat = new Map(planets.map((p) => {
      const own = heat.get(p.index);
      const ns = [...(nb.get(p.index) || [])].map((i) => heat.get(i)).filter((v) => v != null);
      const b = ns.length ? own * 0.5 + (ns.reduce((a, v) => a + v, 0) / ns.length) * 0.5 : own;
      return [p.index, enemy(p) ? Math.min(1, Math.max(0.6, b)) : Math.min(0.5, Math.max(0, b))];
    }));
  }
  return new Map(planets.map((p) => {
    const f = enemyOf.get(p.index);
    const h = SUPPLY.Humans;
    if (!f) return [p.index, h];
    const e = SUPPLY[f] || h, t = heat.get(p.index);
    return [p.index, h.map((v, i) => Math.round(v + (e[i] - v) * t))];
  }));
}
function dropletColor(effect, planet) {
  const n = effect.original.toUpperCase();
  if (n.includes("CONTROL SYSTEM")) return DROP.site;
  if (effect.kind === "support" || n.includes("SEAF")) return DROP.Humans;
  for (const [f, ks] of Object.entries(KEYS)) if (ks.some((k) => n.includes(k))) return DROP[f];
  if (planet?.owner && planet.owner !== "Humans") return DROP[planet.owner];
  return DROP[planet?.event?.faction] || DROP.none;
}

export class GalaxyMap {
  constructor(container, A, { onSelect, onHover }) {
    this.A = A;
    this.onSelect = onSelect;
    this.onHover = onHover;
    this.canvas = document.createElement("canvas");
    container.appendChild(this.canvas);
    this.ctx = this.canvas.getContext("2d");
    this.scale = 1; this.ox = 0; this.oy = 0;
    this.hideOurs = true;
    this.wave = 1; this.intro = settings.anim ? 0 : 1;
    this.selected = null;
    this.data = null;
    this.rocks = new Map();
    this.cam = null;
    this.resize();
    this.ro = new ResizeObserver(() => this.resize());
    this.ro.observe(container);
    this.bind();
    this.loop = this.loop.bind(this);
    this.raf = requestAnimationFrame(this.loop);
  }
  destroy() { cancelAnimationFrame(this.raf); this.ro.disconnect(); }

  resize() {
    const r = this.canvas.parentElement.getBoundingClientRect();
    this.w = r.width; this.h = r.height;
    this.dpr = window.devicePixelRatio || 1;
    this.canvas.width = r.width * this.dpr;
    this.canvas.height = r.height * this.dpr;
  }
  unit() { return (Math.min(this.w, this.h) / 2) * 0.92; }
  world(x, y) { return [this.w / 2 + x * this.unit(), this.h / 2 - y * this.unit()]; }
  screen(x, y) { const [wx, wy] = this.world(x, y); return [wx * this.scale + this.ox, wy * this.scale + this.oy]; }

  setData(war) {
    this.data = war;
    const planets = war.planets.filter((p) => p.index === 0 || Math.abs(p.position.x) > 0.004 || Math.abs(p.position.y) > 0.004);
    this.planets = planets;
    this.byIndex = new Map(planets.map((p) => [p.index, p]));
    this.fronts = new Set(war.campaigns.map((c) => c.planet.index));
    this.nodeColors = supplyNodeColors(planets, this.byIndex);
    const owners = {};
    const bySector = {};
    planets.forEach((p) => (bySector[p.sector] ??= []).push(p));
    Object.entries(bySector).forEach(([s, m]) => (owners[s] = sectorOwner(m)));
    this.cells = this.A.sectors.map((pts) => {
      const count = {};
      planets.forEach((p) => { if (pointInPolygon(p.position.x, p.position.y, pts)) count[p.sector] = (count[p.sector] || 0) + 1; });
      const sector = Object.entries(count).sort((a, b) => b[1] - a[1])[0]?.[0];
      const owner = sector && owners[sector];
      return { pts, color: owner && owner !== "Humans" ? COLORS[owner] : null };
    });
    this.centroids = Object.entries(bySector).map(([name, m]) => [name, m.reduce((a, p) => a + p.position.x, 0) / m.length, m.reduce((a, p) => a + p.position.y, 0) / m.length]);
    this.gloom = new Set([...war.effects].filter(([, l]) => l.some((e) => /GLOOM/i.test(e.original))).map(([i]) => i));
    this.blackHoles = new Set(planets.filter((p) => BLACK_HOLES.has(p.english)).map((p) => p.index));
    this.fractured = new Set(planets.filter((p) => FRACTURED.has(p.english) || (war.effects.get(p.index) || []).some((e) => /FRACTURED/i.test(e.original))).map((p) => p.index));
    this.drops = new Map([...war.effects].map(([i, list]) => [i, list
      .filter((e) => (["enemy", "hazard", "site"].includes(e.kind) || /SEAF/i.test(e.original)) && !/GLOOM|FRACTURED|BLACK HOLE/i.test(e.original))
      .map((e) => ({ url: effectIcon(this.A, e), color: dropletColor(e, this.byIndex.get(i)) }))
      .filter((d) => d.url).slice(0, 3)]).filter(([, l]) => l.length));
    this.moTargets = new Set((war.assignments || []).flatMap((a) => (a.tasks || []).map((t) => {
      const i = t.valueTypes?.indexOf(12);
      return [11, 12, 13].includes(t.type) && i >= 0 ? Number(t.values[i]) : null;
    })).filter((v) => v != null && v > 0));
  }

  quietOurs(p) { return p.index !== 0 && p.owner === "Humans" && !p.event && !this.fronts.has(p.index); }
  visibility(p) {
    if (p.index === this.selected || !this.quietOurs(p)) return 1;
    const d = Math.hypot(p.position.x, p.position.y);
    const reached = Math.min(1, Math.max(0, (this.wave * 1.25 - d) / 0.12));
    return this.hideOurs ? 1 - reached : reached;
  }
  setHideOurs(v) { this.hideOurs = v; this.wave = settings.anim ? 0 : 1; }

  clamp() {
    const s = this.scale;
    this.ox = Math.min(0, Math.max(this.w * (1 - s), this.ox));
    this.oy = Math.min(0, Math.max(this.h * (1 - s), this.oy));
  }
  zoomAt(factor, cx, cy) {
    const s = Math.min(10, Math.max(1, this.scale * factor));
    this.ox = cx - ((cx - this.ox) * s) / this.scale;
    this.oy = cy - ((cy - this.oy) * s) / this.scale;
    this.scale = s;
    this.clamp();
  }
  flyTo(scale, ox, oy) {
    if (!settings.anim) { this.scale = scale; this.ox = ox; this.oy = oy; this.clamp(); return; }
    this.cam = { t0: performance.now(), s0: this.scale, x0: this.ox, y0: this.oy, s1: scale, x1: ox, y1: oy };
  }
  focus(index) {
    const p = this.byIndex?.get(index);
    if (!p) return;
    this.selected = index;
    const s = Math.max(this.scale, 2.6);
    const [wx, wy] = this.world(p.position.x, p.position.y);
    this.flyTo(s, this.w * 0.5 - wx * s, this.h * 0.45 - wy * s);
  }
  reset() { this.selected = null; this.flyTo(1, 0, 0); }

  pick(mx, my) {
    let best = null, bd = 18 * 18;
    for (const p of this.planets || []) {
      if (this.visibility(p) < 0.5) continue;
      const [x, y] = this.screen(p.position.x, p.position.y);
      const d = (x - mx) ** 2 + (y - my) ** 2;
      if (d < bd) { bd = d; best = p; }
    }
    return best;
  }

  bind() {
    const c = this.canvas;
    let drag = null, moved = false;
    const pos = (e) => { const r = c.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
    c.addEventListener("wheel", (e) => {
      e.preventDefault();
      this.cam = null;
      const [x, y] = pos(e);
      this.zoomAt(Math.exp(-e.deltaY * 0.0015), x, y);
    }, { passive: false });
    const pointers = new Map();
    c.addEventListener("pointerdown", (e) => {
      c.setPointerCapture(e.pointerId);
      pointers.set(e.pointerId, pos(e));
      drag = pos(e); moved = false; this.cam = null;
    });
    c.addEventListener("pointermove", (e) => {
      const p = pos(e);
      if (pointers.has(e.pointerId)) {
        if (pointers.size === 2) {
          const [a, b] = [...pointers.values()];
          const before = Math.hypot(a[0] - b[0], a[1] - b[1]);
          pointers.set(e.pointerId, p);
          const [a2, b2] = [...pointers.values()];
          const after = Math.hypot(a2[0] - b2[0], a2[1] - b2[1]);
          if (before > 0) this.zoomAt(after / before, (a2[0] + b2[0]) / 2, (a2[1] + b2[1]) / 2);
          moved = true;
          return;
        }
        pointers.set(e.pointerId, p);
        const dx = p[0] - drag[0], dy = p[1] - drag[1];
        if (Math.abs(dx) + Math.abs(dy) > 3) moved = true;
        this.ox += dx; this.oy += dy; this.clamp();
        drag = p;
      } else {
        const hit = this.pick(p[0], p[1]);
        this.hover = hit?.index ?? null;
        c.style.cursor = hit ? "pointer" : "grab";
        this.onHover?.(hit, p);
      }
    });
    const up = (e) => {
      pointers.delete(e.pointerId);
      if (!moved && pointers.size === 0) {
        const [x, y] = pos(e);
        const hit = this.pick(x, y);
        if (hit) { this.focus(hit.index); this.onSelect?.(hit); }
      }
    };
    c.addEventListener("pointerup", up);
    c.addEventListener("pointercancel", (e) => pointers.delete(e.pointerId));
    c.addEventListener("pointerleave", () => this.onHover?.(null));
    c.addEventListener("dblclick", (e) => {
      const [x, y] = pos(e);
      const target = this.scale > 1.5 ? 1 : 3;
      this.flyTo(target, x - ((x - this.ox) * target) / this.scale, y - ((y - this.oy) * target) / this.scale);
    });
  }

  loop(now) {
    this.raf = requestAnimationFrame(this.loop);
    if (this.cam) {
      const k = Math.min(1, (now - this.cam.t0) / 650);
      const e = 1 - Math.pow(1 - k, 3);
      this.scale = this.cam.s0 + (this.cam.s1 - this.cam.s0) * e;
      this.ox = this.cam.x0 + (this.cam.x1 - this.cam.x0) * e;
      this.oy = this.cam.y0 + (this.cam.y1 - this.cam.y0) * e;
      this.clamp();
      if (k >= 1) this.cam = null;
    }
    if (this.wave < 1) this.wave = Math.min(1, this.wave + 1 / 90);
    if (this.intro < 1) this.intro = Math.min(1, this.intro + 1 / 80);
    this.draw(settings.anim ? now / 1000 : 0);
  }

  draw(t) {
    const { ctx, dpr } = this;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, this.w, this.h);
    if (!this.data) return;
    const s = this.scale;
    ctx.save();
    ctx.translate(this.ox, this.oy);
    ctx.scale(s, s);
    ctx.globalAlpha = 0.35 + 0.65 * this.intro;
    const px = (v) => v / s;                       // screen px -> world units
    const zf = Math.sqrt(s);
    const W = (p) => this.world(p.position.x, p.position.y);
    const unit = this.unit();

    // Sectors: enemy ones tinted, hatched (screen-space stripes) and outlined with a glow.
    this.patterns ??= {};
    const hatch = (color) => {
      const c = document.createElement("canvas");
      c.width = c.height = 14 * dpr;
      const g = c.getContext("2d");
      g.scale(dpr, dpr);
      g.strokeStyle = color; g.globalAlpha = 0.3; g.lineWidth = 3;
      g.beginPath();
      for (const o of [-14, 0, 14]) { g.moveTo(o, 14); g.lineTo(o + 14, 0); }
      g.stroke();
      return ctx.createPattern(c, "repeat");
    };
    for (const cell of this.cells) {
      ctx.beginPath();
      for (let i = 0; i < cell.pts.length; i += 2) {
        const [x, y] = this.world(cell.pts[i], cell.pts[i + 1]);
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.closePath();
      if (cell.color) {
        ctx.fillStyle = cell.color + "1A"; ctx.fill();
        ctx.save();
        ctx.clip();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.fillStyle = (this.patterns[cell.color] ??= hatch(cell.color));
        ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        ctx.restore();
        ctx.lineJoin = "round";
        ctx.strokeStyle = cell.color + "38"; ctx.lineWidth = px(7); ctx.stroke();
        ctx.strokeStyle = cell.color + "F2"; ctx.lineWidth = px(2.6); ctx.stroke();
      } else {
        ctx.strokeStyle = "rgba(255,255,255,.16)"; ctx.lineWidth = px(1.4); ctx.stroke();
      }
    }

    // Supply lines with bloom, gradient between node colours.
    for (const p of this.planets) {
      const [x1, y1] = W(p);
      for (const w of p.waypoints) {
        const q = this.byIndex.get(w);
        if (!q || (w < p.index && q.waypoints.includes(p.index))) continue;
        const [x2, y2] = W(q);
        const a = this.nodeColors.get(p.index), b = this.nodeColors.get(w);
        const alpha = Math.max(0.25, Math.min(this.visibility(p), this.visibility(q)));
        const grad = ctx.createLinearGradient(x1, y1, x2, y2);
        grad.addColorStop(0, rgba(a)); grad.addColorStop(1, rgba(b));
        ctx.strokeStyle = grad; ctx.lineCap = "round";
        for (const [wd, al] of [[9, 0.1], [4.5, 0.22], [1.8, 1]]) {
          ctx.globalAlpha = al * alpha * (0.35 + 0.65 * this.intro);
          ctx.lineWidth = px(wd);
          ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
        }
      }
    }
    ctx.globalAlpha = 1;

    // The Gloom: drifting amber haze.
    for (const i of this.gloom) {
      const p = this.byIndex.get(i); if (!p) continue;
      const [cx, cy] = W(p);
      for (let k = 0; k < 3; k++) {
        const a = t / 4 + k * 2.1 + i;
        const x = cx + Math.cos(a) * 0.03 * unit, y = cy + Math.sin(a * 0.7) * 0.03 * unit, r = (0.1 + 0.025 * k) * unit;
        const g = ctx.createRadialGradient(x, y, 0, x, y, r);
        g.addColorStop(0, `rgba(${GLOOM},.28)`); g.addColorStop(0.5, `rgba(${GLOOM},.1)`); g.addColorStop(1, `rgba(${GLOOM},0)`);
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill();
      }
    }

    // Enemy attacks: marching dashes.
    ctx.setLineDash([px(6), px(4)]);
    ctx.lineDashOffset = -t * px(20);
    for (const a of this.planets) for (const target of a.attacking) {
      const b = this.byIndex.get(target); if (!b) continue;
      const [x1, y1] = W(a), [x2, y2] = W(b);
      ctx.strokeStyle = COLORS[a.owner] || "#fff"; ctx.lineWidth = px(2);
      ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
    }
    ctx.setLineDash([]);

    // Text is collected here and drawn in screen space at the end (crisp, even letter spacing).
    const labels = [];
    if (s > 1.25 && s < 4.1) {
      for (const [name, x, y] of this.centroids) { const [wx, wy] = this.world(x, y); labels.push([name.toUpperCase(), wx, wy, 1, true]); }
    }

    // Planets.
    const base = px(3.5) * zf;
    const beat = settings.anim ? 0.6 + 0.4 * Math.sin(t * 3.4) : 0.8;
    const redraw = () => {};
    for (const p of this.planets) {
      const [cx, cy] = W(p);
      const v = this.visibility(p);
      if (v <= 0.01) continue;
      const front = this.fronts.has(p.index);
      const r = front ? base * 1.6 : base;
      if (this.blackHoles.has(p.index)) { this.blackHole(cx, cy, r * 1.8, t, px); continue; }
      if (this.fractured.has(p.index)) { this.rubble(p.index, cx, cy, r * 1.9, t); continue; }
      const se = p.index === 0;
      const art = img(planetArt(this.A, p, this.data.effects.get(p.index) || []), redraw);
      const shown = art && (se || front || s >= 1.8);
      const ringR = shown ? r * 1.5 : r;
      const drops = se ? null : this.drops.get(p.index);
      ctx.globalAlpha = v;
      if (drops) this.droplets(drops, cx, cy, ringR, px(6.5) * Math.min(zf, 1.8));
      if (shown) {
        const d = se ? r * 4 : r * 2.8;
        if (!se) { ctx.fillStyle = (COLORS[p.owner] || "#999") + "59"; ctx.beginPath(); ctx.arc(cx, cy, d / 2 + px(1.5), 0, 7); ctx.fill(); }
        ctx.drawImage(art, cx - d / 2, cy - d / 2, d, d);
      } else {
        ctx.fillStyle = COLORS[p.owner] || "#999"; ctx.beginPath(); ctx.arc(cx, cy, r, 0, 7); ctx.fill();
        ctx.strokeStyle = "rgba(255,255,255,.35)"; ctx.lineWidth = px(0.8); ctx.stroke();
      }
      ctx.lineWidth = px(2);
      if (p.event) {
        ctx.strokeStyle = `rgba(255,75,75,${beat})`; ctx.beginPath(); ctx.arc(cx, cy, ringR + px(5), 0, 7); ctx.stroke();
        ctx.strokeStyle = `rgba(255,75,75,${(1 - beat) * 0.5})`; ctx.beginPath(); ctx.arc(cx, cy, ringR + px(5 + 8 * beat), 0, 7); ctx.stroke();
      } else if (front) {
        ctx.strokeStyle = "rgba(255,255,255,.55)"; ctx.lineWidth = px(1); ctx.beginPath(); ctx.arc(cx, cy, ringR + px(3), 0, 7); ctx.stroke();
      }
      if (this.moTargets.has(p.index)) { ctx.strokeStyle = "#FFC400"; ctx.lineWidth = px(1.5); ctx.beginPath(); ctx.arc(cx, cy, ringR + px(8), 0, 7); ctx.stroke(); }
      if (p.index === this.data.dss?.planet) {
        const dss = img(icon(this.A, "DSS_Icon"), redraw), d = px(18) * Math.min(zf, 1.8);
        const ax = cx + ringR + px(6), ay = cy - ringR - d - px(2);
        ctx.fillStyle = `rgba(255,196,0,${0.2 + 0.3 * beat})`; ctx.beginPath(); ctx.arc(ax + d / 2, ay + d / 2, d * 0.8, 0, 7); ctx.fill();
        if (dss) ctx.drawImage(dss, ax, ay, d, d);
      }
      if (p.index === this.selected) {
        ctx.strokeStyle = "#fff"; ctx.lineWidth = px(2); ctx.lineCap = "round";
        const R = ringR + px(11), rot = t * 4;
        for (let q = 0; q < 4; q++) { ctx.beginPath(); ctx.arc(cx, cy, R, rot + q * Math.PI / 2 + 0.26, rot + q * Math.PI / 2 + 1.3); ctx.stroke(); }
      }
      const small = this.w < 600 && s < 1.6;
      if ((front && (!small || p.event)) || s >= 2.6 || p.index === this.data.dss?.planet || p.index === this.hover) {
        labels.push([p.name, cx, cy + ringR + px(14) + (drops ? px(6.5) * Math.min(zf, 1.8) * 2.9 : 0), v, false]);
      }
      ctx.globalAlpha = 1;
    }

    // Super Earth wave and the opening radar sweep.
    const [sx, sy] = this.world(0, 0);
    if (this.wave < 1) {
      const r = this.wave * 1.25 * unit, f = 1 - this.wave;
      ctx.fillStyle = `rgba(63,169,255,${0.1 * f})`; ctx.beginPath(); ctx.arc(sx, sy, r, 0, 7); ctx.fill();
      ctx.strokeStyle = `rgba(63,169,255,${0.25 * f})`; ctx.lineWidth = px(14); ctx.stroke();
      ctx.strokeStyle = `rgba(63,169,255,${0.95 * f})`; ctx.lineWidth = px(2.5); ctx.stroke();
    }
    if (this.intro < 1) {
      const r = this.intro * 1.1 * unit, f = 1 - this.intro;
      ctx.strokeStyle = `rgba(255,196,0,${0.6 * f})`; ctx.lineWidth = px(2); ctx.beginPath(); ctx.arc(sx, sy, r, 0, 7); ctx.stroke();
    }
    ctx.restore();
    ctx.textAlign = "center";
    for (const [text, x, y, a, sector] of labels) {
      ctx.globalAlpha = a;
      ctx.font = sector ? '700 10px "Chakra Petch"' : '600 11px "Chakra Petch"';
      ctx.fillStyle = sector ? "rgba(255,255,255,.3)" : "rgba(255,255,255,.95)";
      ctx.shadowColor = "rgba(0,0,0,.9)"; ctx.shadowBlur = sector ? 0 : 3;
      ctx.fillText(text, x * s + this.ox, y * s + this.oy);
    }
    ctx.shadowBlur = 0; ctx.globalAlpha = 1;
  }

  droplets(list, cx, cy, R, b) {
    const { ctx } = this;
    const spread = (34 * Math.PI) / 180, first = Math.PI / 2 - (spread * (list.length - 1)) / 2;
    list.forEach((d, i) => {
      const a = first + i * spread, ux = Math.cos(a), uy = Math.sin(a), nx = -uy, ny = ux;
      const tip = [cx + ux * R * 0.75, cy + uy * R * 0.75], c = [cx + ux * (R + b * 1.5), cy + uy * (R + b * 1.5)];
      const r1 = [c[0] + nx * b, c[1] + ny * b], r2 = [c[0] - nx * b, c[1] - ny * b], bot = [c[0] + ux * b, c[1] + uy * b];
      ctx.beginPath();
      ctx.moveTo(...tip);
      ctx.bezierCurveTo(tip[0] + nx * b * 0.25, tip[1] + ny * b * 0.25, r1[0] - ux * b * 0.9, r1[1] - uy * b * 0.9, ...r1);
      ctx.bezierCurveTo(r1[0] + ux * b * 0.55, r1[1] + uy * b * 0.55, bot[0] + nx * b * 0.55, bot[1] + ny * b * 0.55, ...bot);
      ctx.bezierCurveTo(bot[0] - nx * b * 0.55, bot[1] - ny * b * 0.55, r2[0] + ux * b * 0.55, r2[1] + uy * b * 0.55, ...r2);
      ctx.bezierCurveTo(r2[0] - ux * b * 0.9, r2[1] - uy * b * 0.9, tip[0] - nx * b * 0.25, tip[1] - ny * b * 0.25, ...tip);
      ctx.closePath();
      ctx.fillStyle = d.color; ctx.fill();
      ctx.strokeStyle = "rgba(0,0,0,.5)"; ctx.lineWidth = b * 0.12; ctx.stroke();
      const im = img(d.url, () => {});
      if (im) {
        const s = b * 1.35;
        // Dark emblem: draw the white icon through a darkening composite.
        ctx.save(); ctx.filter = "brightness(0) opacity(.9)"; ctx.drawImage(im, c[0] - s / 2, c[1] - s / 2, s, s); ctx.restore();
      }
    });
  }

  blackHole(cx, cy, r, t, px) {
    const { ctx } = this;
    const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r * 4);
    g.addColorStop(0, "rgba(180,92,255,.45)"); g.addColorStop(1, "rgba(180,92,255,0)");
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, r * 4, 0, 7); ctx.fill();
    ctx.save(); ctx.translate(cx, cy); ctx.rotate(t * 3);
    const disk = ctx.createConicGradient ? ctx.createConicGradient(0, 0, 0) : null;
    if (disk) { disk.addColorStop(0, "#FFB347"); disk.addColorStop(0.3, "#B45CFF"); disk.addColorStop(0.5, "rgba(0,0,0,0)"); disk.addColorStop(0.75, "#FF6FD8"); disk.addColorStop(1, "#FFB347"); }
    ctx.strokeStyle = disk || "#B45CFF"; ctx.lineWidth = r * 0.55; ctx.beginPath(); ctx.arc(0, 0, r * 1.55, 0, 7); ctx.stroke();
    ctx.restore();
    ctx.fillStyle = "#000"; ctx.beginPath(); ctx.arc(cx, cy, r, 0, 7); ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,.55)"; ctx.lineWidth = px(0.8); ctx.stroke();
  }

  rubble(seed, cx, cy, r, t) {
    const { ctx } = this;
    if (!this.rocks.has(seed)) {
      let x = seed * 7919 + 1;
      const rnd = () => ((x = (x * 16807) % 2147483647) / 2147483647);
      const pal = ["#8A8F98", "#6E6358", "#A39A8C", "#5C6470"];
      this.rocks.set(seed, Array.from({ length: 16 }, () => ({ a: rnd() * 6.28, d: 0.15 + rnd() * 1.35, s: 0.1 + rnd() * 0.22, v: 0.4 + rnd() * 0.9, c: pal[Math.floor(rnd() * 4)] })));
    }
    for (const k of this.rocks.get(seed)) {
      const a = k.a + (t / 4) * k.v, x = cx + Math.cos(a) * r * k.d, y = cy + Math.sin(a) * r * k.d;
      ctx.fillStyle = k.c; ctx.beginPath(); ctx.arc(x, y, r * k.s, 0, 7); ctx.fill();
      ctx.fillStyle = "rgba(255,255,255,.25)"; ctx.beginPath(); ctx.arc(x - r * k.s * 0.3, y - r * k.s * 0.3, r * k.s * 0.45, 0, 7); ctx.fill();
    }
  }
}
