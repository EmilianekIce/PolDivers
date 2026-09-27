// helldivers.wiki.gg, fetched only on demand (a tab opened, an article clicked) -- never crawled.
// Campaigns come from the {{Campaign}} / {{Campaign MO}} templates of the "Campaigns" page,
// parsed exactly like the Android app does (WikiCampaigns.kt).

const WIKI = "https://helldivers.wiki.gg";
const api = (params) => fetch(`${WIKI}/api.php?${new URLSearchParams({ format: "json", formatversion: "2", origin: "*", ...params })}`).then((r) => {
  if (!r.ok) throw new Error("wiki HTTP " + r.status);
  return r.json();
});

export const pageUrl = (title) => `${WIKI}/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`;
export const fileUrl = (name, width = 1280) => `${WIKI}/wiki/Special:FilePath/${encodeURIComponent(name.replace(/ /g, "_"))}?width=${width}`;

// ---------- wikitext ----------
function matchingClose(text, start) {
  let depth = 0;
  for (let i = start; i < text.length - 1;) {
    if (text.startsWith("{{", i)) { depth++; i += 2; } else if (text.startsWith("}}", i)) { depth--; if (depth === 0) return i; i += 2; } else i++;
  }
  return -1;
}
function splitTopLevel(body) {
  const parts = [];
  let braces = 0, brackets = 0, last = 0;
  for (let i = 0; i < body.length; i++) {
    if (body.startsWith("{{", i)) { braces++; i++; continue; }
    if (body.startsWith("}}", i)) { braces--; i++; continue; }
    if (body.startsWith("[[", i)) { brackets++; i++; continue; }
    if (body.startsWith("]]", i)) { brackets--; i++; continue; }
    if (body[i] === "|" && !braces && !brackets) { parts.push(body.slice(last, i)); last = i + 1; }
  }
  parts.push(body.slice(last));
  return parts;
}
export function topLevelTemplates(text) {
  const out = [];
  let i = 0;
  for (;;) {
    const start = text.indexOf("{{", i);
    if (start < 0) break;
    const end = matchingClose(text, start);
    if (end < 0) break;
    const parts = splitTopLevel(text.slice(start + 2, end));
    const params = {};
    parts.slice(1).forEach((p) => { const eq = p.indexOf("="); if (eq > 0) params[p.slice(0, eq).trim().toLowerCase()] = p.slice(eq + 1).trim(); });
    out.push([parts[0].trim(), params]);
    i = end + 2;
  }
  return out;
}
/** Wikitext -> text with <i=1>highlight</i> markup (the game's own dispatch markup). */
export function toGameMarkup(text) {
  let s = text || "";
  s = s.replace(/<ref[^>]*\/>/gs, "").replace(/<ref[^>]*>.*?<\/ref>/gs, "").replace(/<br\s*\/?>/gi, "\n");
  s = s.replace(/\[\[(?:File|Image|Plik):[^\]]*\]\]/gi, "");
  for (let k = 0; k < 3; k++) {
    s = s.replace(/\{\{[^{}|]*\|([^{}]*?)\}\}/g, (_, g) => g.split("|").pop());
    s = s.replace(/\{\{[^{}]*\}\}/g, "");
  }
  s = s.replace(/\[\[[^\]|]*\|([^\]]*)\]\]/g, "<i=1>$1</i>").replace(/\[\[([^\]]*)\]\]/g, "<i=1>$1</i>");
  s = s.replace(/\[https?:\/\/\S+ ([^\]]*)\]/g, "$1").replace(/'''(.*?)'''/g, "<i=1>$1</i>").replace(/''/g, "");
  s = s.replace(/<(?!\/?i(=\d)?>)[^>]+>/g, "").replace(/(<i=1>)+/g, "<i=1>").replace(/(<\/i>)+/g, "</i>");
  return s.split("\n").map((l) => l.trim()).join("\n").replace(/\n{3,}/g, "\n\n").trim();
}
export const cleanWikitext = (t) => toGameMarkup(t).replace(/<\/?i(=\d)?>/g, "");

const OUTCOME = (mo) => {
  const o = (mo.outcome || "").trim().toLowerCase();
  if (["success", "successful", "victory"].includes(o)) return "success";
  if (["failure", "failed", "fail", "defeat"].includes(o)) return "failure";
  if (["in-progress", "in-proggress", "in progress", "ongoing", "active"].includes(o)) return "progress";
  return mo.date_end ? "unknown" : "progress";
};
const FACTION = { terminid: "Terminids", terminids: "Terminids", automaton: "Automaton", automatons: "Automaton", illuminate: "Illuminate", human: "Humans", humans: "Humans", "super earth": "Humans" };

