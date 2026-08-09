# AI 思想仓库 V1-A 技术方案与开发计划

> 依据：《AI 思想仓库 V1 产品需求文档》(V1 需求已确认)
> 状态：待用户确认后进入编码
> 范围：V1-A（可日常使用的核心版本），V1-B 仅做结构预留

---

## 0. 总体架构

```
┌─────────────────────────────┐        HTTPS（LAN 或 VPS）        ┌──────────────────────────┐
│  Android App（红米 K70）      │ ───────────────────────────────► │  私有网关服务端（单用户）    │
│  Kotlin + Jetpack Compose    │                                  │  Node.js 轻量服务           │
│  · Room/SQLite 本地数据库      │ ◄─────────────────────────────── │  · 仅保存 DeepSeek 密钥     │
│  · FTS5 全文索引 + 本地语义向量 │          仅元数据/整理结果         │  · AI 整理/导出/深度分析代理  │
│  · WorkManager 后台队列        │                                  │  · 日志不含原文与密钥        │
│  · 语音转写 / 草稿 / 备份      │                                  └────────────┬─────────────┘
└─────────────────────────────┘                                               │ HTTPS
                                                                   ┌──────────▼──────────┐
                                                                   │   DeepSeek API       │
                                                                   │   （用户自有密钥）      │
                                                                   └─────────────────────┘
```

核心原则落实：
- **本地优先**：记录、草稿、评论、搜索、浏览全部离线可用；保存成功不依赖 AI。
- **密钥不出客户端**：DeepSeek 密钥只存在于网关服务端的环境变量/受限配置文件中。
- **原文不可变**：AI 只产出标题、分类、标签、关联等元数据，任何环节不改写、不合并原文。
- **分析授权制**：深度分析（V1-B）只读取用户明确选择的范围。

---

## 1. 技术选型

| 部分 | 选型 | 理由 |
| --- | --- | --- |
| Android 语言/UI | Kotlin 2.x + Jetpack Compose + Material3 | PRD 推荐；单用户 App 开发效率高 |
| 本地数据库 | Room（`androidx.sqlite:sqlite-bundled`） | 稳定结构化存储；bundled SQLite 自带较新 FTS5，支持中文 trigram 分词 |
| 全文搜索 | FTS5 trigram + LIKE 兜底 | 中文无需额外分词器；2 字以内查询用 LIKE |
| 语义搜索 | 本地 ONNX Runtime Mobile + bge-small-zh 嵌入模型 | 离线可用、隐私最好；模型约 25–95MB，首次下载一次 |
| 后台任务 | WorkManager | 断网重连、重试、定时清理回收站、定时补跑失败任务 |
| 网络 | Retrofit/OkHttp + HTTPS + 个人访问令牌 | 与网关通信 |
| 本地设置 | DataStore | 网关地址、令牌、偏好 |
| 语音 | Android 系统 SpeechRecognizer | 零配置；失败保留页面可重试（见决策点 D2） |
| 网关服务端 | Node.js（Hono/Express）单进程轻量服务 | 易于在用户 Windows 电脑上运行；密钥放 `.env`/环境变量 |
| 导出 | 复制文本 / `.txt`/`.md` 本地生成；Word/长图由网关生成（联网） | App 轻量；Word 用服务端 docx 库 |
| 备份 | 完整数据导出为单个压缩文件（JSON 快照），本地滚动保留 7 份 | 见 §6 |

最低支持：minSdk 26（Android 8.0），targetSdk 35。红米 K70（Android 14 / HyperOS）可直接安装。
分发方式：签名 APK 直接安装（不做应用商店上架）。

---

## 2. 数据结构（Room 数据库 Schema v1）

### 2.1 核心表

