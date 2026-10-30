import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/**
 * 统一响应格式
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

const service: AxiosInstance = axios.create({
  // 同源相对路径: dev 走 vite 代理, 生产走 nginx 反代; 接口 url 自带 /api/v1 前缀
  baseURL: '',
  timeout: 15000,
})

// 无需认证的公开端点: 带上过期 token 反而会被 MP-JWT 拒绝(401)
const PUBLIC_ENDPOINTS = ['/api/v1/auth/login', '/api/v1/wiki/share', '/api/v1/wiki/view/', '/api/open/']

// 401 统一处理标志 (防止并发请求重复弹窗/跳转)
let redirectingToLogin = false

/** 会话失效: 清登录态并回登录页 (裸 HTTP 401 与 R 格式 1001/1002 两条路径共用) */
function handleSessionExpired() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  const userStore = useUserStore()
  userStore.logout()
  ElMessage.error('登录已过期, 请重新登录')
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
  setTimeout(() => { redirectingToLogin = false }, 1000)
}

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const url = config.url || ''
    if (userStore.accessToken && !PUBLIC_ENDPOINTS.some((p) => url.includes(p))) {
      config.headers.Authorization = `Bearer ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 兼容非包装响应（如文件下载）
    if (typeof res !== 'object' || res === null || !('code' in res)) {
      return response
    }

    if (res.code === 0) {
      return response
    }

    // 1001/1002 未登录
    if (res.code === 1001 || res.code === 1002) {
      ElMessageBox.confirm('登录状态已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }).catch(() => {})
      return Promise.reject(new Error(res.message))
    }

    // 1003 无权限
    if (res.code === 1003) {
      ElMessage.error('您没有权限执行此操作')
      return Promise.reject(new Error(res.message))
    }

    // 业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    // 裸 HTTP 401: MP-JWT 过期/无效在 security 层直接 401 (R 格式 1001/1002 走上方成功分支)
    if (error.response?.status === 401) {
      handleSessionExpired()
      return Promise.reject(new Error('登录已过期'))
    }
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