export function parseCampaigns(wikitext) {
  const seen = new Set();
  return topLevelTemplates(wikitext)
    .filter(([n]) => n.toLowerCase() === "campaign")
    .map(([, p]) => {
      const name = cleanWikitext(p.name || "").trim();
      if (!name || seen.has(name.toLowerCase())) return null;
      seen.add(name.toLowerCase());
      const phases = [];
      for (let n = 1; n <= 6; n++) {
        const raw = p[`${n}_mo`];
        const mo = raw && topLevelTemplates(raw).find(([t]) => t.toLowerCase() === "campaign mo")?.[1];
        if (!mo) continue;
        phases.push({
          number: n, name: cleanWikitext(mo.name || ""), outcome: OUTCOME(mo), image: (mo.image || "").trim() || null,
          dateStart: cleanWikitext(mo.date_start || ""), dateEnd: cleanWikitext(mo.date_end || ""),
          briefing: toGameMarkup(mo.briefing), debrief: toGameMarkup(mo.debrief), objective: toGameMarkup(mo.objective),
          rewardType: (mo.reward_type || "").trim().toLowerCase(), rewardAmount: parseInt(mo.reward_amount, 10) || 0,
        });
      }
      return {
        name, description: toGameMarkup(p.description), banner: (p.banner_image || "").trim() || null,
        rewardType: (p.reward_type || "").trim().toLowerCase(), rewardAmount: parseInt(p.reward_amount, 10) || 0,
        rewardText: cleanWikitext(p.reward_text || ""), faction: FACTION[(p.faction || "").trim().toLowerCase()] || "", phases,
        get active() { return this.phases.some((ph) => ph.outcome === "progress"); },
      };
    })
    .filter(Boolean);
}

let campaignsPromise;
export function getCampaigns() {
  campaignsPromise ??= api({ action: "parse", prop: "wikitext", redirects: "1", page: "Campaigns" })
    .then((j) => parseCampaigns(j.parse?.wikitext || "").reverse())
    .catch((e) => { campaignsPromise = null; throw e; });
  return campaignsPromise;
}

// ---------- articles / search ----------
const articles = new Map();
export async function getArticle(title) {
  if (articles.has(title)) return articles.get(title);
  const j = await api({ action: "parse", prop: "text|displaytitle", redirects: "1", disablelimitreport: "1", disableeditsection: "1", disabletoc: "1", page: title });
  if (!j.parse) throw new Error("Brak strony: " + title);
  const a = { title: j.parse.title || title, html: j.parse.text };
  articles.set(title, a);
  return a;
}
export async function search(query) {
  if (!query.trim()) return [];
  const j = await api({ action: "query", list: "search", srlimit: "24", srnamespace: "0", srsearch: query });
  const results = j.query?.search || [];
  if (!results.length) return results;
  try {
    const t = await api({ action: "query", prop: "pageimages", piprop: "thumbnail", pithumbsize: "200", pilimit: "50", redirects: "1", titles: results.map((r) => r.title).join("|") });
    const thumbs = Object.fromEntries((t.query?.pages || []).filter((p) => p.thumbnail).map((p) => [p.title, p.thumbnail.source]));
    results.forEach((r) => (r.thumbnail = thumbs[r.title]));
  } catch { /* thumbnails are optional */ }
  return results;
}

export const ARTICLE_CSS = `
  :root { color-scheme: dark; }
  body { background:#0B0D10; color:#E8EAED; font-family:"Chakra Petch",system-ui,sans-serif; font-size:16px; line-height:1.6; margin:0; padding:8px 4px 40px; }
  a { color:#FFC400; text-decoration:none; } a:hover { text-decoration:underline; }
  h1,h2,h3,h4 { color:#FFC400; line-height:1.25; margin:22px 0 8px; font-family:"Russo One",sans-serif; font-weight:400; }
  h2 { font-size:21px; border-bottom:1px solid #2A2E35; padding-bottom:4px; text-transform:uppercase; }
  img { max-width:100%; height:auto; vertical-align:middle; cursor:zoom-in; }
  img[src*="Stratagem_Arrow"], img[alt*="Arrow"] { width:1.5em !important; height:1.5em !important; cursor:default; }
  .mw-parser-output > * { max-width: 100%; }
  figure, .thumb { margin:10px 0; } figcaption, .thumbcaption { font-size:13px; color:#9AA0A8; }
  table { border-collapse:collapse; font-size:14px; margin:10px 0; display:block; overflow-x:auto; max-width:100%; }
  th, td { border:1px solid #2A2E35; padding:5px 8px; vertical-align:top; } th { background:#14171C; color:#FFC400; }
  .infobox, aside.portable-infobox, .portable-infobox { float:right; max-width:340px; margin:0 0 14px 18px; background:#14171C; border:1px solid #2A2E35; }
  @media (max-width: 700px) { .infobox, .portable-infobox { float:none; max-width:100%; margin:0 0 14px; } }
  .navbox, .mw-editsection, .noprint, #toc, .toc, .mw-empty-elt, .catlinks, .printfooter, .ambox, .metadata, .mw-jump-link, .sidebar { display:none !important; }
  .gallery { display:flex; flex-wrap:wrap; gap:8px; padding:0; list-style:none; }
  audio, video { width:100%; } pre, code { white-space:pre-wrap; background:#14171C; }
  .src { margin-top:28px; padding-top:10px; border-top:1px solid #2A2E35; font-size:13px; color:#9AA0A8; }
`;
