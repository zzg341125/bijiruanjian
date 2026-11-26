const COLORS = [
  ["墨黑", "#4d4a46"],
  ["砖红", "#c34c4c"],
  ["暖橙", "#c47b28"],
  ["金色", "#9a7a20"],
  ["松绿", "#3e7456"],
  ["靛蓝", "#3f65a8"],
  ["紫藤", "#7a5798"]
];

const state = {
  data: {
    notes: [],
    folders: [],
    settings: { theme: "system", fontScale: 1 }
  },
  view: "all",
  folderId: null,
  query: "",
  selectedId: null,
  sortDescending: true,
  saveTimer: null,
  savedEditorRange: null,
  toastTimer: null,
  webDavTimer: null,
  updateStartupTimer: null,
  updateTimer: null,
  syncing: false
};

const elements = {
  searchInput: document.querySelector("#searchInput"),
  newNoteButton: document.querySelector("#newNoteButton"),
  settingsButton: document.querySelector("#settingsButton"),
  addFolderButton: document.querySelector("#addFolderButton"),
  folderList: document.querySelector("#folderList"),
  noteList: document.querySelector("#noteList"),
  emptyList: document.querySelector("#emptyList"),
  listTitle: document.querySelector("#listTitle"),
  listSubtitle: document.querySelector("#listSubtitle"),
  allCount: document.querySelector("#allCount"),
  starredCount: document.querySelector("#starredCount"),
  trashCount: document.querySelector("#trashCount"),
  editorEmpty: document.querySelector("#editorEmpty"),
  editorContent: document.querySelector("#editorContent"),
  noteTitle: document.querySelector("#noteTitle"),
  noteEditor: document.querySelector("#noteEditor"),
  saveState: document.querySelector("#saveState"),
  starButton: document.querySelector("#starButton"),
  pinButton: document.querySelector("#pinButton"),
  copyButton: document.querySelector("#copyButton"),
  exportButton: document.querySelector("#exportButton"),
  deleteButton: document.querySelector("#deleteButton"),
  folderSelect: document.querySelector("#folderSelect"),
  trashBanner: document.querySelector("#trashBanner"),
  restoreButton: document.querySelector("#restoreButton"),
  permanentDeleteButton: document.querySelector("#permanentDeleteButton"),
  imageButton: document.querySelector("#imageButton"),
  colorButton: document.querySelector("#colorButton"),
  colorMenu: document.querySelector("#colorMenu"),
  settingsDialog: document.querySelector("#settingsDialog"),
  themeSelect: document.querySelector("#themeSelect"),
  fontScaleInput: document.querySelector("#fontScaleInput"),
  fontScaleLabel: document.querySelector("#fontScaleLabel"),
  webDavUrl: document.querySelector("#webDavUrl"),
  webDavUsername: document.querySelector("#webDavUsername"),
  webDavPassword: document.querySelector("#webDavPassword"),
  webDavAutoSync: document.querySelector("#webDavAutoSync"),
  webDavTestButton: document.querySelector("#webDavTestButton"),
  webDavSyncButton: document.querySelector("#webDavSyncButton"),
  webDavStatus: document.querySelector("#webDavStatus"),
  autoUpdateEnabled: document.querySelector("#autoUpdateEnabled"),
  updateCheckButton: document.querySelector("#updateCheckButton"),
  updateInstallButton: document.querySelector("#updateInstallButton"),
  updateStatus: document.querySelector("#updateStatus"),
  appVersionLabel: document.querySelector("#appVersionLabel"),
  exportDataButton: document.querySelector("#exportDataButton"),
  importDataButton: document.querySelector("#importDataButton"),
  toast: document.querySelector("#toast")
};

function uid() {
  return crypto.randomUUID();
}

function now() {
  return Date.now();
}

