# Testing notes

Tested on 2026-11-08 with:

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

Result:

- Unit tests pass.
- Lint: 0 errors.
- Debug APK builds and installs.
- Release build passes R8 minification and produces an unsigned APK.

## Manual smoke test

- Cold launch and empty state.
- Create a note, title/body input, auto-save.
- Markdown mode and preview.
- Ruled-paper editor rendering.
- Fixed home search box.
- Editor undo and redo.
- Direct multi-file Markdown import.
- WebDAV configuration, connection test, Markdown upload, restore, and foreground sync.
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