**records（记录，原文唯一权威存放地）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | UUID |
| content | TEXT NOT NULL | 原文，AI 永不改写 |
| title | TEXT | AI 生成或人工标题 |
| title_source | TEXT DEFAULT 'ai' | `ai` / `manual`；人工标题后 AI 不覆盖 |
| created_at | INTEGER NOT NULL | 首次记录时间（ms），永不改变 |
| updated_at | INTEGER NOT NULL | 内部修改时间（ms），不展示 |
| status | TEXT DEFAULT 'pending' | `pending`/`processing`/`organized`/`failed` |
| is_favorite | INTEGER DEFAULT 0 | 仅用户可改 |
| is_pinned | INTEGER DEFAULT 0 | 仅用户可改 |
| deleted_at | INTEGER NULL | 非空=在回收站；30 天后清理 |
| ai_attempts | INTEGER DEFAULT 0 | AI 重试次数 |
| ai_error | TEXT | 最近一次 AI 错误摘要（不含原文） |
| ai_processed_at | INTEGER NULL | AI 整理完成时间 |

**drafts（实时草稿）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| content | TEXT NOT NULL | 输入中的文字（含语音转写中间态） |
| created_at | INTEGER | 草稿创建时间 |
| updated_at | INTEGER | 每次自动保存更新时间 |

**comments（评论与批注）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| record_id | TEXT FK→records ON DELETE CASCADE | |
| content | TEXT NOT NULL | 评论不覆盖原文 |
| created_at | INTEGER NOT NULL | 独立添加时间 |

**categories（分类维度取值）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| dimension | TEXT NOT NULL | `type`/`topic`/`urgency`/`importance`/`emotion`/`tag` |
| name | TEXT NOT NULL | 展示名 |
| created_by | TEXT DEFAULT 'ai' | `seed`/`ai`/`user` |
| is_active | INTEGER DEFAULT 1 | 用户停用/删除用 |
| sort_order | INTEGER DEFAULT 0 | |

UNIQUE(dimension, name)。种子数据见 §2.3。

**record_categories（记录↔分类多对多）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| record_id | TEXT FK ON DELETE CASCADE | |
| category_id | TEXT FK ON DELETE CASCADE | |
| source | TEXT DEFAULT 'ai' | `ai`/`user`：用户手动改动的来源标记 |
| created_at | INTEGER | |

PK(record_id, category_id)。同一条记录可同时挂多个维度、多个标签。

**ai_feedback（个性化学习信号，V1-A 先记录，V1-B 用于学习）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| record_id | TEXT FK | |
| category_id | TEXT | |
| action | TEXT | `add_category`/`remove_category`/`retitle`/`change_category` |
| old_value / new_value | TEXT | 修改前后值 |
| created_at | INTEGER | |

**record_links（记录关联建议）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| from_record_id / to_record_id | TEXT FK | 双向记录 |
| reason | TEXT | 关联原因（AI 生成） |
| source | TEXT DEFAULT 'ai' | |
| created_at | INTEGER | |

UNIQUE(from, to)。

**ai_jobs（AI 整理队列）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | |
| record_id | TEXT FK | |
| job_type | TEXT DEFAULT 'organize' | 预留 `embed`、`analyze` |
| status | TEXT DEFAULT 'queued' | `queued`/`running`/`done`/`failed` |
| attempts | INTEGER DEFAULT 0 | 上限 5 次，指数退避 |
| next_retry_at | INTEGER | |
| last_error | TEXT | 不含原文 |
| created_at / updated_at | INTEGER | |

**embeddings（语义向量，本地）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| record_id | TEXT PK FK | |
| model | TEXT | 模型标识 |
| vector | BLOB | 浮点向量 |
| updated_at | INTEGER | |

### 2.2 搜索索引（FTS5 虚拟表）

```
CREATE VIRTUAL TABLE records_fts USING fts5(
  record_id UNINDEXED, content, title, comments, category_names,
  tokenize = 'trigram'
);
```
- 由 records/comments/record_categories 变更触发器增量同步。
- 短查询（1–2 字）回退 `LIKE '%kw%'`。
- 语义搜索：查询向量与 embeddings 余弦相似度 top-N，与 FTS 结果用 RRF 融合排序。

### 2.3 V1-B 预留（本轮只建结构，不实现）

- **analysis_sessions / analysis_messages**：多会话深度分析，含 scope_json（课题/标签/时间范围/记录 ID 列表）与引用片段 citations_json。
- **change_log**：实体级增量日志（entity/id/op/ts/payload），为 V1-B 手机↔管理端同步预留。
- **settings（DataStore）**：gateway_url、gateway_token、embedding_model 等。

### 2.4 种子分类数据（按 PRD 4.3）

