# Testing notes

Tested on 2026-11-20 with:

- Android emulator API 37
- JDK 21
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- 曲阳便签 `0.11.20` (`versionCode` 11020)

## Automated checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Result on 2026-11-19:

- 92 unit tests pass with 0 failures and 0 errors.
- Lint: 0 errors, 127 warnings (mostly upstream OAF deprecation notices).
- Debug APK builds and installs.
- Release build passes R8 minification and produces a 34.9 MB unsigned APK.
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
- WebDAV address, username and password are persisted together and all three fields are
  prefilled when reopening the configuration dialog.
- WebDAV passwords use plaintext storage in app-private preferences as configured by the user;
  legacy encrypted passwords are migrated on first read and the old ciphertext/key are removed.
- A concrete WebDAV folder such as `/dav/biji/` is synced directly.
- Only the Jianguoyun `/dav/` root creates the dedicated `曲阳便签` child folder.
- Jianguoyun may return HTTP 400 for a missing child folder; the client creates it with MKCOL and retries PROPFIND.
- PROPFIND matches Obsidian's network request: Depth 1, no XML request body, Accept `text/plain,application/xml`.
- Nutstore Link-header pagination follows all `rel="next"` pages on the same server.
- Remote-imported files are not uploaded again during the same sync.
- Sync summary reports the actual number of Nutstore directory pages scanned.
- Local APK versionCode is 11020 for version 0.11.20.
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
- Back returns from an OAF document or spreadsheet directly to the note list.
- A DOCX edit was saved in place to the original attachment and the updated text was present in `word/document.xml`.
- An XLSX cell edit was saved in place to the original attachment and the updated value was present in `xl/sharedStrings.xml`.
- OAF receives the original attachment display name while reading and writing the host app's private file path.
- Ruled-paper lines now follow the same scroll offset as title and body text.
- Title, metadata and body line heights scale together with the configured app font size.
- GitHub Release parsing covers version-code ordering, APK selection and digest extraction.
- Online updates download to app cache, verify SHA-256 and signer compatibility before opening the system installer.
- Online update downloads have no total-duration cap; connection and stalled-stream timeouts remain enforced.
- The visible editor hides Markdown and HTML markers while preserving heading, centered, bullet,
  bold, quote and todo styling.
- Format toolbar actions replace the previous line format instead of stacking markers.
- Formatting actions normalize marker sequences like the previously corrupted
  `# <center>- **- [ ]` line into visible text with one selected format.
- Typing inside a formatted todo continues to show the checkbox without exposing `- [ ]`.
- Rich formatting survives leaving and reopening the note while the Markdown source remains
  available for search, sync and export.
- Opening a DOCX or XLSX note from the home list launches the embedded Office editor directly.
- Exiting the Office editor returns directly to the note list without reopening the document.
- Word and Excel top bars remain visible after the previous reading-mode auto-hide interval.
- The document title is the first ruled-paper line; folder, date, count and format controls sit in a
  separate fixed information strip above the paper.
- Pressing Enter in the title moves focus into the body without adding a line break to the title.
- Ruled-paper line spacing is derived from the configured font scale, so the rules remain aligned
  with title and body text at 80%, 100% and 150% sizes.
- The supplied Smartisan Notes launcher artwork is used for the package launcher and round icon.
- Launcher resources use a full-bleed adaptive icon instead of placing the artwork inside the
  system's legacy grey icon plate.
- The home header uses the reference wood title bar, centered category title, settings and compose
  actions without extra header menus.
- The home search field uses a compact 26dp white capsule.
- Home note cards use the reference cream paper colors, 10dp radius, 52dp content inset, relative
  timestamps and persistent pin/star controls.
- Note cards expose only the left half of the reference clip artwork and keep actions available
  through long press.
- The paperclip is rendered above the card layer so its overlapping half remains recognisable.
- The launcher package resolves to the supplied Smartisan Notes icon for every density bucket.
- Relative timestamps render as `今天`, Chinese weekday, day count and year-count formats.
- Long body editing keeps the active caret line above the IME and fixed format toolbar.
- The editor title bar uses the reference wood toolbar with undo, redo, image insertion,
  completion and overflow actions.
