# 曲阳便签 Windows

曲阳便签 Windows 是 Android 版的本地优先桌面客户端，采用 Electron + 原生 HTML/CSS/JavaScript 实现。

## 功能

- 全部便签、加星便签、文件夹和回收站
- 标题、正文搜索，置顶、加星和自动保存
- 信纸式编辑器与撤销/重做
- 标题、居中、列表、粗体、引用和 7 色文字
- 光标位置插入原图并按正文宽度显示
- 回收站恢复与彻底删除
- WebDAV 明文密码配置、连接测试、手动同步与每 30 秒自动双向同步
- 单条便签导出 HTML
- 全部便签数据导入/导出 JSON
- 浅色、深色和跟随系统主题
- Windows 键盘快捷键和原生编辑菜单

## 开发运行

```powershell
cd windows-client
npm install
npm start
```

## 构建 Windows 便携版

```powershell
cd windows-client
npm install
npm run build:win
```

构建结果位于 `windows-client/dist/`。

## 数据位置

便签数据和图片保存在 Electron 的 Windows 用户数据目录中，卸载便携版不会主动删除这些数据。可在“设置”中导出全部便签数据进行备份。