function currentNote() {
  return state.data.notes.find((note) => note.id === state.selectedId) || null;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeMarkdown(value) {
  return String(value ?? "").replace(/([\\`*_[\]()#])/g, "\\$1");
}

function safeImageUrl(value) {
  try {
    const parsed = new URL(value);
    return ["file:", "data:", "http:", "https:"].includes(parsed.protocol) ? parsed.href : "";
  } catch {
    return "";
  }
}

function cssColorToHex(value) {
  const text = String(value || "").trim();
  if (/^#[0-9A-Fa-f]{6}$/.test(text)) return text.toLowerCase();
  const match = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i.exec(text);
  if (!match) return "";
  const hex = match.slice(1, 4)
    .map((part) => Number(part).toString(16).padStart(2, "0"))
    .join("");
  return `#${hex}`;
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
    .replace(/\\([\\`*_[\]()#])/g, "$1")
    .trim();
}

function inlineMarkdownToHtml(value) {
  const colorTokens = [];
  let source = String(value ?? "").replace(
    /<color:(#[0-9A-Fa-f]{6})>(.*?)<\/color>/gs,
    (_match, color, content) => {
      const token = `\u0000COLOR${colorTokens.length}\u0000`;
      colorTokens.push(`<span style="color:${color}">${inlineMarkdownToHtml(content)}</span>`);
      return token;
    }
  );
  source = escapeHtml(source)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, "<em>$1</em>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, (_match, label, href) => {
      const safe = safeImageUrl(href);
      return safe ? `<a href="${escapeHtml(safe)}">${label}</a>` : label;
    });
  colorTokens.forEach((html, index) => {
    source = source.replace(`\u0000COLOR${index}\u0000`, html);
  });
  return source;
}

function markdownToHtml(markdown) {
  const lines = String(markdown || "").replaceAll("\r\n", "\n").split("\n");
  const output = [];
  let listType = null;
  const closeList = () => {
    if (listType) {
      output.push(`</${listType}>`);
      listType = null;
    }
  };
  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    const image = /^!\[([^\]]*)]\(([^)]+)\)\s*$/.exec(line);
    if (image) {
      closeList();
      const src = safeImageUrl(image[2]);
      if (src) {
        output.push(
          `<figure class="note-image" contenteditable="false"><img src="${escapeHtml(src)}" alt="${escapeHtml(image[1])}"><figcaption contenteditable="true">${escapeHtml(image[1] || "图片")}</figcaption></figure>`
        );
      }
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      closeList();
      const level = heading[1].length;
      output.push(`<h${level}>${inlineMarkdownToHtml(heading[2])}</h${level}>`);
      continue;
    }
    const unordered = /^[-*]\s+(.*)$/.exec(line);
    if (unordered) {
      if (listType !== "ul") {
        closeList();
        output.push("<ul>");
        listType = "ul";
      }
      output.push(`<li>${inlineMarkdownToHtml(unordered[1])}</li>`);
      continue;
    }
    const ordered = /^\d+\.\s+(.*)$/.exec(line);
    if (ordered) {
      if (listType !== "ol") {
        closeList();
        output.push("<ol>");
        listType = "ol";
      }
      output.push(`<li>${inlineMarkdownToHtml(ordered[1])}</li>`);
      continue;
    }
    const quote = /^>\s?(.*)$/.exec(line);
    if (quote) {
      closeList();
      output.push(`<blockquote>${inlineMarkdownToHtml(quote[1])}</blockquote>`);
      continue;
    }
    const centered = /^<center>(.*)<\/center>$/.exec(line.trim());
    if (centered) {
      closeList();
      output.push(`<div style="text-align:center">${inlineMarkdownToHtml(centered[1])}</div>`);
      continue;
    }
    if (!line.trim()) {
      closeList();
      output.push("<div><br></div>");
      continue;
    }
    closeList();
    output.push(`<div>${inlineMarkdownToHtml(line)}</div>`);
  }
  closeList();
  return output.join("");
}

function inlineHtmlToMarkdown(node) {
  if (node.nodeType === Node.TEXT_NODE) {
    return escapeMarkdown(node.textContent || "");
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return "";
  const tag = node.tagName.toLowerCase();
  const children = [...node.childNodes].map(inlineHtmlToMarkdown).join("");
  if (tag === "br") return "\n";
  if (tag === "strong" || tag === "b") return `**${children}**`;
  if (tag === "em" || tag === "i") return `*${children}*`;
  if (tag === "code") return `\`${children}\``;
  if (tag === "a") {
    const href = safeImageUrl(node.getAttribute("href") || "");
    return href ? `[${children}](${href})` : children;
  }
  if (tag === "font") {
    const color = node.getAttribute("color") || "";
    return /^#[0-9A-Fa-f]{6}$/.test(color) ? `<color:${color}>${children}</color>` : children;
  }
  if (tag === "span") {
    const color = cssColorToHex(node.style?.color);
    return color ? `<color:${color}>${children}</color>` : children;
  }
  return children;
}

function blockHtmlToMarkdown(node) {
  if (node.nodeType === Node.TEXT_NODE) return escapeMarkdown(node.textContent || "");
  if (node.nodeType !== Node.ELEMENT_NODE) return "";
  const tag = node.tagName.toLowerCase();
  if (/^h[1-6]$/.test(tag)) {
    return `${"#".repeat(Number(tag[1]))} ${[...node.childNodes].map(inlineHtmlToMarkdown).join("")}`;
  }
  if (tag === "figure") {
    const image = node.querySelector("img");
    if (!image) return "";
    const src = safeImageUrl(image.getAttribute("src") || "");
    if (!src) return "";
    const caption = node.querySelector("figcaption")?.innerText?.trim() || image.getAttribute("alt") || "图片";
    return `![${escapeMarkdown(caption)}](${src})`;
  }
  if (tag === "blockquote") {
    return [...node.childNodes]
      .map(inlineHtmlToMarkdown)
      .join("")
      .split("\n")
      .map((line) => `> ${line}`)
      .join("\n");
  }
  if (tag === "ul" || tag === "ol") {
    return [...node.children].map((item, index) => {
      const prefix = tag === "ol" ? `${index + 1}. ` : "- ";
      return prefix + [...item.childNodes].map(inlineHtmlToMarkdown).join("").trim();
    }).join("\n");
  }
  const content = [...node.childNodes].map(inlineHtmlToMarkdown).join("").trim();
  if (!content) return "";
  if (node.style?.textAlign === "center") return `<center>${content}</center>`;
  return content;
}

function htmlToMarkdown(html) {
  const template = document.createElement("template");
  template.innerHTML = html || "";
  const blocks = [...template.content.childNodes]
    .map((node) => blockHtmlToMarkdown(node))
    .filter((value) => value !== "");
  return blocks.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

function sanitizeEditorHtml(html) {
  const template = document.createElement("template");
  template.innerHTML = html;
  template.content
    .querySelectorAll("script, iframe, object, embed, link, meta, style")
    .forEach((node) => node.remove());
  template.content.querySelectorAll("*").forEach((node) => {
    [...node.attributes].forEach((attribute) => {
      const name = attribute.name.toLowerCase();
      const value = attribute.value.trim().toLowerCase();
      if (name.startsWith("on") || value.startsWith("javascript:")) {
        node.removeAttribute(attribute.name);
      }
    });
  });
  return template.innerHTML;
}

function normalizeNote(note) {
  if (!note.bodyMarkdown) {
    note.bodyMarkdown = note.bodyHtml
      ? htmlToMarkdown(note.bodyHtml)
      : (note.bodyText || "");
  }
  delete note.bodyHtml;
  note.bodyText = markdownToPlainText(note.bodyMarkdown);
  note.attachments = note.attachments || [];
  return note;
}

function selectedNoteVisible() {
  const note = currentNote();
  if (!note) return false;
  if (note.trashed !== (state.view === "trash")) return false;
  if (state.view === "starred" && !note.starred) return false;
  if (state.view === "folder" && note.folderId !== state.folderId) return false;
  return true;
}

function filteredNotes() {
  const query = state.query.trim().toLocaleLowerCase();
  const result = state.data.notes.filter((note) => {
    if (state.view === "trash") {
      if (!note.trashed) return false;
    } else if (note.trashed) {
      return false;
    }
    if (state.view === "starred" && !note.starred) return false;
    if (state.view === "folder" && note.folderId !== state.folderId) return false;
    if (!query) return true;
    return (
      note.title.toLocaleLowerCase().includes(query) ||
      note.bodyText.toLocaleLowerCase().includes(query) ||
      (note.tags || "").toLocaleLowerCase().includes(query)
    );
  });
  result.sort((a, b) => {
    if (Boolean(a.pinned) !== Boolean(b.pinned)) return a.pinned ? -1 : 1;
    const difference = state.sortDescending ? b.updatedAt - a.updatedAt : a.updatedAt - b.updatedAt;
    return difference || b.createdAt - a.createdAt;
  });
  return result;
}

function viewTitle() {
  if (state.view === "starred") return "加星便签";
  if (state.view === "trash") return "回收站";
  if (state.view === "folder") {
    return state.data.folders.find((folder) => folder.id === state.folderId)?.name || "文件夹";
  }
  return "全部便签";
}

function formatTime(timestamp) {
  const date = new Date(timestamp);
  const today = new Date();
  const time = date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  if (date.toDateString() === today.toDateString()) return `今天 ${time}`;
  return date.toLocaleDateString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function renderCounts() {
  elements.allCount.textContent = state.data.notes.filter((note) => !note.trashed).length;
  elements.starredCount.textContent = state.data.notes.filter(
    (note) => !note.trashed && note.starred
  ).length;
  elements.trashCount.textContent = state.data.notes.filter((note) => note.trashed).length;
}

function renderFolders() {
  elements.folderList.replaceChildren();
  for (const folder of state.data.folders) {
    const row = document.createElement("div");
    row.className = "folder-item";
    row.tabIndex = 0;
    row.setAttribute("role", "button");
    row.classList.toggle("active", state.view === "folder" && state.folderId === folder.id);
    row.innerHTML = `
      <span class="folder-dot" style="background:${escapeHtml(folder.color)}"></span>
      <span>${escapeHtml(folder.name)}</span>
      <button class="folder-menu-button" type="button" title="文件夹操作" aria-label="文件夹操作">•••</button>
    `;
    row.addEventListener("click", () => {
      state.view = "folder";
      state.folderId = folder.id;
      render();
    });
    row.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        row.click();
      }
    });
    row.querySelector(".folder-menu-button").addEventListener("click", (event) => {
      event.stopPropagation();
      openFolderMenu(folder);
    });
    elements.folderList.append(row);
  }
}

function renderFolderSelect() {
  const stale = elements.folderSelect.value;
  elements.folderSelect.replaceChildren();
  const none = document.createElement("option");
  none.value = "";
  none.textContent = "全部便签";
  elements.folderSelect.append(none);
  state.data.folders.forEach((folder) => {
    const option = document.createElement("option");
    option.value = folder.id;
    option.textContent = folder.name;
    elements.folderSelect.append(option);
  });
  const note = currentNote();
  elements.folderSelect.value = note?.folderId || (stale && state.selectedId ? stale : "");
}

function icon(name) {
  const paths = {
    pin: '<path d="m9 4 6 0-.7 6 4.7 4.7H5l4.7-4.7L9 4ZM12 15v6"></path>',
    star: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z"></path>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"></rect><circle cx="8" cy="9" r="2"></circle><path d="m4 18 5-5 4 4 3-3 4 4"></path>'
  };
  return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || ""}</svg>`;
}

function renderNoteList() {
  const notes = filteredNotes();
  elements.noteList.replaceChildren();
  elements.emptyList.classList.toggle("hidden", notes.length > 0);
  elements.noteList.classList.toggle("hidden", notes.length === 0);
  for (const note of notes) {
    const card = document.createElement("article");
    card.className = "note-card";
    card.classList.toggle("active", note.id === state.selectedId);
    card.innerHTML = `
      <span class="note-clip" aria-hidden="true"></span>
      <span class="note-gutter"></span>
      <div class="note-main">
        <div class="note-time">${escapeHtml(formatTime(note.updatedAt))}</div>
        <div class="note-title">${escapeHtml(note.title || "无标题")}</div>
        <div class="note-preview">${escapeHtml(note.bodyText || "空白便签")}</div>
      </div>
      <div class="note-flags">
        ${note.pinned ? icon("pin") : ""}
        ${note.starred ? icon("star") : ""}
        ${/!\[[^\]]*]\([^)]+\)/.test(note.bodyMarkdown) ? icon("image") : ""}
      </div>
    `;
    card.addEventListener("click", () => selectNote(note.id));
    elements.noteList.append(card);
  }
  elements.listTitle.textContent = viewTitle();
  elements.listSubtitle.textContent = `${notes.length} 条便签`;
}

function renderEditor() {
  const note = currentNote();
  const showEditor = Boolean(note);
  elements.editorEmpty.classList.toggle("hidden", showEditor);
  elements.editorContent.classList.toggle("hidden", !showEditor);
  if (!note) return;

  if (elements.noteTitle.value !== note.title) elements.noteTitle.value = note.title;
  const renderedMarkdown = markdownToHtml(note.bodyMarkdown);
  if (!elements.noteEditor.contains(document.activeElement) && elements.noteEditor.innerHTML !== renderedMarkdown) {
    elements.noteEditor.innerHTML = renderedMarkdown;
  }
  elements.noteEditor.contentEditable = note.trashed ? "false" : "true";
  elements.noteTitle.disabled = note.trashed;
  elements.trashBanner.classList.toggle("hidden", !note.trashed);
  elements.starButton.classList.toggle("active", Boolean(note.starred));
  elements.pinButton.classList.toggle("active", Boolean(note.pinned));
  elements.starButton.setAttribute("aria-pressed", String(Boolean(note.starred)));
  elements.pinButton.setAttribute("aria-pressed", String(Boolean(note.pinned)));
  renderFolderSelect();
}

function render() {
  renderCounts();
  renderFolders();
  renderNoteList();
  renderEditor();
}

function selectNote(id) {
  flushSave();
  state.selectedId = id;
  render();
}

function createNote() {
  flushSave();
  const timestamp = now();
  const note = {
    id: uid(),
    title: "",
    bodyMarkdown: "",
    bodyText: "",
    tags: "",
    folderId: state.view === "folder" ? state.folderId : null,
    starred: false,
    pinned: false,
    trashed: false,
    attachments: [],
    createdAt: timestamp,
    updatedAt: timestamp,
    deletedAt: null
  };
  state.data.notes.unshift(note);
  state.selectedId = note.id;
  state.view = "all";
  render();
  elements.noteTitle.focus();
  scheduleSave();
}

function markSaving() {
  elements.saveState.textContent = "正在保存…";
}

function scheduleSave() {
  const note = currentNote();
  if (!note || note.trashed) return;
  note.title = elements.noteTitle.value;
  note.bodyMarkdown = htmlToMarkdown(sanitizeEditorHtml(elements.noteEditor.innerHTML));
  note.bodyText = markdownToPlainText(note.bodyMarkdown);
  note.updatedAt = now();
  markSaving();
  clearTimeout(state.saveTimer);
  state.saveTimer = setTimeout(() => {
    flushSave();
    renderNoteList();
  }, 450);
}

async function flushSave() {
  const note = currentNote();
  if (note && !note.trashed) {
    note.title = elements.noteTitle.value;
    note.bodyMarkdown = htmlToMarkdown(sanitizeEditorHtml(elements.noteEditor.innerHTML));
    note.bodyText = markdownToPlainText(note.bodyMarkdown);
  }
  clearTimeout(state.saveTimer);
  state.saveTimer = null;
  await window.quyang.saveData(state.data);
  elements.saveState.textContent = "已保存";
}

function showToast(message) {
  clearTimeout(state.toastTimer);
  elements.toast.textContent = message;
  elements.toast.classList.add("show");
  state.toastTimer = setTimeout(() => elements.toast.classList.remove("show"), 1800);
}

function rememberSelection() {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return;
  const range = selection.getRangeAt(0);
  if (elements.noteEditor.contains(range.commonAncestorContainer)) {
    state.savedEditorRange = range.cloneRange();
  }
}

function restoreSelection() {
  if (!state.savedEditorRange) return;
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(state.savedEditorRange);
}

function executeCommand(command, value = null) {
  const note = currentNote();
  if (!note || note.trashed) return;
  restoreSelection();
  elements.noteEditor.focus();
  document.execCommand(command, false, value);
  rememberSelection();
  scheduleSave();
}

function applyToolbarCommand(command) {
  if (command === "undo" || command === "redo") {
    executeCommand(command);
    return;
  }
  if (command === "bold" || command === "center" || command === "list") {
    const map = { bold: "bold", center: "justifyCenter", list: "insertUnorderedList" };
    executeCommand(map[command]);
    return;
  }
  if (command === "heading" || command === "quote") {
    const tag = command === "heading" ? "h1" : "blockquote";
    const current = document.queryCommandValue("formatBlock").toLowerCase();
    executeCommand("formatBlock", current === tag ? "<p>" : `<${tag}>`);
  }
}

function applyColor(color) {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    showToast("请先选择要改色的文字");
    return;
  }
  executeCommand("foreColor", color);
  elements.colorMenu.classList.add("hidden");
  elements.colorButton.setAttribute("aria-expanded", "false");
}

async function insertImages() {
  const note = currentNote();
  if (!note || note.trashed) return;
  rememberSelection();
  const images = await window.quyang.chooseImages();
  if (!images.length) return;
  restoreSelection();
  elements.noteEditor.focus();
  for (const image of images) {
    const figure = document.createElement("figure");
    figure.className = "note-image";
    figure.contentEditable = "false";
    figure.innerHTML = `
      <img src="${escapeHtml(image.url)}" alt="${escapeHtml(image.name)}">
      <figcaption contenteditable="true">${escapeHtml(image.name)}</figcaption>
    `;
    const selection = window.getSelection();
    if (selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      range.deleteContents();
      range.insertNode(figure);
      range.setStartAfter(figure);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    } else {
      elements.noteEditor.append(figure);
    }
    note.attachments.push(image);
  }
  scheduleSave();
  showToast(`已插入 ${images.length} 张图片`);
}

function moveToTrash(note) {
  if (!note || note.trashed) return;
  note.trashed = true;
  note.deletedAt = now();
  note.updatedAt = now();
  flushSave().then(() => {
    state.selectedId = null;
    render();
  });
}

function restoreNote() {
  const note = currentNote();
  if (!note) return;
  note.trashed = false;
  note.deletedAt = null;
  note.updatedAt = now();
  state.view = "all";
  flushSave().then(render);
}

function permanentlyDelete() {
  const note = currentNote();
  if (!note) return;
  if (!confirm(`彻底删除“${note.title || "无标题"}”？此操作无法撤销。`)) return;
  state.data.notes = state.data.notes.filter((item) => item.id !== note.id);
  state.selectedId = null;
  flushSave().then(render);
}

function openFolderMenu(folder) {
  const name = prompt("重命名文件夹", folder.name);
  if (name == null) return;
  if (!name.trim()) {
    if (confirm(`删除文件夹“${folder.name}”？其中的便签会移到全部便签。`)) {
      state.data.notes.forEach((note) => {
        if (note.folderId === folder.id) note.folderId = null;
      });
      state.data.folders = state.data.folders.filter((item) => item.id !== folder.id);
      if (state.folderId === folder.id) {
        state.view = "all";
        state.folderId = null;
      }
      flushSave().then(render);
    }
    return;
  }
  folder.name = name.trim();
  flushSave().then(render);
}

function addFolder() {
  const name = prompt("新建文件夹");
  if (!name?.trim()) return;
  state.data.folders.push({
    id: uid(),
    name: name.trim(),
    color: "#bf7729"
  });
  flushSave().then(render);
}

function buildExportHtml(note) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(note.title || "无标题")}</title>
<style>
body{margin:0;background:#e9e1d2;color:#4f3929;font-family:"Microsoft YaHei UI",sans-serif}
main{max-width:900px;min-height:100vh;margin:auto;padding:56px 70px 90px 92px;background-color:#fbf7ed;background-image:linear-gradient(to bottom,transparent 41px,#ded4c5 42px);background-size:100% 42px;border-left:46px solid #eee3d3;box-shadow:0 0 28px #6d543b40}
h1{font-family:"STSong","SimSun",serif;font-size:32px;margin:0 0 42px}
article{font-size:17px;line-height:42px;white-space:pre-wrap}
figure{margin:26px 0;padding:10px;background:#fffefa;border-radius:10px;box-shadow:0 6px 20px #4d38251f}
img{display:block;width:100%;height:auto;border-radius:7px}
figcaption{color:#806f5d;font-size:13px;padding-top:8px}
blockquote{margin:0;padding-left:16px;border-left:3px solid #bf7729;color:#7a6958}
</style>
</head>
<body><main><h1>${escapeHtml(note.title || "无标题")}</h1><article>${markdownToHtml(note.bodyMarkdown)}</article></main></body>
</html>`;
}

async function exportCurrentNote() {
  const note = currentNote();
  if (!note) return;
  await flushSave();
  const saved = await window.quyang.exportHtml({
    title: note.title,
    html: buildExportHtml(note)
  });
  if (saved) showToast("网页已导出");
}

async function copyCurrentNote() {
  const note = currentNote();
  if (!note) return;
  await flushSave();
  await navigator.clipboard.writeText(
    `${note.title || "无标题"}\n\n${note.bodyText || ""}`
  );
  showToast("便签文字已复制");
}

function applyTheme() {
  const theme = state.data.settings.theme || "system";
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.setProperty(
    "--editor-scale",
    String(state.data.settings.fontScale || 1)
  );
}

function webDavConfig() {
  return {
    fileUrl: state.data.settings.webDavUrl || "",
    username: state.data.settings.webDavUsername || "",
    password: state.data.settings.webDavPassword || ""
  };
}

function updateWebDavStatus(message) {
  elements.webDavStatus.textContent = message;
}

function mergeRemoteNotes(remoteNotes) {
  const byId = new Map(state.data.notes.map((note) => [String(note.id), note]));
  for (const remote of remoteNotes) {
    normalizeNote(remote);
    const local = byId.get(String(remote.id));
    if (!local || remote.updatedAt >= local.updatedAt) {
      byId.set(String(remote.id), remote);
    }
  }
  state.data.notes = [...byId.values()];
  if (state.selectedId && !state.data.notes.some((note) => note.id === state.selectedId)) {
    state.selectedId = state.data.notes[0]?.id || null;
  }
}

async function syncWebDav({ silent = false } = {}) {
  if (state.syncing || !state.data.settings.webDavUrl?.trim()) {
    if (!silent) updateWebDavStatus("请先填写 WebDAV 地址");
    return;
  }
  state.syncing = true;
  elements.webDavTestButton.disabled = true;
  elements.webDavSyncButton.disabled = true;
  elements.webDavSyncButton.textContent = "同步中…";
  updateWebDavStatus("正在比较本地与远端便签…");
  try {
    await flushSave();
    const result = await window.quyang.syncWebDav(webDavConfig(), state.data.notes);
    mergeRemoteNotes(result.notes || []);
    state.data.settings.lastWebDavSync = result.syncedAt;
    await flushSave();
    render();
    const time = new Date(result.syncedAt).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit"
    });
    updateWebDavStatus(`同步成功 · ${time} · 远端 ${result.downloaded || 0} 篇`);
    if (!silent) showToast("WebDAV 同步成功");
  } catch (error) {
    updateWebDavStatus(`同步失败：${error.message}`);
    if (!silent) showToast(`同步失败：${error.message}`);
  } finally {
    state.syncing = false;
    elements.webDavTestButton.disabled = false;
    elements.webDavSyncButton.disabled = false;
    elements.webDavSyncButton.textContent = "立即同步";
  }
}

function restartAutoSync() {
  clearInterval(state.webDavTimer);
  state.webDavTimer = null;
  if (
    state.data.settings.webDavAutoSync &&
    state.data.settings.webDavUrl?.trim()
  ) {
    state.webDavTimer = setInterval(() => syncWebDav({ silent: true }), 30_000);
  }
}

function updateUpdaterStatus(status) {
  elements.updateStatus.textContent = status.message || "更新状态未知";
  const downloading = status.state === "downloading";
  const downloaded = status.state === "downloaded";
  elements.updateCheckButton.disabled = downloading || status.state === "installing";
  elements.updateInstallButton.disabled = !downloaded;
  elements.updateInstallButton.hidden = !downloaded && status.state !== "installing";
  elements.updateCheckButton.textContent = downloading ? "下载中…" : "立即检查更新";
  if (downloaded) {
    elements.updateInstallButton.textContent = `重启并升级到 ${status.latestVersion}`;
    showToast(`新版本 ${status.latestVersion} 已下载`);
  }
}

async function checkForUpdates({ automatic = false, retried = false } = {}) {
  if (elements.updateCheckButton.disabled) return null;
  elements.updateCheckButton.disabled = true;
  try {
    const result = await window.quyang.checkForUpdates();
    updateUpdaterStatus(result);
    if (automatic && result.state === "downloaded") {
      setTimeout(() => {
        if (confirm(`曲阳便签 ${result.latestVersion} 已下载，是否立即重启升级？`)) {
          installUpdate();
        }
      }, 600);
    }
    return result;
  } catch (error) {
    updateUpdaterStatus({ state: "error", message: `检查更新失败：${error.message}` });
    if (automatic && !retried && state.data.settings.autoUpdateEnabled) {
      state.updateStartupTimer = setTimeout(
        () => checkForUpdates({ automatic: true, retried: true }),
        15_000
      );
    }
    return null;
  } finally {
    if (!elements.updateStatus.textContent.startsWith("正在下载")) {
      elements.updateCheckButton.disabled = false;
    }
  }
}

async function installUpdate() {
  try {
    elements.updateInstallButton.disabled = true;
    await flushSave();
    await window.quyang.installUpdate();
  } catch (error) {
    elements.updateInstallButton.disabled = false;
    updateUpdaterStatus({ state: "error", message: `升级失败：${error.message}` });
  }
}

function restartUpdateChecks() {
  clearTimeout(state.updateStartupTimer);
  clearInterval(state.updateTimer);
  state.updateStartupTimer = null;
  state.updateTimer = null;
  if (state.data.settings.autoUpdateEnabled) {
    state.updateStartupTimer = setTimeout(
      () => checkForUpdates({ automatic: true }),
      2500
    );
    state.updateTimer = setInterval(
      () => checkForUpdates({ automatic: false }),
      6 * 60 * 60 * 1000
    );
  }
}

function setupColorMenu() {
  for (const [label, color] of COLORS) {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.color = color;
    button.innerHTML = `
      <span class="color-swatch" style="background:${color}"></span>
      <span>${label}</span>
    `;
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => applyColor(color));
    elements.colorMenu.append(button);
  }
}

