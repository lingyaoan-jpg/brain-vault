# 脑内收容所（Brain Containment）

一个为个人日常使用打造的 Android 思想记录应用：像备忘录一样随手写下想法，AI 在后台自动整理归类，不需要手动选分类。

## 核心设计

- **原始文字与数据安全优先**：用户原文不被 AI 改写、合并或删除，AI 只负责自动打标签、分类。
- **免分类记录**：写的时候不用选分类，AI 在后台自动整理。
- **收容 / 收容所双页结构**：首页只有一个「去收容」按钮；「收容所」按内容类型分方块卡片展示全部记录。
- **自带 DeepSeek API 对接**：在 App 设置里填写自己的 DeepSeek API Key，密钥用 Android Keystore 加密保存，不写入源码、不上传。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room（SQLite）本地存储
- Hilt 依赖注入
- DataStore / SharedPreferences（加密）
- NanoHTTPD 局域网管理端（可选）
- DeepSeek API（内容分类与整理）

## 构建

```bash
# 需要 Android SDK（在 local.properties 中配置 sdk.dir）
./gradlew.bat assembleDebug
```

调试 APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 说明

- 本项目是个人自用应用，功能按个人习惯定制。
- 需求文档与开发计划见 `docs/`。
