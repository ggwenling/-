import { http } from './request'
import { store } from '@/app/store'
import { clearCredentials } from '@/features/auth/authSlice'
import { showBannedDialog } from '@/components/BannedDialog'
import { AUTH_EXPIRED_CODES, CODE_BANNED, CODE_RESTRICTED } from '@/lib/constants'
import { toast } from 'sonner'
import type { Result, UploadVO } from './types'

/**
 * 图片上传（契约见 docs/contracts-frontend-web.md §4）。
 *
 * 与 Vue 版一致：`POST /api/uploads`，form-data 字段名固定 **`file`**，需 `Authorization: Bearer`。
 * 注意：本函数**不走 RTK Query**，因此这里手动复刻 baseQuery 的错误码语义，
 * 保证上传失败时的提示与其它接口一致（旧版由全局 axios 拦截器统一处理）。
 *
 * 差异说明：Vue 版手写了 `Content-Type: multipart/form-data`（无 boundary）；
 * 这里交给 axios 依据 FormData 自动生成 boundary（行为等价且更稳）。
 */
export async function uploadImage(file: File): Promise<UploadVO> {
  const form = new FormData()
  form.append('file', file)

  const res = await http.post('/uploads', form)
  const body = res.data as Result<UploadVO>

  if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
    if (AUTH_EXPIRED_CODES.includes(body.code)) {
      store.dispatch(clearCredentials())
      toast.error(body.message || '登录已过期，请重新登录')
    } else if (body.code === CODE_BANNED) {
      showBannedDialog(body.message)
    } else if (body.code === CODE_RESTRICTED) {
      toast.warning(`${body.message || '信用分受限'}（完成订单可获得信用分，恢复至 60 分后自动解除）`)
    } else {
      toast.error(body.message || '图片上传失败')
    }
    throw new Error(body.message || '图片上传失败')
  }

  return body.data
}
