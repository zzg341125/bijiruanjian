const USER_AGENT = "QuyangNotes-Windows/0.11.24";
const SYNC_INTERVAL_MILLIS = 30_000;

function authHeader(config) {
  const pair = `${config.username || ""}:${config.password || ""}`;
  return `Basic ${Buffer.from(pair, "utf8").toString("base64")}`;
}

function normalizeConfig(config) {
  if (!config?.fileUrl?.trim()) throw new Error("请先填写 WebDAV 地址");
  const url = new URL(config.fileUrl.trim());
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("WebDAV 地址必须以 http:// 或 https:// 开头");
  }
  if (!url.pathname.endsWith("/")) url.pathname += "/";
  const path = url.pathname.replace(/\/+$/, "");
  if (path === "/dav") {
    url.pathname = "/dav/曲阳便签/";
  }
  return {
    rootUrl: url,
    username: config.username || "",
    password: config.password || ""
  };
}

async function request(config, targetUrl, options = {}) {
  const response = await fetch(targetUrl, {
    ...options,
    headers: {
      Authorization: authHeader(config),
      "User-Agent": USER_AGENT,
      ...(options.headers || {})
    },
    signal: AbortSignal.timeout(30_000)
  });
  return response;
}

async function ensureText(response, action) {
  if (!response.ok && response.status !== 207) {
    const details = (await response.text().catch(() => "")).slice(0, 300);
    throw new Error(`${action}失败：HTTP ${response.status}${details ? ` · ${details}` : ""}`);
  }
  return response;
}

async function ensureDirectory(config) {
  const response = await request(config, config.rootUrl, {
    method: "MKCOL"
  });
  if ([201, 204, 301, 302, 405, 409].includes(response.status)) return;
  await ensureText(response, "创建 WebDAV 目录");
}

function escapeXml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function buildFrontmatter(note) {
  const html = Buffer.from(note.bodyHtml || "", "utf8").toString("base64");
  return [
    "---",
    `id: ${note.id}`,
    `title: ${JSON.stringify(note.title || "")}`,
    `createdAt: ${Number(note.createdAt) || Date.now()}`,
    `updatedAt: ${Number(note.updatedAt) || Date.now()}`,
    `starred: ${Boolean(note.starred)}`,
    `pinned: ${Boolean(note.pinned)}`,
    `trashed: ${Boolean(note.trashed)}`,
    `folderId: ${note.folderId ? JSON.stringify(note.folderId) : "null"}`,
    `bodyHtmlBase64: ${html}`,
    "---",
    "",
    `# ${String(note.title || "无标题").replaceAll("\n", " ")}`,
    "",
    note.bodyText || ""
  ].join("\n");
}

function unquote(value) {
  if (value === "null") return null;
  try {
    return JSON.parse(value);
  } catch {
    return value.replace(/^["']|["']$/g, "");
  }
}

function parseNoteFile(content, fallbackId) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---/.exec(content);
  if (!match) return null;
  const values = new Map();
  match[1].split(/\r?\n/).forEach((line) => {
    const separator = line.indexOf(": ");
    if (separator > 0) {
      values.set(line.slice(0, separator), line.slice(separator + 2));
    }
  });
  try {
    const html = Buffer.from(values.get("bodyHtmlBase64") || "", "base64").toString("utf8");
    return {
      id: unquote(values.get("id") || JSON.stringify(fallbackId)),
      title: String(unquote(values.get("title") || "\"\"")),
      bodyHtml: html,
      bodyText: content.slice(match[0].length).replace(/^\s*#.*(?:\r?\n)+/, "").trim(),
      createdAt: Number(values.get("createdAt")) || Date.now(),
      updatedAt: Number(values.get("updatedAt")) || Date.now(),
      starred: values.get("starred") === "true",
      pinned: values.get("pinned") === "true",
      trashed: values.get("trashed") === "true",
      folderId: unquote(values.get("folderId") || "null"),
      attachments: []
    };
  } catch {
    return null;
  }
}

async function listRemoteFiles(config) {
  const body = `<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop><d:displayname/><d:getcontentlength/><d:getlastmodified/></d:prop>
</d:propfind>`;
  const response = await request(config, config.rootUrl, {
    method: "PROPFIND",
    headers: {
      Depth: "1",
      "Content-Type": "application/xml; charset=utf-8"
    },
    body
  });
  await ensureText(response, "读取 WebDAV 目录");
  const xml = await response.text();
  const hrefs = [...xml.matchAll(/<[^:>]*:?href[^>]*>([^<]+)<\/[^:>]*:?href>/gi)]
    .map((match) => match[1].trim())
    .filter((href) => /\/note-[^/]+\.md$/i.test(decodeURIComponent(href)));
  return [...new Set(hrefs.map((href) => ({
    href,
    url: new URL(href, config.rootUrl).toString(),
    fileName: decodeURIComponent(href.split("/").pop())
  })))];
}

async function downloadRemoteNotes(config) {
  const files = await listRemoteFiles(config);
  const notes = [];
  for (const file of files) {
    const response = await request(config, file.url, { method: "GET" });
    if (!response.ok) continue;
    const fallbackId = file.fileName.replace(/^note-/, "").replace(/\.md$/i, "");
    const note = parseNoteFile(await response.text(), fallbackId);
    if (note) notes.push(note);
  }
  return notes;
}

async function uploadNote(config, note) {
  const fileName = `note-${encodeURIComponent(note.id)}.md`;
  const target = new URL(fileName, config.rootUrl);
  const response = await request(config, target, {
    method: "PUT",
    headers: { "Content-Type": "text/markdown; charset=utf-8" },
    body: buildFrontmatter(note)
  });
  await ensureText(response, `上传便签“${note.title || "无标题"}”`);
}

async function testConnection(input) {
  const config = normalizeConfig(input);
  const response = await request(config, config.rootUrl, { method: "OPTIONS" });
  await ensureText(response, "WebDAV 连接测试");
  return { ok: true, rootUrl: config.rootUrl.toString() };
}

async function syncNotes(input, localNotes) {
  const config = normalizeConfig(input);
  await ensureDirectory(config);
  const remoteNotes = await downloadRemoteNotes(config);
  const remoteById = new Map(remoteNotes.map((note) => [String(note.id), note]));
  const mergedById = new Map();

  for (const note of localNotes || []) {
    const remote = remoteById.get(String(note.id));
    mergedById.set(String(note.id), !remote || note.updatedAt >= remote.updatedAt ? note : remote);
  }
  for (const remote of remoteNotes) {
    if (!mergedById.has(String(remote.id))) mergedById.set(String(remote.id), remote);
  }

  const merged = [...mergedById.values()];
  for (const note of merged) {
    const remote = remoteById.get(String(note.id));
    if (!remote || note.updatedAt > remote.updatedAt) await uploadNote(config, note);
  }

  return {
    notes: merged,
    uploaded: merged.filter((note) => {
      const remote = remoteById.get(String(note.id));
      return !remote || note.updatedAt > remote.updatedAt;
    }).length,
    downloaded: remoteNotes.length,
    rootUrl: config.rootUrl.toString(),
    syncedAt: Date.now()
  };
}

async function downloadNotes(input) {
  const config = normalizeConfig(input);
  await ensureDirectory(config);
  return {
    notes: await downloadRemoteNotes(config),
    rootUrl: config.rootUrl.toString(),
    syncedAt: Date.now()
  };
}

module.exports = {
  SYNC_INTERVAL_MILLIS,
  testConnection,
  syncNotes,
  downloadNotes,
  buildFrontmatter,
  parseNoteFile
};
