import { defineStore } from 'pinia'

/**
 * Placeholder global store. Real stores (profile, schedule, clock-ins...)
 * will replace this as features land.
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    appName: 'TeDeben' as const,
  }),
})