function bindEvents() {
  document.querySelectorAll(".view-item").forEach((button) => {
    button.addEventListener("click", () => {
      flushSave();
      state.view = button.dataset.view;
      state.folderId = null;
      document.querySelectorAll(".view-item").forEach((item) => {
        item.classList.toggle("active", item === button);
      });
      render();
    });
  });

  elements.searchInput.addEventListener("input", () => {
    state.query = elements.searchInput.value;
    renderNoteList();
  });
  elements.newNoteButton.addEventListener("click", createNote);
  elements.addFolderButton.addEventListener("click", addFolder);
  elements.noteTitle.addEventListener("input", scheduleSave);
  elements.noteEditor.addEventListener("input", scheduleSave);
  document.addEventListener("selectionchange", rememberSelection);

  document.querySelectorAll("[data-command]").forEach((button) => {
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => applyToolbarCommand(button.dataset.command));
  });

  elements.colorButton.addEventListener("mousedown", (event) => event.preventDefault());
  elements.colorButton.addEventListener("click", () => {
    const opening = elements.colorMenu.classList.contains("hidden");
    elements.colorMenu.classList.toggle("hidden", !opening);
    elements.colorButton.setAttribute("aria-expanded", String(opening));
  });

  document.addEventListener("click", (event) => {
    if (!event.target.closest(".color-control")) {
      elements.colorMenu.classList.add("hidden");
      elements.colorButton.setAttribute("aria-expanded", "false");
    }
  });

  elements.imageButton.addEventListener("mousedown", (event) => event.preventDefault());
  elements.imageButton.addEventListener("click", insertImages);

  elements.starButton.addEventListener("click", () => {
    const note = currentNote();
    if (!note) return;
    note.starred = !note.starred;
    note.updatedAt = now();
    flushSave().then(render);
  });

  elements.pinButton.addEventListener("click", () => {
    const note = currentNote();
    if (!note) return;
    note.pinned = !note.pinned;
    note.updatedAt = now();
    flushSave().then(render);
  });

  elements.deleteButton.addEventListener("click", () => moveToTrash(currentNote()));
  elements.restoreButton.addEventListener("click", restoreNote);
  elements.permanentDeleteButton.addEventListener("click", permanentlyDelete);
  elements.copyButton.addEventListener("click", copyCurrentNote);
  elements.exportButton.addEventListener("click", exportCurrentNote);

  elements.folderSelect.addEventListener("change", () => {
    const note = currentNote();
    if (!note) return;
    note.folderId = elements.folderSelect.value || null;
    note.updatedAt = now();
    flushSave().then(render);
  });

  document.querySelector("#sortButton").addEventListener("click", () => {
    state.sortDescending = !state.sortDescending;
    renderNoteList();
  });

  elements.settingsButton.addEventListener("click", () => {
    elements.themeSelect.value = state.data.settings.theme || "system";
    elements.fontScaleInput.value = String(state.data.settings.fontScale || 1);
    elements.fontScaleLabel.textContent = `${Math.round((state.data.settings.fontScale || 1) * 100)}%`;
    elements.webDavUrl.value = state.data.settings.webDavUrl || "";
    elements.webDavUsername.value = state.data.settings.webDavUsername || "";
    elements.webDavPassword.value = state.data.settings.webDavPassword || "";
    elements.webDavAutoSync.checked = Boolean(state.data.settings.webDavAutoSync);
    updateWebDavStatus(
      state.data.settings.webDavUrl
        ? (state.data.settings.webDavAutoSync ? "自动同步已开启" : "WebDAV 已配置")
        : "尚未配置"
    );
    elements.settingsDialog.showModal();
  });

  elements.themeSelect.addEventListener("change", () => {
    state.data.settings.theme = elements.themeSelect.value;
    applyTheme();
    flushSave();
  });

  elements.fontScaleInput.addEventListener("input", () => {
    const scale = Number(elements.fontScaleInput.value);
    state.data.settings.fontScale = scale;
    elements.fontScaleLabel.textContent = `${Math.round(scale * 100)}%`;
    applyTheme();
    flushSave();
  });

  elements.webDavUrl.addEventListener("input", () => {
    state.data.settings.webDavUrl = elements.webDavUrl.value.trim();
    flushSave();
    restartAutoSync();
  });

  elements.webDavUsername.addEventListener("input", () => {
    state.data.settings.webDavUsername = elements.webDavUsername.value.trim();
    flushSave();
  });

  elements.webDavPassword.addEventListener("input", () => {
    state.data.settings.webDavPassword = elements.webDavPassword.value;
    flushSave();
  });

  elements.webDavAutoSync.addEventListener("change", () => {
    state.data.settings.webDavAutoSync = elements.webDavAutoSync.checked;
    flushSave();
    restartAutoSync();
    updateWebDavStatus(
      elements.webDavAutoSync.checked ? "自动同步已开启" : "自动同步已关闭"
    );
  });

  elements.webDavTestButton.addEventListener("click", async () => {
    elements.webDavTestButton.disabled = true;
    updateWebDavStatus("正在测试连接…");
    try {
      const result = await window.quyang.testWebDav(webDavConfig());
      updateWebDavStatus(`连接成功 · ${result.rootUrl}`);
      showToast("WebDAV 连接成功");
    } catch (error) {
      updateWebDavStatus(`连接失败：${error.message}`);
    } finally {
      elements.webDavTestButton.disabled = false;
    }
  });

  elements.webDavSyncButton.addEventListener("click", () => syncWebDav());
  elements.autoUpdateEnabled.addEventListener("change", () => {
    state.data.settings.autoUpdateEnabled = elements.autoUpdateEnabled.checked;
    flushSave();
    restartUpdateChecks();
    if (!elements.autoUpdateEnabled.checked) {
      updateUpdaterStatus({ state: "idle", message: "自动更新已关闭" });
    }
  });

  elements.updateCheckButton.addEventListener("click", () => checkForUpdates());
  elements.updateInstallButton.addEventListener("click", installUpdate);
  window.quyang.onUpdaterStatus(updateUpdaterStatus);

  elements.exportDataButton.addEventListener("click", async () => {
    await flushSave();
    if (await window.quyang.exportData(state.data)) showToast("便签数据已导出");
  });

  elements.importDataButton.addEventListener("click", async () => {
    const imported = await window.quyang.chooseImport();
    if (!imported || !Array.isArray(imported.notes)) return;
    const byId = new Map(state.data.notes.map((note) => [note.id, note]));
    imported.notes.forEach((note) => byId.set(note.id, normalizeNote(note)));
    state.data.notes = [...byId.values()];
    state.data.folders = Array.isArray(imported.folders)
      ? imported.folders
      : state.data.folders;
    state.data.settings = imported.settings || state.data.settings;
    await flushSave();
    applyTheme();
    render();
    showToast("便签数据已导入");
  });

  window.addEventListener("beforeunload", () => {
    if (state.saveTimer) flushSave();
  });

  window.quyang.onMenuAction((action) => {
    if (action === "new-note") createNote();
    if (action === "save") flushSave().then(() => showToast("已保存"));
    if (action === "import-data") elements.importDataButton.click();
  });
}

