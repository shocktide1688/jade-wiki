import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { setupGuards } from './guards'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/login/index.vue'),
    meta: { layout: 'blank', title: '登录' },
  },
  {
    // 匿名分享浏览（ShowDoc 式分享, 免登录）
    path: '/wiki/view/:itemId',
    name: 'WikiView',
    component: () => import('@/pages/wiki/view/index.vue'),
    meta: { title: '文档浏览', layout: 'blank' },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '首页', icon: 'House' },
      },
      // ===== 系统管理 =====
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/pages/system/user.vue'),
        meta: { title: '用户管理', icon: 'User' },
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/pages/system/role.vue'),
        meta: { title: '角色管理', icon: 'UserFilled' },
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('@/pages/system/menu.vue'),
        meta: { title: '菜单管理', icon: 'Menu' },
      },
      {
        path: 'system/dept',
        name: 'SystemDept',
        component: () => import('@/pages/system/dept.vue'),
        meta: { title: '部门管理', icon: 'OfficeBuilding' },
      },
      {
        path: 'system/dict',
        name: 'SystemDict',
        component: () => import('@/pages/system/dict.vue'),
        meta: { title: '字典管理', icon: 'Reading' },
      },
      {
        path: 'system/notice',
        name: 'SystemNotice',
        component: () => import('@/pages/system/notice.vue'),
        meta: { title: '通知公告', icon: 'Bell' },
      },
      // ===== 系统管理-配置 =====
      {
        path: 'system/mail',
        name: 'SystemMail',
        component: () => import('@/pages/system/mail/index.vue'),
        meta: { title: '邮件配置', icon: 'Message' },
      },
      {
        path: 'system/ldap',
        name: 'SystemLdap',
        component: () => import('@/pages/system/ldap/index.vue'),
        meta: { title: 'LDAP 配置', icon: 'Key' },
      },
      // ===== 系统监控 =====
      {
        path: 'monitor/operlog',
        name: 'MonitorOperLog',
        component: () => import('@/pages/monitor/operlog.vue'),
        meta: { title: '操作日志', icon: 'Document' },
      },
      {
        path: 'monitor/loginlog',
        name: 'MonitorLoginLog',
        component: () => import('@/pages/monitor/loginlog.vue'),
        meta: { title: '登录日志', icon: 'Key' },
      },
      // ===== 个人中心 =====
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/pages/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserFilled', hidden: true },
      },
      // ===== 文档中心 (Wiki) =====
      {
        path: 'wiki/items',
        name: 'WikiItems',
        component: () => import('@/pages/wiki/items/index.vue'),
        meta: { title: '我的项目', icon: 'Notebook' },
      },
      {
        path: 'wiki/workbench',
        name: 'WikiWorkbench',
        component: () => import('@/pages/wiki/workbench/index.vue'),
        meta: { title: '文档编辑器', icon: 'EditPen', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/error/404.vue'),
    meta: { layout: 'blank', title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupGuards(router)

/** 收集所有已注册路由的完整路径（用于过滤后端菜单里没实现的） */
export const REGISTERED_PATHS = new Set<string>(['/dashboard', '/login'])
/** meta.hidden 的路由: 可访问但不进侧边栏 (如工作台经项目卡片进入, 个人中心经头像下拉进入) */
export const HIDDEN_PATHS = new Set<string>()
function collectPaths(rs: RouteRecordRaw[], prefix = '') {
  for (const r of rs) {
    const p = (prefix + '/' + (r.path || '')).replace('//', '/')
    if (p && p !== '/') {
      REGISTERED_PATHS.add(p)
      if (r.meta?.hidden) HIDDEN_PATHS.add(p)
    }
    if (r.children?.length) collectPaths(r.children, p)
  }
}
collectPaths(routes)

export default router
