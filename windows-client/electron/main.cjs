const { app, BrowserWindow, Menu, dialog, ipcMain } = require("electron");
const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const webdav = require("./webdav.cjs");

const APP_NAME = "曲阳便签";
let mainWindow = null;
let saveQueue = Promise.resolve();

function userDataPath(...parts) {
  return path.join(app.getPath("userData"), ...parts);
}

async function ensureStorage() {
  await fs.mkdir(userDataPath("attachments"), { recursive: true });
}

async function loadData() {
  await ensureStorage();
  try {
    const raw = await fs.readFile(userDataPath("notes.json"), "utf8");
    return JSON.parse(raw);
  } catch (error) {
    if (error.code !== "ENOENT") console.error("读取便签数据失败", error);
    return {
      notes: [],
      folders: [],
      settings: {
        theme: "system",
        fontScale: 1,
        webDavUrl: "",
        webDavUsername: "",
        webDavPassword: "",
        webDavAutoSync: false
      }
    };
  }
}

function saveData(data) {
  const serialized = JSON.stringify(data, null, 2);
  saveQueue = saveQueue.then(async () => {
    await ensureStorage();
    const target = userDataPath("notes.json");
    const temporary = `${target}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(temporary, serialized, "utf8");
    await fs.rename(temporary, target);
  }).catch((error) => {
    console.error("保存便签数据失败", error);
    throw error;
  });
  return saveQueue.then(() => true);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 980,
    minHeight: 680,
    title: `${APP_NAME} ${app.getVersion()}`,
    backgroundColor: "#e8dfcf",
    icon: path.join(__dirname, "..", "assets", "icon.png"),
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.loadFile(path.join(__dirname, "..", "src", "index.html"));
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

function buildMenu() {
  const template = [
    {
      label: "文件",
      submenu: [
        {
          label: "新建便签",
          accelerator: "CommandOrControl+N",
          click: () => mainWindow?.webContents.send("menu-action", "new-note")
        },
        {
          label: "保存",
          accelerator: "CommandOrControl+S",
          click: () => mainWindow?.webContents.send("menu-action", "save")
        },
        { type: "separator" },
        {
          label: "导入便签数据",
          click: () => mainWindow?.webContents.send("menu-action", "import-data")
        },
        { type: "separator" },
        { role: "quit", label: "退出" }
      ]
    },
    {
      label: "编辑",
      submenu: [
        { role: "undo", label: "撤销", accelerator: "CommandOrControl+Z" },
        { role: "redo", label: "重做", accelerator: "CommandOrControl+Y" },
        { type: "separator" },
        { role: "cut", label: "剪切" },
        { role: "copy", label: "复制" },
        { role: "paste", label: "粘贴" },
        { role: "selectAll", label: "全选" }
      ]
    },
    {
      label: "查看",
      submenu: [
        { role: "resetZoom", label: "重置缩放" },
        { role: "zoomIn", label: "放大" },
        { role: "zoomOut", label: "缩小" },
        { type: "separator" },
        { role: "togglefullscreen", label: "全屏" }
      ]
    }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

ipcMain.handle("data:load", () => loadData());
ipcMain.handle("data:save", (_event, data) => saveData(data));

ipcMain.handle("attachments:choose", async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: "选择要插入的图片",
    properties: ["openFile", "multiSelections"],
    filters: [
      {
        name: "图片",
        extensions: ["png", "jpg", "jpeg", "webp", "gif", "bmp"]
      }
    ]
  });
  if (result.canceled) return [];
  const attachments = [];
  for (const source of result.filePaths) {
    const suffix = path.extname(source).toLowerCase() || ".png";
    const destination = userDataPath("attachments", `${crypto.randomUUID()}${suffix}`);
    await fs.copyFile(source, destination);
    attachments.push({
      id: crypto.randomUUID(),
      name: path.basename(source),
      path: destination,
      url: pathToFileURL(destination).href
    });
  }
  return attachments;
});

ipcMain.handle("data:choose-import", async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: "导入便签数据",
    properties: ["openFile"],
    filters: [{ name: "曲阳便签数据", extensions: ["json"] }]
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  return JSON.parse(await fs.readFile(result.filePaths[0], "utf8"));
});

ipcMain.handle("data:export", async (_event, data) => {
  const result = await dialog.showSaveDialog(mainWindow, {
    title: "导出便签数据",
    defaultPath: `曲阳便签-${new Date().toISOString().slice(0, 10)}.json`,
    filters: [{ name: "曲阳便签数据", extensions: ["json"] }]
  });
  if (result.canceled || !result.filePath) return false;
  await fs.writeFile(result.filePath, JSON.stringify(data, null, 2), "utf8");
  return true;
});

ipcMain.handle("note:export-html", async (_event, payload) => {
  const result = await dialog.showSaveDialog(mainWindow, {
    title: "导出便签",
    defaultPath: `${payload.title || "无标题"}.html`,
    filters: [{ name: "网页", extensions: ["html"] }]
  });
  if (result.canceled || !result.filePath) return false;
  await fs.writeFile(result.filePath, payload.html, "utf8");
  return true;
});

ipcMain.handle("webdav:test", (_event, config) => webdav.testConnection(config));
ipcMain.handle("webdav:sync", (_event, config, notes) => webdav.syncNotes(config, notes));
ipcMain.handle("webdav:download", (_event, config) => webdav.downloadNotes(config));

app.whenReady().then(async () => {
  app.setName(APP_NAME);
  await ensureStorage();
  buildMenu();
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
