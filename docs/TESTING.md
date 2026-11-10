# Testing notes

Tested on 2026-11-10 with:

- Android emulator API 37
- JDK 21
- Gradle 8.9
- Android Gradle Plugin 8.7.3

## Automated checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Result on 2026-11-10:

- 60 unit tests pass with 0 failures and 0 errors.
- Lint: 0 errors, 32 existing launcher-icon warnings.
- Debug APK builds and installs.
- Release build passes R8 minification and produces an unsigned APK.
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
- Local debug APK versionCode is 7006 so installing 0.7.6 must replace older builds.
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
- DOCX/XLSX use the embedded MIT-licensed Weave Office engine on arm64-v8a.
- The merged debug APK includes `WeaveOfficeActivity` and its four native libraries.

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
