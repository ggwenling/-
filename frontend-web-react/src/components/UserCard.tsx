import { BadgeCheck, UserRound } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { creditLevel } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { SellerVO } from '@/api/types'

/**
 * 卖家 / 发布者信息卡（对齐 Vue 版 components/UserCard.vue）：
 * 头像 + 昵称 + 校园认证徽章 + 信用档位徽章，底部固定面交提醒文案。
 */
export function UserCard({
  user,
  className,
  showHint = true
}: {
  user?: SellerVO | null
  className?: string
  showHint?: boolean
}) {
  if (!user) return null
  const level = creditLevel(user.creditScore)

  return (
    <div className={cn('paper-card p-4', className)}>
      <div className="flex items-center gap-3">
        {user.avatar ? (
          <img src={user.avatar} alt="" className="size-12 rounded-full border border-border object-cover" />
        ) : (
          <span className="grid size-12 place-items-center rounded-full bg-secondary text-primary">
            <UserRound className="size-5" />
          </span>
        )}
        <div className="min-w-0">
          <div className="truncate text-[15px] font-semibold text-foreground">{user.nickname}</div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            {user.authStatus === 1 ? (
              <Badge variant="success" className="gap-1">
                <BadgeCheck className="size-3" />
                校园已认证
              </Badge>
            ) : (
              <Badge variant="soft">未认证</Badge>
            )}
            <Badge variant={level.tone === 'soft' ? 'soft' : level.tone}>信用 {level.text}</Badge>
          </div>
        </div>
      </div>

      {showHint ? (
        <p className="mt-3 border-t border-dashed border-border pt-3 text-[12px] leading-relaxed text-muted-foreground">
          面交前请在聊天中约好时间地点，见面验货后再确认（PRD §5.3）
        </p>
      ) : null}
    </div>
  )
}

export default UserCard
