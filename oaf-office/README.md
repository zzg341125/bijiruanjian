# OAF Office editor

曲阳便签使用 OAF 作为内置的 DOCX/XLSX 查看与编辑引擎。Office 文件通过应用内
`MainActivity` 打开，不依赖 WPS Office、Microsoft 365 或浏览器，也不会把文件上传到
第三方 Office 服务。

- 上游项目：Open Any File (OAF)
- 上游提交：`42b8af700d252ae9d607ef496f388971bb330d88`
- 许可证：Apache License 2.0，全文见 `LICENSE-OAF.txt`

为嵌入宿主应用做了以下修改：

- 只保留内部 `com.prempoddar.oaf.MainActivity`，并设为 `exported=false`。
- OAF 设置改用 `oaf_settings.preferences_pb`，避免与宿主应用的 DataStore 冲突。
- 支持把附件原始的显示名称传入编辑器，同时仍直接读写宿主保存的真实文件。
- XLSX 保存先在内存生成完整工作簿，再覆盖原文件，避免截断后无法继续读取。
- 使用宿主统一的应用包名和签名，不包含 OAF 自身的外部安装、更新入口。