- type：灵感/念头、观点、感受/情绪、事件记录、分析/反思、文章/散文、待处理事项、碎碎念/吐槽
- topic：亲密关系、自我认知、自我成长、原生家庭
- urgency：立即、近期、稍后、**无时限（默认）**
- importance：核心、重要、一般、随想
- emotion：开心、焦虑、愤怒、委屈、疲惫、平静、矛盾（无情绪不强加）
- tag：自由扩展

---

## 3. 记录状态机

```
草稿(drafts) ──确认保存──► 已保存/待整理(pending)
                              │ WorkManager 队列(联网自动)
                              ▼
                        AI 整理中(processing)
                              ├─成功─► 已整理(organized)
                              └─失败─► 整理失败(failed) ──重试──► processing
任何状态 ──删除──► 回收站(deleted_at 非空, 保留30天)
回收站 ──恢复──► 回到删除前状态(分类/标签/评论/关联级联恢复)
回收站 ──永久删除(二次确认)──► 级联删除
```

- 编辑正文：直接改 content，`created_at` 不变、无历史版本、不显示修改时间。
- 手动改标题/分类：立即生效并写 `ai_feedback`；人工标题标记后 AI 不再覆盖（A05）。

---

## 4. AI 整理协议（网关 ↔ DeepSeek）

### 4.1 请求（POST /api/organize）

```json
{
  "record_id": "uuid",
  "content": "当前记录原文",
  "title": "现有标题(若有, 人工标题则跳过标题生成)",
  "existing_types": ["灵感/念头", "观点", "..."],
  "existing_topics": ["亲密关系", "..."]
}
```

只发送当前记录 + 必要的少量分类上下文，符合 PRD §9。

### 4.2 响应（强制 JSON Schema）

```json
{
  "title": "短标题(≤12字, 不得改写原文)",
  "types": ["观点"],
  "topics": ["自我认知"],
  "urgency": "无时限",
  "importance": "一般",
  "emotions": ["平静"],
  "tags": ["沟通", "工作"],
  "related_record_ids": ["uuid..."],
  "note": "一句可忽略的说明(可选)"
}
```

- 服务端校验：字段合法性、枚举属于现有分类或允许新增、`title` 与 `content` 不得相同长度/大段重合（防改写）。
- `response_format = json_object`；解析失败记 `failed` 并重试，不影响原文。
- 超时/余额不足：`failed` 状态 + 后台退避重试（最多 5 次，之后每日补跑）。

### 4.3 网关服务端要求

- 单用户：个人访问令牌校验；局域网默认绑定内网地址，VPS 部署时强制 HTTPS。
- 密钥：仅存 `.env` 或环境变量，不进仓库、不进 App。
- 日志：只记时间戳、状态码、耗时、记录 ID；不记原文、密钥、完整请求体。

---

## 5. V1-A 功能模块与开发步骤（按风险排序）

| 阶段 | 内容 | 对应验收 |
| --- | --- | --- |
| P0 工程基础 | Gradle 项目、Compose、Room+迁移框架、种子数据、签名、红米 K70 装机验证 | A19(升级安全基础) |
| P1 记录与草稿 | 记录页（零必填）、实时草稿自动保存与恢复、保存→记录、列表/时间线、详情、正文编辑、评论批注 | A01 A08 A09 |
| P2 离线优先 | ai_jobs + WorkManager 队列、断网保存提示、联网自动入队、状态展示与重试 | A03 |
| P3 网关+AI 整理 | 网关服务端、/api/organize、元数据落库、人工标题/分类保护 | A04 A05 A06 |
| P4 多维首页 | 分类首页各维度摘要区、同记录多维度、筛选排序（紧急程度无通知） | A11 |
| P5 搜索 | FTS5 全文 + 短词 LIKE + 本地语义向量，结果页筛选与命中片段高亮 | A10 |
| P6 收藏/置顶/回收站 | 收藏入口、置顶排序、回收站 30 天自动清理、二次确认、级联恢复 | A14 A15 |
| P7 语音输入 | 系统 SpeechRecognizer 转写→可编辑→确认保存，失败保留页面重试 | A02 |
| P8 导出与备份 | 单条复制文本/.txt/.md；Word/长图（网关生成）；完整备份滚动 7 份、恢复回滚、迁移前自动备份 | A16 A18 A19 |
| P9 桌面快捷入口 | 长按图标→文字记录/语音记录（ShortcutManager） | V1-A 清单第 9 项 |
| P10 V1-A 整体验收 | 真机逐条跑 V1-A 覆盖的验收项，输出测试报告 | §7 |