- The editor information strip, 42dp ruled-paper lines and 16sp/18sp typography follow the
  reference dimensions while preserving title-first editing.
- Title and body glyphs are vertically centered inside each ruled-paper line cell at every
  supported font scale.
- Settings uses the reference wood background with grouped light list rows.
- Note paper colour can be selected as light, default, dark or darkest, and the editor updates
  immediately without leaving the screen.
- Synchronization is silent: automatic and manual sync do not show floating, inline or snackbar
  status messages.
- The home note list prepares timestamps and image indicators once per data change, defers
  long-press menus until opened, and draws each paper card without per-card shadow layers.
- Settings groups use direct canvas drawing instead of clipped shadow surfaces.
- The wooden background is installed as the window background so scrolling does not repaint the
  full image in Compose.
- Home cards draw their paper, margin, divider and border without creating paths or clips on
  every frame.
- The editor title bar keeps always-visible “撤销” and “重做” text buttons.
- Undo becomes enabled after editing, restores the previous title/body, and enables redo.
- WebDAV synchronization emits started, successful and failed status states for both automatic and
  manual synchronization.
- The reference APK's highest-resolution `560dpi` launcher icon was copied for the installed app
  icon and reused for the in-app share/export brand image.
- The image editor supports up to 9 embedded images, multi-image selection, camera capture,
  two-line captions, crop boundaries, full-screen swiping preview, gallery save,
  drag/step reordering and deletion.
- Inserting an image captures the active body cursor and splits the document at that position,
  so text remains before and after the image in document order.
- Embedded images render as white floating paper cards with rounded corners and a soft shadow
  above the ruled lines, matching the reference image treatment.
- Ruled-paper horizontal lines use higher contrast and a slightly stronger stroke for better
  separation while editing.
- Selected images expose the reference operation row below the image, including delete,
  description, save, preview, crop and drag-order controls.
- Attachment captions and display order are persisted and included in encrypted backups.
- Room migration 2→3 adds attachment captions without deleting existing notes or files.
- The insertion sheet exposes “从相册选取” and “拍摄照片” directly, matching the reference flow.
- The insertion sheet uses the reference title bar and two 48dp grey actions without an extra
  attachment action.
- Gallery selection opens a full-screen black picker with three-column thumbnails, a `0/9`
  selection counter and the reference title/buttons.
- Tapping an image enters its operation state with circular delete, description, save, preview
  and crop controls plus a top-right drag handle.
- Cropping uses the visible image bounds rather than letterboxed black bars.
- Saving to the gallery preserves the original PNG/JPEG/WebP extension and byte format.
- Long-image sharing renders embedded images and captions in their document positions.
- Horizontal ruled-paper lines are drawn inside the scrollable document layer, so title, body,
  wrapped lines and paper rules move together at every scroll position.

## Manual smoke test

- Cold launch and empty state.
- Create a note, title/body input, auto-save.
- Markdown mode and preview.
- Ruled-paper editor rendering.
- Fixed home search box.
- Editor undo and redo.
- Direct multi-file Markdown import.
- Unified file import converts Markdown/TXT into notes and stores DOCX, XLSX, PDF and image
  files as attachment notes.
- Imported PDF and DOCX files open in the embedded OAF viewers.
- Imported binary files are registered for WebDAV sync without creating redundant Markdown
  mirrors.
- WebDAV configuration, connection test, Markdown upload, restore, and foreground sync.
- Nutstore root URL uses the dedicated subfolder and never appends it twice.
- Delete confirmation for notes, folders, and attachments.
- Single-file HTML export with inline icon and attachments.
- Force-stop and relaunch: note persisted.
- Search filters by title/body/tag.
- Home list matches the reference wood background, toolbar controls, search capsule and card spacing.
- Pin/star controls are independently tappable while card long press opens the note actions.
- Folder creation and folder chip filtering.
- Star action and Starred view.
- Move to Trash, Trash view, and restore.
- Settings page opens; theme, dynamic color, font scale, PIN and backup controls render.
- No `FATAL EXCEPTION` observed for the app process.

Screenshots are stored in this directory. Test data is synthetic and contains no
real user notes.
