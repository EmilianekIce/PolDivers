// PolDivers 3D galaxy map (WebGL, three.js). Shared by the website and the Android app (WebView).
//
// It renders a plain "map model" -- planets with positions, owners, supply links, effect droplets,
// sector cells -- built by the host (Web/js/map.js on the site, Map3dModel.kt in the app), so both
// show exactly the same map. Everything flat (sectors, supply lines, the Gloom) lies on the galactic
// plane; planets float just above it as billboards, attacks arc over it, the black hole and debris
// are real 3D objects. Controls: drag = pan, right drag / two fingers = rotate & tilt, wheel /
// pinch = zoom.
import * as THREE from "./three.min.js"; // three.js r186, MIT -- see THREE-LICENSE.txt

const { MapControls, LineSegments2, LineSegmentsGeometry, LineMaterial, Line2, LineGeometry } = THREE;

const R = 100;                    // map units [-1, 1] -> world [-R, R]
const PLANET_Y = 0.7;             // planets float slightly above the plane
const FOV = 42;
const DEFAULT_POLAR = 0.62;       // ~35 degrees from straight down
const COLORS = { Humans: "#4FA3E0", Terminids: "#E8A33D", Automaton: "#E0483E", Illuminate: "#8A5CF6" };
const RED = "#FF4B4B", YELLOW = "#FFC400", WAVE = "#3FA9FF", GLOOM = "216,169,69";

THREE.ColorManagement.enabled = false; // colours are authored for the screen, as on the 2D map

const toWorld = (x, y, h = 0) => new THREE.Vector3(x * R, h, -y * R);

// ---------- generated textures ----------
const texCache = new Map();
function canvasTex(key, size, draw) {
  let t = texCache.get(key);
  if (!t) {
    const c = document.createElement("canvas");
    c.width = c.height = size;
    draw(c.getContext("2d"), size);
    t = new THREE.CanvasTexture(c);
    t.anisotropy = 4;
    texCache.set(key, t);
  }
  return t;
}
const discTex = () => canvasTex("disc", 64, (g, s) => {
  g.fillStyle = "#fff"; g.beginPath(); g.arc(s / 2, s / 2, s / 2 - 2, 0, 7); g.fill();
});
/** White ring, radius 0.47 of the texture. */
const ringTex = (w = 7) => canvasTex("ring" + w, 256, (g, s) => {
  g.strokeStyle = "#fff"; g.lineWidth = w; g.beginPath(); g.arc(s / 2, s / 2, s * 0.47, 0, 7); g.stroke();
});
const glowTex = () => canvasTex("glow", 128, (g, s) => {
  const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  r.addColorStop(0, "rgba(255,255,255,1)"); r.addColorStop(0.4, "rgba(255,255,255,.35)"); r.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = r; g.fillRect(0, 0, s, s);
});
const gloomTex = () => canvasTex("gloom", 128, (g, s) => {
  const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  r.addColorStop(0, `rgba(${GLOOM},.30)`); r.addColorStop(0.5, `rgba(${GLOOM},.11)`); r.addColorStop(1, `rgba(${GLOOM},0)`);
  g.fillStyle = r; g.fillRect(0, 0, s, s);
});
/** Four rotating brackets around the selected planet. */
const bracketTex = () => canvasTex("brackets", 256, (g, s) => {
  g.strokeStyle = "#fff"; g.lineWidth = 9; g.lineCap = "round";
  for (let q = 0; q < 4; q++) { g.beginPath(); g.arc(s / 2, s / 2, s * 0.44, q * Math.PI / 2 + 0.26, q * Math.PI / 2 + 1.3); g.stroke(); }
});
const accretionTex = () => canvasTex("accretion", 256, (g, s) => {
  if (g.createConicGradient) {
    const c = g.createConicGradient(0, s / 2, s / 2);
    c.addColorStop(0, "#FFB347"); c.addColorStop(0.3, "#B45CFF"); c.addColorStop(0.5, "rgba(0,0,0,0)"); c.addColorStop(0.75, "#FF6FD8"); c.addColorStop(1, "#FFB347");
    g.fillStyle = c;
  } else g.fillStyle = "#B45CFF";
  g.fillRect(0, 0, s, s);
});
const galaxyTex = () => canvasTex("galaxy", 512, (g, s) => {
  const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  r.addColorStop(0, "rgba(60,110,170,.32)"); r.addColorStop(0.35, "rgba(40,70,120,.16)"); r.addColorStop(0.75, "rgba(25,40,70,.07)"); r.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = r; g.fillRect(0, 0, s, s);
});

const loader = new THREE.TextureLoader();
const urlTex = new Map();
function imageTex(url, onload) {
  let t = urlTex.get(url);
  if (!t) {
    t = loader.load(url, onload);
    t.anisotropy = 4;
    urlTex.set(url, t);
  }
  return t;
}
const imgCache = new Map();
function image(url, onload) {
  let im = imgCache.get(url);
  if (!im) { im = new Image(); im.crossOrigin = "anonymous"; im.src = url; imgCache.set(url, im); }
  if (im.complete && im.naturalWidth) return im;
  im.addEventListener("load", onload, { once: true });
  return null;
}

