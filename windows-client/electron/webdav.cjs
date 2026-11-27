const crypto = require("node:crypto");

const USER_AGENT = "QuyangNotes-Windows/0.11.29";
const SYNC_INTERVAL_MILLIS = 30_000;
const MAX_REMOTE_DIRECTORIES = 100;
const MAX_REMOTE_FILES = 5_000;

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
  return fetch(targetUrl, {
    ...options,
    headers: {
      Authorization: authHeader(config),
      "User-Agent": USER_AGENT,
      ...(options.headers || {})
    },
    signal: AbortSignal.timeout(30_000)
  });
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

function unescapeXml(value) {
  return String(value)
    .replaceAll("&amp;", "&")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", "\"")
    .replaceAll("&apos;", "'");
}

function buildFrontmatter(note) {
  const markdown = note.bodyMarkdown || note.bodyText || "";
  const encodedMarkdown = Buffer.from(markdown, "utf8").toString("base64");
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
    `bodyMarkdownBase64: ${encodedMarkdown}`,
    "---",
    "",
    markdown
  ].join("\n");
}

function unquote(value) {
  if (value === "null" || value == null) return null;
  try {
    return JSON.parse(value);
  } catch {
    return String(value).replace(/^["']|["']$/g, "");
  }
}

function decodeBase64Url(value) {
  try {
    return Buffer.from(String(value || ""), "base64url").toString("utf8");
  } catch {
    return "";
  }
}

function metadataLines(source) {
  return new Map(
    String(source || "")
      .split(/\r?\n/)
      .map((line) => {
        const separator = line.indexOf("=");
        if (separator <= 0) return null;
        return [line.slice(0, separator), line.slice(separator + 1)];
      })
      .filter(Boolean)
  );
}

function frontmatterValues(source) {
  return new Map(
    String(source || "")
      .split(/\r?\n/)
      .map((line) => {
        const separator = line.indexOf(": ");
        if (separator <= 0) return null;
        return [line.slice(0, separator), line.slice(separator + 2)];
      })
      .filter(Boolean)
  );
}

function stableRemoteId(fileName) {
  const hash = crypto.createHash("sha256")
    .update(String(fileName || "markdown"))
    .digest("hex")
    .slice(0, 32);
  return `remote-${hash}`;
}

function fileNameTitle(fileName) {
  const name = String(fileName || "").split("/").pop() || "";
  return name.replace(/\.(?:md|markdown|txt)$/i, "").trim() || "Markdown 记事";
}

function stripLeadingHeading(markdown, title) {
  const normalized = String(markdown || "").replace(/^\n+/, "");
  const match = /^#\s+(.+?)(?:\n+|$)/.exec(normalized);
  if (!match) return normalized.trim();
  if (title && match[1].trim() !== title.trim()) return normalized.trim();
  return normalized.slice(match[0].length).trim();
}

function markdownToPlainText(markdown) {
  return String(markdown || "")
    .replace(/!\[([^\]]*)]\([^)]+\)/g, "$1")
    .replace(/<color:#[0-9A-Fa-f]{6}>|<\/color>/g, "")
    .replace(/<center>|<\/center>/g, "")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/^>\s?/gm, "")
    .replace(/^[-*]\s+/gm, "")
    .replace(/^\d+\.\s+/gm, "")
    .replace(/\*\*|__|`/g, "")
    .trim();
}

function parseNoteFile(content, fallbackId, options = {}) {
  const source = String(content || "").replace(/^\uFEFF/, "").replaceAll("\r\n", "\n");
  const fileName = options.fileName || "";
  const fileUpdatedAt = Number(options.updatedAt) || Date.now();
  const androidHeader = /^<!--\s*quyang-note\s*\n([\s\S]*?)\n-->\n?/i.exec(source);
  const yamlHeader = /^---\n([\s\S]*?)\n---(?:\n|$)/.exec(source);

  if (androidHeader) {
    const values = metadataLines(androidHeader[1]);
    const title = decodeBase64Url(values.get("title")).trim() || fileNameTitle(fileName);
    const plainBody = source.slice(androidHeader[0].length);
    const bodyMarkdown = stripLeadingHeading(plainBody, title);
    return {
      id: String(values.get("id") || fallbackId || stableRemoteId(fileName)),
      title,
      bodyMarkdown,
      bodyText: markdownToPlainText(bodyMarkdown),
      createdAt: Number(values.get("created-at")) || fileUpdatedAt,
      updatedAt: Number(values.get("updated-at")) || fileUpdatedAt,
      starred: values.get("starred") === "true",
      pinned: values.get("pinned") === "true",
      trashed: values.get("trashed") === "true",
      folderId: values.get("folder-id") || null,
      attachments: [],
      remoteFileName: fileName
    };
  }

  if (yamlHeader) {
    const values = frontmatterValues(yamlHeader[1]);
    const encodedBody = values.get("bodyMarkdownBase64") || "";
    const legacyBody = values.get("bodyHtmlBase64")
      ? Buffer.from(values.get("bodyHtmlBase64"), "base64").toString("utf8")
      : "";
    const plainBody = source.slice(yamlHeader[0].length);
    const decodedBody = encodedBody
      ? Buffer.from(encodedBody, "base64").toString("utf8")
      : plainBody;
    const title = String(unquote(values.get("title") || "\"\"")).trim() ||
      fileNameTitle(fileName);
    const bodyMarkdown = decodedBody
      ? (encodedBody ? decodedBody.trim() : stripLeadingHeading(decodedBody, title))
      : legacyBody;
    return {
      id: String(unquote(values.get("id") || JSON.stringify(fallbackId || stableRemoteId(fileName)))),
      title,
      bodyMarkdown,
      ...(legacyBody && !encodedBody ? { bodyHtml: legacyBody } : {}),
      bodyText: markdownToPlainText(bodyMarkdown),
      createdAt: Number(values.get("createdAt")) || fileUpdatedAt,
      updatedAt: Number(values.get("updatedAt")) || fileUpdatedAt,
      starred: values.get("starred") === "true",
      pinned: values.get("pinned") === "true",
      trashed: values.get("trashed") === "true",
      folderId: unquote(values.get("folderId") || "null"),
      attachments: [],
      remoteFileName: fileName
    };
  }

  const heading = /^#\s+(.+?)(?:\n+|$)/.exec(source);
  const title = heading?.[1]?.trim() || fileNameTitle(fileName);
  const bodyMarkdown = heading
    ? source.slice(heading[0].length).trim()
    : source.trim();
  return {
    id: String(fallbackId || stableRemoteId(fileName)),
    title,
    bodyMarkdown,
    bodyText: markdownToPlainText(bodyMarkdown),
    createdAt: fileUpdatedAt,
    updatedAt: fileUpdatedAt,
    starred: false,
    pinned: false,
    trashed: false,
    folderId: null,
    attachments: [],
    remoteFileName: fileName
  };
}

function propertyValue(block, name) {
  const pattern = new RegExp(
    `<(?:[A-Za-z0-9_-]+:)?${name}[^>]*>([\\s\\S]*?)<\\/(?:[A-Za-z0-9_-]+:)?${name}>`,
    "i"
  );
  return unescapeXml(pattern.exec(block)?.[1] || "").trim();
}

function relativeRemotePath(rootUrl, targetUrl) {
  if (targetUrl.origin !== rootUrl.origin) return null;
  try {
    const rootPath = decodeURIComponent(rootUrl.pathname).replace(/\/+$/, "") + "/";
    const targetPath = decodeURIComponent(targetUrl.pathname);
    if (!targetPath.startsWith(rootPath)) return null;
    return targetPath.slice(rootPath.length).replace(/\/+$/, "");
  } catch {
    return null;
  }
}

async function propfindDirectory(config, directoryUrl) {
  const body = `<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getcontenttype/>
    <d:getlastmodified/>
    <d:getetag/>
  </d:prop>
</d:propfind>`;
  let response = await request(config, directoryUrl, {
    method: "PROPFIND",
    headers: {
      Depth: "1",
      "Content-Type": "application/xml; charset=utf-8"
    },
    body
  });
  if ([400, 415, 501].includes(response.status)) {
    response = await request(config, directoryUrl, {
      method: "PROPFIND",
      headers: { Depth: "1" }
    });
  }
  return response;
}

async function listRemoteFiles(config) {
  const queue = [config.rootUrl];
  const visitedDirectories = new Set();
  const filesByPath = new Map();

  while (queue.length > 0 && visitedDirectories.size < MAX_REMOTE_DIRECTORIES) {
    const directoryUrl = queue.shift();
    const directoryKey = directoryUrl.toString();
    if (visitedDirectories.has(directoryKey)) continue;
    visitedDirectories.add(directoryKey);

    const response = await propfindDirectory(config, directoryUrl);
    if (response.status === 404) continue;
    await ensureText(response, "读取 WebDAV 目录");
    const xml = await response.text();
    const responseBlocks = [...xml.matchAll(
      /<(?:[A-Za-z0-9_-]+:)?response[^>]*>([\s\S]*?)<\/(?:[A-Za-z0-9_-]+:)?response>/gi
    )];

    for (const match of responseBlocks) {
      const block = match[1];
      const hrefMatch = /<(?:[A-Za-z0-9_-]+:)?href[^>]*>([\s\S]*?)<\/(?:[A-Za-z0-9_-]+:)?href>/i
        .exec(block);
      if (!hrefMatch) continue;
      const href = unescapeXml(hrefMatch[1]).trim();
      let targetUrl;
      try {
        targetUrl = new URL(href, directoryUrl);
      } catch {
        continue;
      }
      const relativePath = relativeRemotePath(config.rootUrl, targetUrl);
      if (relativePath == null) continue;
      const isDirectory = targetUrl.pathname.endsWith("/") ||
        /<(?:[A-Za-z0-9_-]+:)?collection(?:\s[^>]*)?>/i.test(block);
      if (isDirectory) {
        if (relativePath && targetUrl.toString() !== directoryUrl.toString()) {
          queue.push(targetUrl);
        }
        continue;
      }
      const contentType = propertyValue(block, "getcontenttype").split(";")[0].toLowerCase();
      const isTextNote = /\.(?:md|markdown|txt)$/i.test(relativePath) ||
        contentType === "text/markdown" ||
        contentType === "text/plain";
      if (!isTextNote || filesByPath.size >= MAX_REMOTE_FILES) continue;
      filesByPath.set(relativePath, {
        fileName: relativePath,
        url: targetUrl.toString(),
        updatedAt: Date.parse(propertyValue(block, "getlastmodified")) || 0,
        fingerprint: [
          propertyValue(block, "getetag"),
          propertyValue(block, "getlastmodified"),
          propertyValue(block, "getcontentlength")
        ].filter(Boolean).join("|") || relativePath
      });
    }
  }

  return [...filesByPath.values()];
}

async function downloadRemoteNotes(config) {
  const files = await listRemoteFiles(config);
  const notes = [];
  for (const file of files) {
    const response = await request(config, file.url, { method: "GET" });
    if (!response.ok) continue;
    const fallbackId = /^note-([^/]+)\.md$/i.exec(file.fileName)?.[1]
      ?.replace(/%2F/gi, "_");
    const note = parseNoteFile(await response.text(), fallbackId, {
      fileName: file.fileName,
      updatedAt: file.updatedAt
    });
    if (note) notes.push(note);
  }
  return notes;
}

function encodeRelativeRemotePath(value) {
  return String(value || "")
    .split("/")
    .filter((segment) => segment && segment !== "." && segment !== "..")
    .map(encodeURIComponent)
    .join("/");
}

function defaultNoteFileName(noteId) {
  const safeId = String(noteId || crypto.randomUUID())
    .replace(/[\\/:*?"<>|\u0000-\u001F]/g, "_");
  return `note-${safeId}.md`;
}

async function uploadNote(config, note, preferredFileName) {
  const relativeFileName = preferredFileName || defaultNoteFileName(note.id);
  const target = new URL(encodeRelativeRemotePath(relativeFileName), config.rootUrl);
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
  if (!response.ok && ![405, 501].includes(response.status)) {
    await ensureText(response, "WebDAV 连接测试");
  }
  await ensureDirectory(config);
  const files = await listRemoteFiles(config);
  return {
    ok: true,
    rootUrl: config.rootUrl.toString(),
    remoteFiles: files.length
  };
}

async function syncNotes(input, localNotes) {
  const config = normalizeConfig(input);
  await ensureDirectory(config);
  const remoteNotes = await downloadRemoteNotes(config);
  const remoteById = new Map(remoteNotes.map((note) => [String(note.id), note]));
  const mergedById = new Map();

  for (const note of localNotes || []) {
    const remote = remoteById.get(String(note.id));
    if (!remote || Number(note.updatedAt) > Number(remote.updatedAt)) {
      mergedById.set(String(note.id), note);
    } else {
      mergedById.set(String(note.id), remote);
    }
  }
  for (const remote of remoteNotes) {
    if (!mergedById.has(String(remote.id))) mergedById.set(String(remote.id), remote);
  }

  const merged = [...mergedById.values()];
  let uploaded = 0;
  for (const note of merged) {
    const remote = remoteById.get(String(note.id));
    if (!remote || Number(note.updatedAt) > Number(remote.updatedAt)) {
      await uploadNote(config, note, note.remoteFileName || remote?.remoteFileName);
      uploaded++;
    }
  }

  return {
    notes: merged,
    uploaded,
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