每个阶段完成即按验收清单自测并报告结果（遵循你的要求 7）。

### V1-B 边界（本轮不做，仅预留）
历史分类调整建议(A07)、重复主题提示(A12)、多会话深度分析(A13)、电脑管理端与批量导入(A17)、同步(A20)、批量修改/导出、AI 个性化学习闭环。

---

## 6. 备份与升级安全

- **完整备份内容**：records、comments、record_categories、categories、record_links、ai_feedback、analysis 会话（V1-B 后）、settings。
- **格式**：单个 `.zip`（含 schema 版本号 + JSON 快照 + 校验和），支持导出到用户选择目录。
- **滚动保留**：本地自动保留最近 7 份。
- **恢复**：恢复前先备份现有数据；恢复过程失败自动回滚，不破坏现有可用数据（A18）。
- **升级**：Room 迁移脚本；每次数据库升级前自动做一次本地备份；`sqlite-bundled` 保证跨版本行为一致（A19）。

---

## 7. V1-A 验收映射表

| 编号 | 验收项 | 归属 | 阶段 |
| --- | --- | --- | --- |
| A01 | 退出后草稿可恢复 | V1-A | P1 |
| A02 | 语音→可编辑转写→只存文字 | V1-A | P7 |
| A03 | 断网保存→待整理→联网自动整理 | V1-A | P2/P3 |
| A04 | AI 生成标题并多维度分类，重启保持 | V1-A | P3/P4 |
| A05 | 人工标题不被 AI 覆盖 | V1-A | P3 |
| A06 | 手动改分类立即生效并成为学习信号 | V1-A | P3 |
| A07 | 历史分类调整建议 | V1-B | — |
| A08 | 编辑正文不改首记时间、无历史版本 | V1-A | P1 |
| A09 | 评论批注按时间展示、可搜索 | V1-A | P1/P5 |
| A10 | 语义搜索返回命中原文预览 | V1-A | P5 |
| A11 | 同记录多维度展示，重启保持 | V1-A | P4 |
| A12/A13 | 深度分析相关 | V1-B | — |
| A14 | 回收站 30 天级联恢复 | V1-A | P6 |
| A15 | 收藏/置顶即时生效，AI 不干预 | V1-A | P6 |
| A16 | 单条导出（文本先行，Word/图片后置） | V1-A | P8 |
| A17 | 电脑端批量导入 | V1-B | — |
| A18 | 完整备份与恢复 | V1-A | P8 |
| A19 | 升级不丢数据 | V1-A | P0/P8 |
| A20 | 同步冲突保护 | V1-B | — |

---

## 8. 需要你确认的决策点（均为默认建议，可整体确认或逐条修改）

- **D1 网关部署位置**（影响最大）：默认跑在你家电脑上（局域网），手机在家时 AI 整理，不在家时先保存、回家自动补整理；如你希望随时随地整理，需一台 VPS + 域名（成本约几十元/月）。
- **D2 语音识别**：默认用红米 K70 系统自带语音识别（零配置）；若实测不可用，备选在网关接入第三方 ASR（需你提供对应服务 key）。
- **D3 语义搜索模型**：默认本地 ONNX `bge-small-zh`（约 25–95MB，首次联网下载一次，之后离线可用）；也可改为 V1-A 先只做关键词搜索、语义搜索延后。
- **D4 导出优先级**：默认 V1-A 先做“复制纯文本 + 导出 .txt/.md”，Word/长图由网关生成放 V1-A 后期。
- **D5 开发节奏**：P0–P10 按阶段交付并逐阶段自测；每完成一个阶段我会按验收清单测试并报告，你确认后再进入下一阶段。

---

## 9. 下一步

你确认本方案（或指出要调整的决策点）后，我从 P0 开始搭建：初始化 Android 工程、数据库 Schema 与迁移、签名与装机验证，随后进入 P1。
