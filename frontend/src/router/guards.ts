import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'

const WHITE_LIST = ['/login', '/404', '/403']
// 匿名分享浏览: /wiki/view/** 免登录
const WHITE_PREFIX = ['/wiki/view']

export function setupGuards(router: Router) {
  router.beforeEach((to, _from, next) => {
    const userStore = useUserStore()

    if (to.meta.title) {
      document.title = `${to.meta.title} - Jade Platform`
    }

    if (WHITE_LIST.includes(to.path) || WHITE_PREFIX.some((p) => to.path.startsWith(p))) {
      return next()
    }

    if (!userStore.isLoggedIn) {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    next()
  })
}
