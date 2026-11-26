const crypto = require("node:crypto");
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const { spawn } = require("node:child_process");
const { finished } = require("node:stream/promises");

const RELEASE_API = "https://api.github.com/repos/zzg341125/bijiruanjian/releases/latest";
const DOWNLOAD_TIMEOUT_MILLIS = 30 * 60 * 1000;
let downloadedUpdate = null;

function normalizeVersion(value) {
  return String(value || "").trim().replace(/^v/i, "");
}

function compareVersions(left, right) {
  const a = normalizeVersion(left).split(".").map((part) => Number.parseInt(part, 10) || 0);
  const b = normalizeVersion(right).split(".").map((part) => Number.parseInt(part, 10) || 0);
  for (let index = 0; index < Math.max(a.length, b.length); index++) {
    const difference = (a[index] || 0) - (b[index] || 0);
    if (difference !== 0) return difference > 0 ? 1 : -1;
  }
  return 0;
}

function selectWindowsAsset(assets) {
  const executables = (assets || []).filter(
    (asset) => /\.exe$/i.test(asset.name || "") && !/\.sha256$/i.test(asset.name || "")
  );
  return executables.find((asset) => /windows/i.test(asset.name) && /portable/i.test(asset.name)) ||
    executables.find((asset) => /windows/i.test(asset.name)) ||
    executables[0] ||
    null;
}

function checksumForAsset(text, assetName) {
  const normalizedName = path.basename(assetName || "").toLowerCase();
  const line = String(text || "").split(/\r?\n/).find((candidate) => {
    const fileName = candidate.trim().split(/\s+/).pop()?.replace(/^\*/, "").toLowerCase();
    return fileName === normalizedName;
  });
  return line?.trim().split(/\s+/)[0]?.toLowerCase() || "";
}

async function sha256File(filePath) {
  const hash = crypto.createHash("sha256");
  await finished(fs.createReadStream(filePath).on("data", (chunk) => hash.update(chunk)));
  return hash.digest("hex");
}

async function downloadFile(url, destination, onProgress) {
  const response = await fetch(url, {
    headers: {
      Accept: "application/octet-stream",
      "User-Agent": "QuyangNotes-Windows-Updater"
    },
    signal: AbortSignal.timeout(DOWNLOAD_TIMEOUT_MILLIS),
    redirect: "follow"
  });
  if (!response.ok || !response.body) {
    throw new Error(`下载安装包失败：HTTP ${response.status}`);
  }
  const total = Number(response.headers.get("content-length")) || 0;
  const temporary = `${destination}.part`;
  const output = fs.createWriteStream(temporary);
  const reader = response.body.getReader();
  let downloaded = 0;
  let lastReportedPercent = -1;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = Buffer.from(value);
      downloaded += chunk.length;
      if (!output.write(chunk)) {
        await new Promise((resolve) => output.once("drain", resolve));
      }
      const percent = total > 0 ? Math.min(100, Math.round((downloaded / total) * 100)) : 0;
      if (percent !== lastReportedPercent) {
        lastReportedPercent = percent;
        onProgress?.({ downloaded, total, percent });
      }
    }
    output.end();
    await finished(output);
    await fsp.rename(temporary, destination);
    return downloaded;
  } catch (error) {
    output.destroy();
    await fsp.rm(temporary, { force: true }).catch(() => {});
    throw error;
  }
}

async function latestRelease() {
  const response = await fetch(RELEASE_API, {
    headers: {
      Accept: "application/vnd.github+json",
      "User-Agent": "QuyangNotes-Windows-Updater"
    },
    signal: AbortSignal.timeout(30_000)
  });
  if (!response.ok) throw new Error(`检查更新失败：HTTP ${response.status}`);
  return response.json();
}

