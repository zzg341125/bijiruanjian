# Feature parity

This is a clean-room implementation. It does not contain decompiled application
code, copied artwork, original application resources, branding, advertising SDKs,
account services, or paid backend behavior.

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
| Image/file attachments | Yes |
| Reminders | Yes |
| Share as text | Yes |
| Share as long image | Yes |
| Application PIN lock | Yes |
| Theme, dynamic color, font scale | Yes |
| Markdown note files | One `note-ID.md` per note |
| WebDAV online backup and restore | Yes, Obsidian-style three-way sync |
| Direct Markdown import | Yes |
| Ruled paper editor | Yes, rules scroll with document content |
| Title-first document flow | Yes, Enter moves from the title into the body |
| Fixed home search | Yes |
| Paperclip home list | Yes, half-exposed clip with persistent pin/star actions |
| Undo and redo history | Yes |
| Visible rich-text formatting toolbar | Yes, Markdown remains hidden in the editor |
| Self-contained HTML export | Yes |
| Encrypted full backup and merge import | Yes, AES-256-GCM `.qbak` |
| Direct embedded Office opening | Yes, DOCX/XLSX notes open in OAF immediately |
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
- PDF and DOCX import.
- Manual drag ordering.
