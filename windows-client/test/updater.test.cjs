const assert = require("node:assert/strict");
const { test } = require("node:test");
const {
  compareVersions,
  selectWindowsAsset,
  checksumForAsset
} = require("../electron/updater.cjs");

test("compares release versions numerically", () => {
  assert.equal(compareVersions("0.11.26", "0.11.25"), 1);
  assert.equal(compareVersions("v0.11.26", "0.11.26"), 0);
  assert.equal(compareVersions("0.9.99", "0.11.0"), -1);
});

test("selects the Windows portable executable", () => {
  const asset = selectWindowsAsset([
    { name: "QuyangNotes-v0.11.27-release.apk" },
    { name: "QuyangNotes-Windows-0.11.27-portable.exe" }
  ]);
  assert.equal(asset.name, "QuyangNotes-Windows-0.11.27-portable.exe");
});

test("reads the matching Windows checksum", () => {
  const text = [
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  other.exe",
    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  QuyangNotes-Windows-0.11.27-portable.exe"
  ].join("\n");
  assert.equal(
    checksumForAsset(text, "QuyangNotes-Windows-0.11.27-portable.exe"),
    "b".repeat(64)
  );
});
