# AGENTS.md

## 工具调用避坑规则（本机已验证复现，必须遵守）

本机 Codex 通过 CC Switch 代理走 DeepSeek 的 `/v1/responses` 接口，并行工具调用有硬性限制。

**规则：同一个回合里不要并行调用多个 `view_image`。**

原因：并行调用两个 `view_image` 时，Codex 会为每张被缩放的图插入一条 developer 消息
`<image_resize_notice>`，历史顺序会变成
`call A → call B → 输出A → notice → 输出B → notice`。
DeepSeek 要求并行工具调用的结果必须紧邻出现，中间插进这条消息后它匹配不上第二个调用，
直接返回 400：`No tool output found for tool call call_xxxx`。
更麻烦的是这个坏条目会永久留在会话历史里，之后每个请求都会带着它一起失败，
连 `/compact` 也救不了（压缩本身也要先发一次成功的请求），整个窗口彻底卡死。

具体做法：

1. 一个回合只调用一次 `view_image`。
2. 需要看多张图时，一张一张来，不要在同一轮里并行发多个 `view_image`。
3. 优先用智谱 MCP 的 `analyze_image` / `describe_image`：返回纯文本，不占历史，
   也不会触发这个问题。
4. 如果确实需要一轮看多张，先把图片缩到长边 2048px 以内再读
   （1080x2400 这类手机截图一定会被缩放，因此一定会产生 notice）。

遇到报错里出现 `No tool output found for tool call` 时：**不要重试，也不要 `/compact`，
直接停下告诉用户。**