/**
 * Effect droplets hanging under a planet, baked into one texture (centre = planet centre, planet
 * ring radius = 62 px of 256): same teardrops, colours and dark emblems as the 2D map.
 */
function dropletTex(drops, onUpdate) {
  const key = "drops:" + drops.map((d) => d.c + d.icon).join("|");
  let t = texCache.get(key);
  if (t) return t;
  const c = document.createElement("canvas");
  c.width = c.height = 256;
  t = new THREE.CanvasTexture(c);
  texCache.set(key, t);
  const paint = () => {
    const g = c.getContext("2d");
    g.clearRect(0, 0, 256, 256);
    const cx = 128, cy = 128, Rr = 62, b = 25;
    const spread = (34 * Math.PI) / 180, first = Math.PI / 2 - (spread * (drops.length - 1)) / 2;
    drops.forEach((d, i) => {
      const a = first + i * spread, ux = Math.cos(a), uy = Math.sin(a), nx = -uy, ny = ux;
      const tip = [cx + ux * Rr * 0.75, cy + uy * Rr * 0.75], m = [cx + ux * (Rr + b * 1.5), cy + uy * (Rr + b * 1.5)];
      const r1 = [m[0] + nx * b, m[1] + ny * b], r2 = [m[0] - nx * b, m[1] - ny * b], bot = [m[0] + ux * b, m[1] + uy * b];
      g.beginPath();
      g.moveTo(...tip);
      g.bezierCurveTo(tip[0] + nx * b * 0.25, tip[1] + ny * b * 0.25, r1[0] - ux * b * 0.9, r1[1] - uy * b * 0.9, ...r1);
      g.bezierCurveTo(r1[0] + ux * b * 0.55, r1[1] + uy * b * 0.55, bot[0] + nx * b * 0.55, bot[1] + ny * b * 0.55, ...bot);
      g.bezierCurveTo(bot[0] - nx * b * 0.55, bot[1] - ny * b * 0.55, r2[0] + ux * b * 0.55, r2[1] + uy * b * 0.55, ...r2);
      g.bezierCurveTo(r2[0] - ux * b * 0.9, r2[1] - uy * b * 0.9, tip[0] - nx * b * 0.25, tip[1] - ny * b * 0.25, ...tip);
      g.closePath();
      g.fillStyle = d.c; g.fill();
      g.strokeStyle = "rgba(0,0,0,.5)"; g.lineWidth = 3; g.stroke();
      const im = d.icon && image(d.icon, () => { paint(); t.needsUpdate = true; onUpdate(); });
      if (im) {
        const s = b * 1.35;
        g.save(); g.filter = "brightness(0) opacity(.9)"; g.drawImage(im, m[0] - s / 2, m[1] - s / 2, s, s); g.restore();
      }
    });
  };
  paint();
  return t;
}

function lineMaterial(opts) {
  return new LineMaterial({ transparent: true, depthWrite: false, ...opts });
}
const hexRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);

export function webglAvailable() {
  try {
    const c = document.createElement("canvas");
    return !!(window.WebGLRenderingContext && (c.getContext("webgl2") || c.getContext("webgl")));
  } catch { return false; }
}