async function initialize() {
  setupColorMenu();
  bindEvents();
  state.data = await window.quyang.loadData();
  state.data.notes = state.data.notes || [];
  state.data.notes.forEach(normalizeNote);
  state.data.folders = state.data.folders || [];
  state.data.settings = state.data.settings || {
    theme: "system",
    fontScale: 1,
    webDavUrl: "",
    webDavUsername: "",
    webDavPassword: "",
    webDavAutoSync: false,
    autoUpdateEnabled: true
  };
  state.data.settings.webDavUrl ||= "";
  state.data.settings.webDavUsername ||= "";
  state.data.settings.webDavPassword ||= "";
  state.data.settings.webDavAutoSync = Boolean(state.data.settings.webDavAutoSync);
  state.data.settings.autoUpdateEnabled = state.data.settings.autoUpdateEnabled !== false;
  elements.autoUpdateEnabled.checked = state.data.settings.autoUpdateEnabled;
  const appVersion = await window.quyang.getAppVersion();
  elements.appVersionLabel.textContent = appVersion;
  updateUpdaterStatus({ state: "idle", message: `当前版本 ${appVersion}` });
  applyTheme();
  restartAutoSync();
  restartUpdateChecks();
  render();

  if (state.data.notes.length > 0) {
    const first = [...state.data.notes]
      .filter((note) => !note.trashed)
      .sort((a, b) => b.updatedAt - a.updatedAt)[0];
    if (first) state.selectedId = first.id;
  }
  render();
}

initialize().catch((error) => {
  console.error(error);
  showToast(`启动失败：${error.message}`);
});
