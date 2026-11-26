const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("quyang", {
  loadData: () => ipcRenderer.invoke("data:load"),
  saveData: (data) => ipcRenderer.invoke("data:save", data),
  chooseImages: () => ipcRenderer.invoke("attachments:choose"),
  chooseImport: () => ipcRenderer.invoke("data:choose-import"),
  exportData: (data) => ipcRenderer.invoke("data:export", data),
  exportHtml: (payload) => ipcRenderer.invoke("note:export-html", payload),
  testWebDav: (config) => ipcRenderer.invoke("webdav:test", config),
  syncWebDav: (config, notes) => ipcRenderer.invoke("webdav:sync", config, notes),
  downloadWebDav: (config) => ipcRenderer.invoke("webdav:download", config),
  onMenuAction: (callback) => {
    const listener = (_event, action) => callback(action);
    ipcRenderer.on("menu-action", listener);
    return () => ipcRenderer.removeListener("menu-action", listener);
  }
});
