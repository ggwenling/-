import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** shadcn 约定的类名合并工具：clsx 组合 + tailwind-merge 去冲突 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
