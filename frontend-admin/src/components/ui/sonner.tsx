import { Toaster as SonnerToaster, type ToasterProps } from 'sonner'

/**
 * 全局 toast（替代 Element Plus ElMessage）。
 * 复古学院风：纸白底 + 米褐描边 + 墨绿描线。
 */
function Toaster(props: ToasterProps) {
  return (
    <SonnerToaster
      position="top-center"
      richColors
      closeButton
      toastOptions={{
        classNames: {
          toast:
            'group toast !rounded-xl !border-[#e4dbc9] !bg-[#fffcf5] !text-[#253028] !shadow-[0_8px_20px_-12px_rgba(46,91,78,0.28)]',
          description: '!text-[#6e7a70]',
          actionButton: '!bg-[#2e5b4e] !text-[#fbf7ef]',
          cancelButton: '!bg-[#ece4d4] !text-[#2e5b4e]'
        }
      }}
      {...props}
    />
  )
}

export { Toaster }
