import axios from 'axios'
import { getToken } from '@/features/auth/authSlice'
import type { Result, UploadVO } from './types'

/**
 * 图片上传（PRD ADM-07 轮播图）：走完整路径 /api/uploads（无 /api/admin 前缀），
 * 手动携带管理端 JWT。jpg/png/webp ≤5MB。
 */
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE = 5 * 1024 * 1024

/** 上传前校验，返回错误提示文案；通过返回 null */
export function validateImage(file: File): string | null {
  if (!ALLOWED_TYPES.includes(file.type)) return '仅支持 jpg/png/webp 图片'
  if (file.size > MAX_SIZE) return '图片不能超过 5MB'
  return null
}

export async function uploadImage(file: File): Promise<UploadVO> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await axios.post<Result<UploadVO>>('/api/uploads', form, {
    headers: { Authorization: `Bearer ${getToken()}` },
    timeout: 20000
  })
  if (data.code !== 0) {
    throw new Error(data.message || '图片上传失败')
  }
  return data.data
}