export class Galaxy3D {
  /**
   * @param container element to fill
   * @param opts.onSelect(planet) tapped planet (a model planet)
   * @param opts.onHover(planet|null, [x, y]) pointer over a planet (desktop)
   * @param opts.anim animations on/off
   * @param opts.focusShift move focused planets up the screen (the app's details sheet covers the bottom)
   */
  constructor(container, opts = {}) {
    this.opts = opts;
    this.container = container;
    this.anim = opts.anim !== false;
    this.hideOurs = true;
    this.wave = 1;
    this.intro = this.anim ? 0 : 1;
    this.selected = null;
    this.hover = null;
    this.model = null;
    this.planets = [];

    const renderer = (this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "high-performance" }));
    renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x07090c, 1);
    const canvas = (this.canvas = renderer.domElement);
    canvas.style.cssText = "position:absolute;inset:0;width:100%;height:100%;display:block;touch-action:none";
    container.appendChild(canvas);
    this.labels = document.createElement("canvas");
    this.labels.style.cssText = "position:absolute;inset:0;width:100%;height:100%;pointer-events:none";
    container.appendChild(this.labels);
    this.lctx = this.labels.getContext("2d");

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(FOV, 1, 0.5, 6000);
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.75));
    const sun = new THREE.DirectionalLight(0xffffff, 1.5);
    sun.position.set(60, 120, 40);
    this.scene.add(sun);
    this.scene.add(this.backdrop());
    this.world = new THREE.Group();
    this.scene.add(this.world);

    const controls = (this.controls = new MapControls(this.camera, canvas));
    controls.enableDamping = true;
    controls.dampingFactor = 0.12;
    controls.screenSpacePanning = false;
    controls.minDistance = 14;
    controls.maxDistance = 420;
    controls.maxPolarAngle = 1.32;
    controls.zoomSpeed = 1.2;
    controls.rotateSpeed = 0.6;
    if ("zoomToCursor" in controls) controls.zoomToCursor = true;
    controls.addEventListener("start", () => { this.fly = null; this.active = true; this.lastInput = performance.now(); });
    controls.addEventListener("end", () => { this.active = false; this.lastInput = performance.now(); });
    controls.addEventListener("change", () => { this.clampTarget(); this.dirty = true; });

    this.resize();
    this.home();
    this.ro = new ResizeObserver(() => this.resize());
    this.ro.observe(container);
    this.bindPointer();
    this.last = performance.now();
    this.loop = this.loop.bind(this);
    this.raf = requestAnimationFrame(this.loop);
  }

  destroy() {
    cancelAnimationFrame(this.raf);
    this.ro.disconnect();
    this.controls.dispose();
    this.clearWorld();
    this.renderer.dispose();
    this.canvas.remove();
    this.labels.remove();
  }

  // ---------- camera ----------
  resize() {
    const w = this.container.clientWidth || 1, h = this.container.clientHeight || 1;
    this.w = w; this.h = h;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.renderer.setSize(w, h, false);
    this.labels.width = Math.round(w * this.dpr);
    this.labels.height = Math.round(h * this.dpr);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    for (const m of this.lineMats || []) m.resolution.set(w, h);
    this.dirty = true;
  }
  /** Distance at which the whole galaxy fits the view. */
  fitDistance() {
    const t = Math.tan((FOV * Math.PI) / 360);
    return Math.min(400, (R * 1.12) / t / Math.min(1, this.camera.aspect));
  }
  offsetFor(dist, polar = DEFAULT_POLAR, azimuth = 0) {
    return new THREE.Vector3(Math.sin(polar) * Math.sin(azimuth), Math.cos(polar), Math.sin(polar) * Math.cos(azimuth)).multiplyScalar(dist);
  }
  home() {
    this.controls.target.set(0, 0, 0);
    this.camera.position.copy(this.offsetFor(this.fitDistance()));
    this.controls.update();
  }
  clampTarget() {
    const t = this.controls.target;
    const len = Math.hypot(t.x, t.z), max = R * 1.15;
    if (len > max) {
      const k = max / len, dx = t.x * (k - 1), dz = t.z * (k - 1);
      t.x += dx; t.z += dz;
      this.camera.position.x += dx; this.camera.position.z += dz;
    }
    t.y = 0;
  }
  flyTo(target, offset) {
    if (!this.anim) {
      this.controls.target.copy(target);
      this.camera.position.copy(target).add(offset);
      this.controls.update();
      return;
    }
    this.fly = {
      t0: performance.now(),
      a: this.controls.target.clone(), b: target.clone(),
      oa: this.camera.position.clone().sub(this.controls.target), ob: offset.clone(),
    };
  }
  focus(index) {
    const p = this.byIndex?.get(index);
    if (!p) return;
    this.selected = index;
    const off = this.camera.position.clone().sub(this.controls.target);
    const dist = Math.min(off.length(), 62);
    off.setLength(dist);
    const target = toWorld(p.x, p.y);
    const shift = this.opts.focusShift || 0;
    if (shift) {
      const toward = new THREE.Vector3(off.x, 0, off.z);
      if (toward.lengthSq() > 1e-6) target.add(toward.setLength(dist * shift));
    }
    this.flyTo(target, off);
    this.dirty = true;
  }
  reset() { this.selected = null; this.flyTo(new THREE.Vector3(), this.offsetFor(this.fitDistance())); }
  deselect() { this.selected = null; this.dirty = true; }
  /** Turns the view around the current target by [angle] radians (buttons). */
  rotateBy(angle) {
    const off = this.camera.position.clone().sub(this.controls.target);
    off.applyAxisAngle(new THREE.Vector3(0, 1, 0), angle);
    this.flyTo(this.controls.target.clone(), off);
  }
  /** Tilts between top-down and a low perspective (buttons). */
  tiltBy(delta) {
    const off = this.camera.position.clone().sub(this.controls.target);
    const s = new THREE.Spherical().setFromVector3(off);
    s.phi = Math.min(this.controls.maxPolarAngle, Math.max(0.05, s.phi + delta));
    this.flyTo(this.controls.target.clone(), new THREE.Vector3().setFromSpherical(s));
  }

  setAnim(v) { this.anim = v; if (!v) { this.intro = 1; this.wave = 1; } this.dirty = true; }
  /** Shows / hides our quiet worlds; [animate] rolls the Super Earth wave out from the centre. */
  setHideOurs(v, animate = true) { this.hideOurs = v; this.wave = this.anim && animate ? 0 : 1; this.dirty = true; }

  // ---------- static backdrop: stars, galaxy glow, polar grid ----------
  backdrop() {
    const g = new THREE.Group();
    const n = 2200, pos = new Float32Array(n * 3), col = new Float32Array(n * 3);
    let seed = 7;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    for (let i = 0; i < n; i++) {
      const u = rnd() * 2 - 1, th = rnd() * Math.PI * 2, r = 1600 + rnd() * 1800, s = Math.sqrt(1 - u * u);
      pos.set([r * s * Math.cos(th), r * u, r * s * Math.sin(th)], i * 3);
      const k = 0.35 + rnd() * 0.65, tint = rnd();
      col.set(tint < 0.15 ? [k, k * 0.85, k * 0.6] : tint < 0.3 ? [k * 0.7, k * 0.8, k] : [k, k, k], i * 3);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    geo.setAttribute("color", new THREE.BufferAttribute(col, 3));
    g.add(new THREE.Points(geo, new THREE.PointsMaterial({ size: 1.6, sizeAttenuation: false, vertexColors: true, depthWrite: false })));

    const disc = new THREE.Mesh(new THREE.PlaneGeometry(R * 3, R * 3), new THREE.MeshBasicMaterial({ map: galaxyTex(), transparent: true, depthWrite: false }));
    disc.rotation.x = -Math.PI / 2;
    disc.position.y = -0.6;
    disc.renderOrder = -2;
    g.add(disc);

    // Faint polar grid, like the war table in the game.
    const pts = [];
    for (const r of [0.25, 0.5, 0.75, 1, 1.2]) {
      for (let i = 0; i < 128; i++) {
        const a0 = (i / 128) * Math.PI * 2, a1 = ((i + 1) / 128) * Math.PI * 2;
        pts.push(Math.cos(a0) * r * R, -0.3, Math.sin(a0) * r * R, Math.cos(a1) * r * R, -0.3, Math.sin(a1) * r * R);
      }
    }
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2;
      pts.push(Math.cos(a) * 0.08 * R, -0.3, Math.sin(a) * 0.08 * R, Math.cos(a) * 1.2 * R, -0.3, Math.sin(a) * 1.2 * R);
    }
    const grid = new THREE.LineSegments(
      new THREE.BufferGeometry().setAttribute("position", new THREE.Float32BufferAttribute(pts, 3)),
      new THREE.LineBasicMaterial({ color: 0x7fa8d8, transparent: true, opacity: 0.07, depthWrite: false }),
    );
    grid.renderOrder = -1;
    g.add(grid);
    return g;
  }

  // ---------- model -> scene ----------
  clearWorld() {
    // Each geometry/material once (many objects share them); sprites share three's own quad.
    const geos = new Set(), mats = new Set();
    this.world.traverse((o) => {
      if (o.geometry && !o.isSprite) geos.add(o.geometry);
      if (o.material) mats.add(o.material);
    });
    geos.forEach((g) => g.dispose());
    mats.forEach((m) => m.dispose());
    this.world.clear();
  }

  setModel(model) {
    if (!this.model && this.anim) this.intro = 0; // opening sweep when the first data arrives
    this.model = model;
    this.clearWorld();
    this.lineMats = [];
    const planets = (this.planets = model.planets);
    this.byIndex = new Map(planets.map((p) => [p.i, p]));
    this.buildSectors(model.cells || []);
    this.buildSupply(planets);
    this.buildGloom(planets);
    this.buildAttacks(planets);
    this.buildPlanets(planets, model);
    this.buildWave();
    this.sectorLabels = (model.sectors || []).map(([name, x, y]) => ({ text: name.toUpperCase(), pos: toWorld(x, y) }));
    for (const m of this.lineMats) m.resolution.set(this.w, this.h);
    this.dirty = true;
  }

  addLines(positions, colors, { width, opacity, order, additive = false, dashed = false }) {
    if (!positions.length) return null;
    const geo = new LineSegmentsGeometry();
    geo.setPositions(positions);
    geo.setColors(colors);
    const mat = lineMaterial({ vertexColors: true, linewidth: width, opacity, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending, dashed });
    this.lineMats.push(mat);
    const line = new LineSegments2(geo, mat);
    line.renderOrder = order;
    if (dashed) line.computeLineDistances();
    this.world.add(line);
    return line;
  }

  buildSectors(cells) {
    const fillMats = new Map();
    const fillMat = (hex) => {
      let m = fillMats.get(hex);
      if (!m) {
        m = new THREE.ShaderMaterial({
          uniforms: { color: { value: new THREE.Color(hex) }, dpr: { value: this.dpr } },
          vertexShader: "void main(){gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}",
          // Screen-space diagonal hatching, like the 2D map (no moire when zoomed out or tilted).
          fragmentShader: "uniform vec3 color;uniform float dpr;void main(){float s=mod(gl_FragCoord.x+gl_FragCoord.y,14.0*dpr);gl_FragColor=vec4(color,s<3.0*dpr?0.36:0.10);}",
          transparent: true, depthWrite: false, side: THREE.DoubleSide,
        });
        fillMats.set(hex, m);
      }
      return m;
    };
    const neutral = [], neutralC = [], enemy = [], enemyC = [];
    for (const cell of cells) {
      const pts = cell.pts;
      const n = pts.length / 2;
      const seg = (arr, carr, rgb, y) => {
        for (let i = 0; i < n; i++) {
          const j = (i + 1) % n;
          arr.push(pts[2 * i] * R, y, -pts[2 * i + 1] * R, pts[2 * j] * R, y, -pts[2 * j + 1] * R);
          carr.push(...rgb, ...rgb);
        }
      };
      if (cell.c) {
        const shape = new THREE.Shape();
        for (let i = 0; i < n; i++) (i ? shape.lineTo : shape.moveTo).call(shape, pts[2 * i] * R, pts[2 * i + 1] * R);
        const mesh = new THREE.Mesh(new THREE.ShapeGeometry(shape), fillMat(cell.c));
        mesh.rotation.x = -Math.PI / 2;
        mesh.renderOrder = 0;
        this.world.add(mesh);
        seg(enemy, enemyC, hexRgb(cell.c), 0.06);
      } else {
        seg(neutral, neutralC, [1, 1, 1], 0.04);
      }
    }
    this.addLines(neutral, neutralC, { width: 1.3, opacity: 0.16, order: 1 });
    this.addLines(enemy, enemyC, { width: 6, opacity: 0.16, order: 1 });
    this.addLines(enemy, enemyC, { width: 2.2, opacity: 0.95, order: 1 });
  }

  buildSupply(planets) {
    const quiet = { p: [], c: [] }, live = { p: [], c: [] };
    for (const p of planets) {
      for (const w of p.links) {
        const q = this.byIndex.get(w);
        if (!q || (w < p.i && q.links.includes(p.i))) continue;
        const set = p.quiet && q.quiet ? quiet : live;
        set.p.push(p.x * R, 0.2, -p.y * R, q.x * R, 0.2, -q.y * R);
        set.c.push(...hexRgb(p.sup), ...hexRgb(q.sup));
      }
    }
    // Bloom: two soft additive halos under a crisp core; the colours blend along each line.
    this.supplyQuiet = [];
    for (const [set, store] of [[live, null], [quiet, this.supplyQuiet]]) {
      for (const [width, opacity] of [[10, 0.09], [5, 0.2], [1.9, 1]]) {
        const l = this.addLines(set.p, set.c, { width, opacity, order: 2, additive: opacity < 1 });
        if (l && store) { l.userData.base = opacity; store.push(l); }
      }
    }
  }

  buildGloom(planets) {
    this.gloom = [];
    const geo = new THREE.PlaneGeometry(1, 1);
    const mat = new THREE.MeshBasicMaterial({ map: gloomTex(), transparent: true, depthWrite: false });
    for (const p of planets.filter((q) => q.gloom)) {
      for (let k = 0; k < 3; k++) {
        const m = new THREE.Mesh(geo, mat);
        m.rotation.x = -Math.PI / 2;
        const size = (0.2 + 0.05 * k) * R;
        m.scale.set(size, size, 1);
        m.position.set(p.x * R, 0.1 + k * 0.01, -p.y * R);
        m.renderOrder = 3;
        m.userData.gloom = { cx: p.x * R, cz: -p.y * R, seed: k * 2.1 + p.i };
        this.world.add(m);
        this.gloom.push(m);
      }
    }
    if (!this.gloom.length) { geo.dispose(); mat.dispose(); }
  }

  buildAttacks(planets) {
    this.attacks = [];
    for (const a of planets) {
      for (const t of a.atk || []) {
        const b = this.byIndex.get(t);
        if (!b) continue;
        const p0 = toWorld(a.x, a.y, PLANET_Y), p1 = toWorld(b.x, b.y, PLANET_Y);
        const len = p0.distanceTo(p1), h = Math.max(2.5, len * 0.22);
        const pos = [], col = [];
        const rgb = hexRgb(COLORS[a.owner] || "#ffffff");
        for (let i = 0; i <= 28; i++) {
          const s = i / 28;
          const v = p0.clone().lerp(p1, s);
          v.y += Math.sin(s * Math.PI) * h;
          pos.push(v.x, v.y, v.z);
          col.push(...rgb);
        }
        const geo = new LineGeometry();
        geo.setPositions(pos);
        geo.setColors(col);
        const mat = lineMaterial({ vertexColors: true, linewidth: 2.2, opacity: 0.95, dashed: true, dashSize: 1.6, gapSize: 1.1 });
        this.lineMats.push(mat);
        const line = new Line2(geo, mat);
        line.computeLineDistances();
        line.renderOrder = 4;
        this.world.add(line);
        this.attacks.push(mat);
      }
    }
  }

  sprite(map, color, order, opacity = 1) {
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map, color, transparent: true, depthWrite: false, opacity }));
    s.renderOrder = order;
    return s;
  }

  buildPlanets(planets, model) {
    this.rocks = [];
    this.spinning = [];
    const rockGeo = new THREE.IcosahedronGeometry(1, 0);
    const rockMats = ["#8A8F98", "#6E6358", "#A39A8C", "#5C6470"].map((c) => new THREE.MeshStandardMaterial({ color: c, flatShading: true, roughness: 0.9 }));
    const onTex = () => (this.dirty = true);
    for (const p of planets) {
      const pos = toWorld(p.x, p.y, PLANET_Y);
      const g = new THREE.Group();
      g.position.copy(pos);
      const se = p.i === 0;
      const size = se ? 5 : p.front ? 2.5 : 1.7;      // planet diameter in world units
      const parts = (p.parts = { group: g, size, sprites: [] });
      const add = (spr, scale, kind) => {
        spr.userData.scale = scale;
        spr.userData.kind = kind;
        spr.userData.opacity = spr.material.opacity;
        g.add(spr);
        parts.sprites.push(spr);
        return spr;
      };

      if (p.kind === "hole") {
        const r = size * 0.85;
        add(this.sprite(glowTex(), "#B45CFF", 5, 0.55), r * 9, "planet");
        const disk = new THREE.Mesh(new THREE.RingGeometry(r * 1.25, r * 2.0, 64), new THREE.MeshBasicMaterial({ map: accretionTex(), transparent: true, side: THREE.DoubleSide, depthWrite: false }));
        disk.rotation.x = -Math.PI / 2;
        disk.renderOrder = 6;
        g.add(disk);
        this.spinning.push(disk);
        const core = new THREE.Mesh(new THREE.SphereGeometry(r, 24, 16), new THREE.MeshBasicMaterial({ color: 0x000000 }));
        g.add(core);
        parts.mesh = [disk, core];
        add(this.sprite(ringTex(5), "#ffffff", 7, 0.5), r * 2.15, "planet");
      } else if (p.kind === "rubble") {
        let seed = p.i * 7919 + 1;
        const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
        const r = size * 1.0;
        parts.mesh = [];
        for (let k = 0; k < 16; k++) {
          const m = new THREE.Mesh(rockGeo, rockMats[Math.floor(rnd() * 4)]);
          const rock = { a: rnd() * 6.28, d: (0.15 + rnd() * 1.35) * r, s: (0.1 + rnd() * 0.22) * r, v: 0.4 + rnd() * 0.9, y: (rnd() - 0.5) * r * 0.5, spin: rnd() * 2 };
          m.scale.setScalar(rock.s);
          m.userData.rock = rock;
          g.add(m);
          this.rocks.push(m);
          parts.mesh.push(m);
        }
      } else {
        if (p.drops?.length && !se) add(this.sprite(dropletTex(p.drops, onTex), "#ffffff", 5), size * 2.2, "planet");
        if (p.art) {
          if (!se) add(this.sprite(discTex(), COLORS[p.owner] || "#999999", 6, 0.38), size * 1.08, "planet");
          add(this.sprite(imageTex(p.art, onTex), "#ffffff", 7), se ? size * 1.45 : size, "planet");
        } else {
          add(this.sprite(discTex(), COLORS[p.owner] || "#999999", 7), size * 0.62, "planet");
        }
      }
      if (p.ev) {
        parts.defense = add(this.sprite(ringTex(10), RED, 8), size * 1.6, "planet");
        parts.defense2 = add(this.sprite(ringTex(6), RED, 8, 0.5), size * 1.6, "planet");
      } else if (p.front) {
        add(this.sprite(ringTex(5), "#ffffff", 8, 0.55), size * 1.42, "planet");
      }
      if (p.mo) add(this.sprite(ringTex(6), YELLOW, 8), size * 1.95, "planet");
      if (p.i === model.dss && model.dssIcon) {
        const glow = add(this.sprite(glowTex(), YELLOW, 8, 0.45), size * 1.1, "planet");
        glow.center.set(-0.35, -0.35);
        parts.dssGlow = glow;
        const icon = add(this.sprite(imageTex(model.dssIcon, onTex), YELLOW, 9), size * 0.75, "planet");
        icon.center.set(-0.55, -0.55);
      }
      parts.select = add(this.sprite(bracketTex(), "#ffffff", 9, 0), size * 2.2, "planet");
      this.world.add(g);
    }
    if (!this.rocks.length) { rockGeo.dispose(); rockMats.forEach((m) => m.dispose()); }
  }

  buildWave() {
    const mk = (geo, color, opacity) => {
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color, transparent: true, opacity, depthWrite: false, side: THREE.DoubleSide }));
      m.rotation.x = -Math.PI / 2;
      m.position.y = 0.3;
      m.renderOrder = 10;
      m.visible = false;
      this.world.add(m);
      return m;
    };
    this.waveFill = mk(new THREE.CircleGeometry(1, 96), WAVE, 0.1);
    this.waveGlow = mk(new THREE.RingGeometry(0.9, 1, 96), WAVE, 0.25);
    this.waveRing = mk(new THREE.RingGeometry(0.985, 1, 128), WAVE, 0.95);
    this.introRing = mk(new THREE.RingGeometry(0.985, 1, 128), YELLOW, 0.6);
  }

  // ---------- per-frame ----------
  /** 1 = drawn, 0 = hidden: quiet worlds of ours fade in/out as the Super Earth wave passes. */
  visibility(p) {
    if (p.i === this.selected || !p.quiet) return 1;
    const d = Math.hypot(p.x, p.y);
    const reached = Math.min(1, Math.max(0, (this.wave * 1.25 - d) / 0.12));
    return this.hideOurs ? 1 - reached : reached;
  }
  /** Planets grow less than the zoom when the camera pulls back, so they stay findable. */
  sizeBoost() {
    const d = this.camera.position.distanceTo(this.controls.target);
    return Math.pow(Math.max(1, d / 90), 0.5);
  }

  update(t, dt) {
    const boost = this.sizeBoost();
    const beat = this.anim ? 0.6 + 0.4 * Math.sin(t * 3.4) : 0.8;
    for (const p of this.planets) {
      const parts = p.parts;
      if (!parts) continue;
      const v = (p.vis = this.visibility(p));
      parts.group.visible = v > 0.01;
      if (!parts.group.visible) continue;
      parts.group.scale.setScalar(boost);
      for (const s of parts.sprites) {
        s.scale.setScalar(s.userData.scale);
        s.material.opacity = s.userData.opacity * v;
      }
      if (parts.defense) {
        parts.defense.material.opacity = beat * v;
        const grow = 1 + 0.55 * beat;
        parts.defense2.scale.setScalar(parts.defense2.userData.scale * grow);
        parts.defense2.material.opacity = (1 - beat) * 0.5 * v;
      }
      if (parts.dssGlow) parts.dssGlow.material.opacity = (0.2 + 0.3 * beat) * v;
      parts.select.material.opacity = p.i === this.selected ? v : 0;
      if (p.i === this.selected) parts.select.material.rotation = -t * 4;
    }
    for (const disk of this.spinning || []) disk.rotation.z = -t * 3;
    for (const m of this.rocks || []) {
      const k = m.userData.rock, a = k.a + (t / 4) * k.v;
      m.position.set(Math.cos(a) * k.d, k.y + Math.sin(t * k.v + k.a) * 0.15, Math.sin(a) * k.d);
      m.rotation.set(t * k.spin, t * k.v, 0);
    }
    for (const m of this.gloom || []) {
      const g = m.userData.gloom, a = t / 4 + g.seed;
      m.position.x = g.cx + Math.cos(a) * 0.03 * R;
      m.position.z = g.cz + Math.sin(a * 0.7) * 0.03 * R;
    }
    for (const mat of this.attacks || []) mat.dashOffset = -t * 5;
    // Quiet supply lines fade with the worlds they join (never below a quarter).
    const qv = this.hideOurs ? 1 - this.wave : this.wave;
    for (const l of this.supplyQuiet || []) l.material.opacity = l.userData.base * Math.max(0.25, qv);
    // Super Earth wave and the opening sweep.
    const wv = this.wave < 1;
    for (const [m, a] of [[this.waveFill, 0.1], [this.waveGlow, 0.25], [this.waveRing, 0.95]]) {
      if (!m) continue;
      m.visible = wv;
      if (wv) { const r = Math.max(0.01, this.wave * 1.25 * R); m.scale.set(r, r, 1); m.material.opacity = a * (1 - this.wave); }
    }
    if (this.introRing) {
      this.introRing.visible = this.intro < 1;
      const r = Math.max(0.01, this.intro * 1.1 * R);
      this.introRing.scale.set(r, r, 1);
      this.introRing.material.opacity = 0.6 * (1 - this.intro);
    }
    if (this.wave < 1) this.wave = Math.min(1, this.wave + dt / 1.5);
    if (this.intro < 1) this.intro = Math.min(1, this.intro + dt / 1.4);
  }

  loop(now) {
    this.raf = requestAnimationFrame(this.loop);
    const dt = Math.min(0.1, (now - this.last) / 1000);
    this.last = now;
    if (this.fly) {
      const k = Math.min(1, (now - this.fly.t0) / 700), e = 1 - Math.pow(1 - k, 3);
      this.controls.target.lerpVectors(this.fly.a, this.fly.b, e);
      const off = new THREE.Vector3().lerpVectors(this.fly.oa, this.fly.ob, e);
      this.camera.position.copy(this.controls.target).add(off);
      if (k >= 1) this.fly = null;
      this.dirty = true;
    }
    const moved = this.controls.update();
    if (!this.model) return;
    const busy = this.fly || moved || this.active || this.wave < 1 || this.intro < 1 || this.dirty;
    // Idle with animations on: 30 fps is plenty for pulses and drifting fog (saves battery).
    if (!busy) {
      if (!this.anim) return;
      this.skip = !this.skip;
      if (this.skip) return;
    }
    this.dirty = false;
    const t = this.anim ? now / 1000 : 0;
    this.update(t, dt);
    this.renderer.render(this.scene, this.camera);
    this.drawLabels();
  }

  // ---------- screen-space helpers ----------
  /** Screen position (CSS px) and on-screen size of a world point; null when behind the camera. */
  project(v) {
    const p = v.clone().project(this.camera);
    if (p.z > 1 || p.z < -1) return null;
    return [(p.x + 1) / 2 * this.w, (1 - p.y) / 2 * this.h];
  }
  pxPerUnit(v) {
    const depth = v.clone().applyMatrix4(this.camera.matrixWorldInverse).z * -1;
    if (depth <= 0) return 0;
    return this.h / (2 * Math.tan((FOV * Math.PI) / 360) * depth);
  }
  /** Map coordinates -> screen px (used by tests and the hover tooltip). */
  screen(x, y) { return this.project(toWorld(x, y, PLANET_Y)) || [-1, -1]; }

  drawLabels() {
    const g = this.lctx, dpr = this.dpr;
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, this.w, this.h);
    const dist = this.camera.position.distanceTo(this.controls.target);
    const boost = this.sizeBoost();
    g.textAlign = "center";
    if (dist > 70 && dist < 300) {
      g.font = '700 10px "Chakra Petch", sans-serif';
      g.fillStyle = "rgba(255,255,255,.3)";
      for (const s of this.sectorLabels || []) {
        const p = this.project(s.pos);
        if (p) g.fillText(s.text, p[0], p[1]);
      }
    }
    g.font = '600 11px "Chakra Petch", sans-serif';
    g.shadowColor = "rgba(0,0,0,.9)";
    g.shadowBlur = 3;
    const small = this.w < 600 && dist > 150;
    for (const p of this.planets) {
      if (!p.parts || (p.vis ?? 1) <= 0.01) continue;
      const show = (p.front && (!small || p.ev)) || dist < 75 || p.i === this.model.dss || p.i === this.hover || p.i === this.selected;
      if (!show) continue;
      const w = p.parts.group.position;
      const at = this.project(w);
      if (!at) continue;
      const r = (p.parts.size * boost / 2) * this.pxPerUnit(w);
      const below = r * (p.drops?.length ? 2.1 : 1.35) + 11;
      g.globalAlpha = p.vis ?? 1;
      g.fillStyle = "rgba(255,255,255,.95)";
      g.fillText(p.name, at[0], at[1] + below);
    }
    g.shadowBlur = 0;
    g.globalAlpha = 1;
  }

  /** Nearest visible planet to a screen point, within a generous touch radius. */
  pick(mx, my) {
    let best = null, bd = Infinity;
    const boost = this.sizeBoost();
    for (const p of this.planets) {
      if (!p.parts || (p.vis ?? 1) < 0.5) continue;
      const w = p.parts.group.position;
      const at = this.project(w);
      if (!at) continue;
      const reach = Math.max(24, (p.parts.size * boost / 2) * this.pxPerUnit(w) * 1.4);
      const d = Math.hypot(at[0] - mx, at[1] - my);
      if (d <= reach && d < bd) { bd = d; best = p; }
    }
    return best;
  }

  bindPointer() {
    const c = this.canvas;
    const pos = (e) => {
      const r = c.getBoundingClientRect();
      return [((e.clientX - r.left) / r.width) * this.w, ((e.clientY - r.top) / r.height) * this.h];
    };
    let down = null, pointers = 0;
    c.addEventListener("pointerdown", (e) => { pointers++; down = pointers === 1 && e.button === 0 ? pos(e) : null; });
    c.addEventListener("pointermove", (e) => {
      const p = pos(e);
      if (down && Math.hypot(p[0] - down[0], p[1] - down[1]) > 7) down = null;
      if (e.buttons || e.pointerType === "touch") return;
      const hit = this.pick(p[0], p[1]);
      const idx = hit?.i ?? null;
      if (idx !== this.hover) { this.hover = idx; this.dirty = true; }
      c.style.cursor = hit ? "pointer" : "grab";
      this.opts.onHover?.(hit, p);
    });
    const up = (e) => {
      pointers = Math.max(0, pointers - 1);
      if (down && e.type === "pointerup") {
        const [x, y] = pos(e);
        const hit = this.pick(x, y);
        if (hit) { this.focus(hit.i); this.opts.onSelect?.(hit); }
      }
      down = null;
    };
    c.addEventListener("pointerup", up);
    c.addEventListener("pointercancel", up);
    c.addEventListener("pointerleave", () => { if (this.hover != null) { this.hover = null; this.dirty = true; } this.opts.onHover?.(null); });
    c.addEventListener("dblclick", (e) => {
      const [x, y] = pos(e);
      const ray = new THREE.Raycaster();
      ray.setFromCamera(new THREE.Vector2((x / this.w) * 2 - 1, -(y / this.h) * 2 + 1), this.camera);
      const hit = new THREE.Vector3();
      if (!ray.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 1, 0), 0), hit)) return;
      const off = this.camera.position.clone().sub(this.controls.target);
      off.setLength(Math.max(this.controls.minDistance, off.length() * 0.45));
      this.flyTo(hit, off);
    });
    c.addEventListener("contextmenu", (e) => e.preventDefault());
  }
}
