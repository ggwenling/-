/// <reference types="vite/client" />

/** 前台用到的 Vite 环境变量（与 Vue 版 .env.development 口径一致） */
interface ImportMetaEnv {
  /** 演示模式：与后端 `app.demo-mode=true` 对应，认证页展示"验证码打印到控制台"提示 */
  readonly VITE_DEMO_MODE?: string
  /** 可选：覆盖 WebSocket 地址（默认同源 `/ws`，开发期由 Vite 代理转发） */
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
