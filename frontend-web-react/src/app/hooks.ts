import { useDispatch, useSelector } from 'react-redux'
import type { AppDispatch, RootState } from './store'

/** 类型化 hooks（避免每个组件重复标注 RootState / AppDispatch） */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()
