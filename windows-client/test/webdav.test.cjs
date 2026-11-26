const assert = require("node:assert/strict");
const http = require("node:http");
const { test } = require("node:test");
const { parseNoteFile, syncNotes } = require("../electron/webdav.cjs");

function encodedTitle(value) {
  return Buffer.from(value, "utf8").toString("base64url");
}

test("parses the Android quyang-note Markdown format", () => {
  const note = parseNoteFile(
    [
      "<!-- quyang-note",
      "version=1",
      "id=42",
      `title=${encodedTitle("会议记录")}`,
      "created-at=1700000000000",
      "updated-at=1700000002000",
      "starred=true",
      "pinned=false",
      "trashed=false",
      "-->",
      "",
      "# 会议记录",
      "",
      "## 结论",
      "",
      "- 继续推进"
    ].join("\n"),
    "",
    { fileName: "会议记录.md", updatedAt: 1700000001000 }
  );

  assert.equal(note.id, "42");
  assert.equal(note.title, "会议记录");
  assert.equal(note.bodyMarkdown, "## 结论\n\n- 继续推进");
  assert.equal(note.updatedAt, 1700000002000);
  assert.equal(note.starred, true);
  assert.equal(note.remoteFileName, "会议记录.md");
});

test("imports ordinary Markdown without proprietary metadata", () => {
  const note = parseNoteFile(
    "# 旧笔记\n\n第一段\n\n- 第二段",
    "",
    { fileName: "旧笔记.md", updatedAt: 1700000003000 }
  );

  assert.equal(note.title, "旧笔记");
  assert.equal(note.bodyMarkdown, "第一段\n\n- 第二段");
  assert.equal(note.updatedAt, 1700000003000);
  assert.match(note.id, /^remote-[0-9a-f]{32}$/);
});

test("sync imports Markdown files from the WebDAV root and subdirectories", async () => {
  const server = http.createServer((request, response) => {
    if (request.method === "MKCOL") {
      response.writeHead(405).end();
      return;
    }
    if (request.method === "PROPFIND" && request.url === "/notes/") {
      response.writeHead(207, { "Content-Type": "application/xml; charset=utf-8" }).end(`
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/notes/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/notes/note-win.md</d:href>
            <d:propstat><d:prop>
              <d:resourcetype/>
              <d:getlastmodified>Wed, 22 Nov 2023 10:00:00 GMT</d:getlastmodified>
            </d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/notes/%E6%97%A7%E7%AC%94%E8%AE%B0.md</d:href>
            <d:propstat><d:prop>
              <d:resourcetype/>
              <d:getlastmodified>Wed, 22 Nov 2023 10:01:00 GMT</d:getlastmodified>
            </d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/notes/%E5%B7%A5%E4%BD%9C/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
          </d:response>
        </d:multistatus>
      `);
      return;
    }
    if (request.method === "PROPFIND" && request.url === "/notes/%E5%B7%A5%E4%BD%9C/") {
      response.writeHead(207, { "Content-Type": "application/xml; charset=utf-8" }).end(`
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/notes/%E5%B7%A5%E4%BD%9C/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/notes/%E5%B7%A5%E4%BD%9C/%E6%97%A5%E5%B8%B8.md</d:href>
            <d:propstat><d:prop>
              <d:resourcetype/>
              <d:getlastmodified>Wed, 22 Nov 2023 10:02:00 GMT</d:getlastmodified>
            </d:prop></d:propstat>
          </d:response>
        </d:multistatus>
      `);
      return;
    }
    if (request.method === "GET" && request.url === "/notes/old.md") {
      response.writeHead(200, { "Content-Type": "text/markdown" }).end("# 旧格式\n\n同步正文");
      return;
    }
    if (request.method === "GET" && request.url === "/notes/note-win.md") {
      response.writeHead(200, { "Content-Type": "text/markdown" }).end([
        "---",
        "id: win-1",
        'title: "Windows 笔记"',
        "createdAt: 1700000000000",
        "updatedAt: 1700000001000",
        "starred: false",
        "pinned: false",
        "trashed: false",
        "folderId: null",
        `bodyMarkdownBase64: ${Buffer.from("Markdown 正文", "utf8").toString("base64")}`,
        "---",
        "",
        "Markdown 正文"
      ].join("\n"));
      return;
    }
    if (request.method === "GET" && request.url === "/notes/%E6%97%A7%E7%AC%94%E8%AE%B0.md") {
      response.writeHead(200, { "Content-Type": "text/markdown" }).end("# 旧笔记\n\n第一段");
      return;
    }
    if (request.method === "GET" && request.url === "/notes/%E5%B7%A5%E4%BD%9C/%E6%97%A5%E5%B8%B8.md") {
      response.writeHead(200, { "Content-Type": "text/markdown" }).end("# 日常\n\n子目录内容");
      return;
    }
    response.writeHead(404).end();
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const { port } = server.address();
    const result = await syncNotes(
      { fileUrl: `http://127.0.0.1:${port}/notes/`, username: "u", password: "p" },
      []
    );
    assert.equal(result.downloaded, 3);
    assert.deepEqual(
      result.notes.map((note) => note.title).sort(),
      ["Windows 笔记", "旧笔记", "日常"].sort()
    );
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
