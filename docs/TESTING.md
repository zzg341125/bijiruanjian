# Testing notes

Tested on 2026-11-11 with:

- Android emulator API 37
- JDK 21
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- 曲阳便签 `0.11.2` (`versionCode` 11002)

## Automated checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Result on 2026-11-11:

- 64 unit tests pass with 0 failures and 0 errors.
- Lint: 0 errors, 128 warnings (mostly upstream OAF deprecation and launcher-icon notices).
- Debug APK builds and installs.
- Release build passes R8 minification and produces a 34.8 MB unsigned APK.
- A debug-key-signed copy of the minified release APK installs and launches successfully.
- The minified release APK opens and edits DOCX through OAF; the edit remains after process restart.
- Release APK includes arm64-v8a, armeabi-v7a, x86 and x86_64; minimum Android is API 29.
- Encrypted full backup round-trips notes, folders, reminders and embedded attachments.
- A wrong backup password fails with an actionable error and does not return partial data.
- WebDAV connection creates the dedicated `曲阳便签` folder when it does not exist.
- Batch upload and download tests cover 51 Markdown files without truncation.
- HTTP 429 stops the remaining batch requests so the next foreground sync can retry later.
- Live Nutstore authentication still needs a device test with the user's application password.
- Three-way sync covers local, remote, and previous-success states.
- Remote and local deletions are distinguished from new files on the opposite side.
- Conflict resolution keeps the newer note and preserves both versions on equal timestamps.
- Batch deletion protection blocks removal of more than half of a vault with at least five files.
- WebDAV input containing BOM, zero-width characters, or surrounding whitespace is normalized before parsing.
- WebDAV URLs are validated and encoded through OkHttp `HttpUrl`.
- A concrete WebDAV folder such as `/dav/biji/` is synced directly.
- Only the Jianguoyun `/dav/` root creates the dedicated `曲阳便签` child folder.
- Jianguoyun may return HTTP 400 for a missing child folder; the client creates it with MKCOL and retries PROPFIND.
- PROPFIND matches Obsidian's network request: Depth 1, no XML request body, Accept `text/plain,application/xml`.
- Nutstore Link-header pagination follows all `rel="next"` pages on the same server.
- Remote-imported files are not uploaded again during the same sync.
- Sync summary reports the actual number of Nutstore directory pages scanned.
- Local APK versionCode is 11002 for version 0.11.2.
- WebDAV folder traversal includes nested subfolders and preserves relative Markdown paths.
- The download-and-restore action shows the full sync result rather than a fixed success message.
- Imported notes are written before their attachments, satisfying the SQLite foreign key.
- A malformed remote note no longer aborts the remaining Markdown batch.
- HTTP 429/503 enters a retry cooldown, suppresses automatic retry traffic, and reports remaining wait time.
- Markdown, Markdown files using `.markdown`, plain text, and textual MIME types are synced as notes.
- Binary files are counted and reported as skipped instead of silently disappearing.
- Remote relative filenames are reused for local mirrors and later uploads.
- Excel, Word, images, PDF and other binary files are imported as attachment notes.
- Binary sync state is independent from Markdown state and keeps the remote relative path.
- Room migration 1→2 adds the binary-note marker without deleting existing notes.
- New notes use their title for the first remote filename, then keep that name stable.
- DOCX/XLSX use the embedded Apache-2.0 OAF Office engine on all packaged ABIs.
- OAF starts as an internal `exported=false` activity and does not require WPS or Microsoft 365.
- OAF and the host app use separate DataStore files, avoiding a single-process DataStore crash.
- Back returns from an OAF document or spreadsheet to the originating 曲阳便签 editor.
- A DOCX edit was saved in place to the original attachment and the updated text was present in `word/document.xml`.
- An XLSX cell edit was saved in place to the original attachment and the updated value was present in `xl/sharedStrings.xml`.
- OAF receives the original attachment display name while reading and writing the host app's private file path.
- Ruled-paper lines now follow the same scroll offset as title and body text.
- Title, metadata and body line heights scale together with the configured app font size.
- GitHub Release parsing covers version-code ordering, APK selection and digest extraction.
- Online updates download to app cache, verify SHA-256 and signer compatibility before opening the system installer.
- Online update downloads have no total-duration cap; connection and stalled-stream timeouts remain enforced.

## Manual smoke test

- Cold launch and empty state.
- Create a note, title/body input, auto-save.
- Markdown mode and preview.
- Ruled-paper editor rendering.
- Fixed home search box.
- Editor undo and redo.
- Direct multi-file Markdown import.
- WebDAV configuration, connection test, Markdown upload, restore, and foreground sync.
- Nutstore root URL uses the dedicated subfolder and never appends it twice.
- Delete confirmation for notes, folders, and attachments.
- Single-file HTML export with inline icon and attachments.
- Force-stop and relaunch: note persisted.
- Search filters by title/body/tag.
- Folder creation and folder chip filtering.
- Star action and Starred view.
- Move to Trash, Trash view, and restore.
- Settings page opens; theme, dynamic color, font scale, PIN and backup controls render.
- No `FATAL EXCEPTION` observed for the app process.

Screenshots are stored in this directory. Test data is synthetic and contains no
real user notes.
