# 挂号咨询助手（前端）

Vue 3 对话页。版式参考 [chatgpt-web](https://github.com/Chanzhaoyu/chatgpt-web)（MIT），接口接本仓库咨询服务。

## 启动

先启动后端 `consult-registration-agent-service`（默认 `18081`），再：

```bash
cd consult-registration-web
npm install
npm run dev
```

浏览器打开 `http://127.0.0.1:5173`。开发时 `/api` 由 Vite 转到 `18081`。

## 会调的接口

- `POST /api/consultations/messages`
- `PATCH /api/consultations/card`
- `POST /api/consultations/confirmation`

`status` 为 3 / 6 / 7 / 8 或未知码时，只展示提示、不展示卡片和确认按钮。确认后仍可发送病情变化。