async function checkForUpdates({ currentVersion, updatesDir, onStatus = () => {} }) {
  onStatus({ state: "checking", message: "正在检查新版本…" });
  const release = await latestRelease();
  const latestVersion = normalizeVersion(release.tag_name || release.name);
  if (!latestVersion || compareVersions(latestVersion, currentVersion) <= 0) {
    const result = {
      state: "current",
      currentVersion,
      latestVersion: currentVersion,
      message: "已是最新版本"
    };
    onStatus(result);
    return result;
  }

  const asset = selectWindowsAsset(release.assets);
  if (!asset) throw new Error("新版本没有提供 Windows 安装包");
  await fsp.mkdir(updatesDir, { recursive: true });
  const destination = path.join(updatesDir, path.basename(asset.name));
  onStatus({
    state: "downloading",
    latestVersion,
    message: `正在下载 ${latestVersion}…`
  });
  await downloadFile(asset.browser_download_url, destination, ({ percent }) => {
    onStatus({
      state: "downloading",
      latestVersion,
      percent,
      message: `正在下载 ${latestVersion} · ${percent}%`
    });
  });

  const checksumAsset = (release.assets || []).find(
    (candidate) => /^SHA256SUMS-Windows\.txt$/i.test(candidate.name || "")
  );
  if (checksumAsset) {
    const checksumResponse = await fetch(checksumAsset.browser_download_url, {
      headers: { "User-Agent": "QuyangNotes-Windows-Updater" },
      signal: AbortSignal.timeout(30_000)
    });
    if (!checksumResponse.ok) throw new Error("下载更新校验文件失败");
    const expected = checksumForAsset(await checksumResponse.text(), asset.name);
    const actual = await sha256File(destination);
    if (!expected || actual !== expected) {
      await fsp.rm(destination, { force: true });
      throw new Error("更新安装包校验失败，已停止升级");
    }
  }

  downloadedUpdate = {
    version: latestVersion,
    filePath: destination,
    fileName: path.basename(asset.name)
  };
  const result = {
    state: "downloaded",
    currentVersion,
    latestVersion,
    fileName: downloadedUpdate.fileName,
    message: `新版本 ${latestVersion} 已下载`
  };
  onStatus(result);
  return result;
}

async function installUpdate({ updatesDir, targetPath, currentPid, onQuit }) {
  if (!downloadedUpdate) throw new Error("没有已下载的更新");
  if (!targetPath || !fs.existsSync(targetPath)) {
    throw new Error("无法定位当前便携版程序，请手动下载新版本");
  }
  const helperPath = path.join(updatesDir, "apply-quyang-update.ps1");
  const helper = [
    "param([int]$WaitPid, [string]$Source, [string]$Target, [string]$Launch)",
    "$ErrorActionPreference = 'Stop'",
    "while (Get-Process -Id $WaitPid -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }",
    "$next = \"$Target.new\"",
    "for ($i = 0; $i -lt 120; $i++) {",
    "  try {",
    "    Copy-Item -LiteralPath $Source -Destination $next -Force",
    "    [System.IO.File]::Replace($next, $Target, $null, $true)",
    "    Unblock-File -LiteralPath $Target -ErrorAction SilentlyContinue",
    "    Start-Process -FilePath $Launch -WorkingDirectory (Split-Path -Parent $Launch)",
    "    exit 0",
    "  } catch {",
    "    Start-Sleep -Milliseconds 500",
    "  }",
    "}",
    "exit 1"
  ].join("\r\n");
  await fsp.writeFile(helperPath, helper, "utf8");
  const child = spawn(
    "powershell.exe",
    [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-WindowStyle",
      "Hidden",
      "-File",
      helperPath,
      String(currentPid),
      downloadedUpdate.filePath,
      targetPath,
      targetPath
    ],
    {
      detached: true,
      stdio: "ignore",
      windowsHide: true
    }
  );
  child.unref();
  onQuit?.();
  return { state: "installing", version: downloadedUpdate.version };
}

module.exports = {
  compareVersions,
  selectWindowsAsset,
  checksumForAsset,
  checkForUpdates,
  installUpdate
};
