# Feature parity

The business logic is an independent implementation. The current launcher icon,
home background, title bar and note-card visual assets are reproduced from a
user-supplied Smartisan Notes APK for the requested local UI migration. The app
does not include the reference application's business code, advertising SDKs,
account services or paid backend behavior.

## Implemented in v0.1

| Capability | 曲阳便签 |
| --- | --- |
| Text notes | Yes |
| Markdown mode and preview | Yes |
| Checklist notes | Yes |
| Folders | Yes |
| Tags | Yes |
| Search | Yes |
| Starred notes | Yes |
| Pin notes | Yes |
| Trash and restore | Yes |
| Auto-save | Yes |
| Image/file attachments | Yes, with up to 9 embedded images, captions, crop, full-screen preview, gallery save and drag reordering |
| Reminders | Yes |
| Share as text | Yes |
| Share as long image | Yes, including embedded images and captions |
| Application PIN lock | Yes |
| Theme, dynamic color, font scale | Yes |
| Markdown note files | One `note-ID.md` per note |
| WebDAV online backup and restore | Yes, Obsidian-style three-way sync |
| Direct file import | Yes, including Markdown, TXT, DOCX, XLSX, PDF and images |
| Ruled paper editor | Yes, rules scroll with document content |
| Title-first document flow | Yes, Enter moves from the title into the body |
| Fixed home search | Yes |
| Paperclip home list | Yes, Smartisan-style wood background, title bar, half-exposed clip cards, relative timestamps and persistent pin/star actions |
| Undo and redo history | Yes |
| Visible rich-text formatting toolbar | Yes, Markdown remains hidden in the editor |
| Self-contained HTML export | Yes |
| Encrypted full backup and merge import | Yes, AES-256-GCM `.qbak` |
| Direct embedded Office opening | Yes, DOCX/XLSX notes open in OAF immediately |
| Office exit destination | Yes, returns directly to the note list |
| Keyboard-safe long editing | Yes, the active line scrolls above the keyboard |
| Sync status feedback | Yes, syncing, success and failure banners |
| Incoming text share | Yes |
| App shortcuts | Yes |

## Deliberately different

- No advertisements.
- No analytics or tracking SDK.
- No proprietary account or payment integration.
- No copied model, artwork, sounds, templates, or service credentials.
- Local knowledge search instead of a locked cloud RAG service.

## Next stages

- Optional user-configured AI provider.
- Home-screen widget.
- Manual drag ordering.
