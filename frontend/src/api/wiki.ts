import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import type { ApiResponse } from '@/utils/request'

/** 统一解包 R<T> -> T */
async function unwrap<T>(config: Record<string, unknown>): Promise<T> {
  const res: AxiosResponse<ApiResponse<T>> = await request(config)
  return res.data.data
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface ItemView {
  id: number
  itemName: string
  itemDescription?: string
  visibility: 'public' | 'private'
  itemType: string
  creatorId: number
  creatorName?: string
  isArchive: boolean
  createdAt: string
  updatedAt: string
  /** 0=所有者 1=可编辑 2=只读 -1=非成员 */
  myRole: number
  memberCount: number
}

export interface MemberView {
  id: number
  userId: number
  username: string
  nickname?: string
  memberGroup: number
}

export interface CatalogView {
  id: number
  itemId: number
  parentId: number
  catName: string
  sNumber: number
  children: CatalogView[]
}

export interface PageSummary {
  id: number
  itemId: number
  catId?: number | null
  pageTitle: string
  sNumber: number
  authorName?: string
  version: number
  updatedAt: string
}

export interface PageDetail extends PageSummary {
  pageContent: string
  createdAt: string
}

export interface HistoryView {
  id: number
  pageId: number
  pageTitle: string
  authorName?: string
  createdAt: string
}

export interface HistoryDetail extends HistoryView {
  pageContent: string
}

export const wikiApi = {
  // ===== 项目 =====
  listItems: (params: { keyword?: string; page?: number; size?: number }) =>
    unwrap<PageResult<ItemView>>({ url: '/api/v1/wiki/items', method: 'get', params }),
  itemDetail: (itemId: number) =>
    unwrap<ItemView>({ url: `/api/v1/wiki/items/${itemId}`, method: 'get' }),
  createItem: (data: { itemName: string; itemDescription?: string; visibility?: string; password?: string }) =>
    unwrap<ItemView>({ url: '/api/v1/wiki/items', method: 'post', data }),
  updateItem: (itemId: number, data: { itemName: string; itemDescription?: string; visibility?: string; password?: string }) =>
    unwrap<ItemView>({ url: `/api/v1/wiki/items/${itemId}`, method: 'put', data }),
  deleteItem: (itemId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/items/${itemId}`, method: 'delete' }),

  // ===== 成员 =====
  listMembers: (itemId: number) =>
    unwrap<MemberView[]>({ url: `/api/v1/wiki/items/${itemId}/members`, method: 'get' }),
  addMember: (itemId: number, data: { username: string; memberGroup: number }) =>
    unwrap<void>({ url: `/api/v1/wiki/items/${itemId}/members`, method: 'post', data }),
  updateMember: (itemId: number, memberId: number, data: { memberGroup: number }) =>
    unwrap<void>({ url: `/api/v1/wiki/items/${itemId}/members/${memberId}`, method: 'put', data }),
  removeMember: (itemId: number, memberId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/items/${itemId}/members/${memberId}`, method: 'delete' }),

  // ===== 目录 =====
  catalogTree: (itemId: number) =>
    unwrap<CatalogView[]>({ url: '/api/v1/wiki/catalogs', method: 'get', params: { itemId } }),
  createCatalog: (itemId: number, data: { catName: string; parentId?: number; sNumber?: number }) =>
    unwrap<CatalogView>({ url: '/api/v1/wiki/catalogs', method: 'post', data: { itemId, ...data } }),
  renameCatalog: (catalogId: number, data: { catName: string; sNumber?: number }) =>
    unwrap<void>({ url: `/api/v1/wiki/catalogs/${catalogId}`, method: 'put', data }),
  deleteCatalog: (catalogId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/catalogs/${catalogId}`, method: 'delete' }),

  // ===== 页面 =====
  listPages: (itemId: number, catId?: number) =>
    unwrap<PageSummary[]>({ url: '/api/v1/wiki/pages', method: 'get', params: { itemId, catId } }),
  createPage: (itemId: number, data: { pageTitle: string; pageContent?: string; catId?: number | null }) =>
    unwrap<PageDetail>({ url: '/api/v1/wiki/pages', method: 'post', data: { itemId, ...data } }),
  getPage: (pageId: number) =>
    unwrap<PageDetail>({ url: `/api/v1/wiki/pages/${pageId}`, method: 'get' }),
  updatePage: (pageId: number, data: { pageTitle: string; pageContent: string; catId?: number | null; sNumber?: number; version: number }) =>
    unwrap<PageDetail>({ url: `/api/v1/wiki/pages/${pageId}`, method: 'put', data }),
  deletePage: (pageId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/pages/${pageId}`, method: 'delete' }),
  trash: (itemId: number) =>
    unwrap<PageSummary[]>({ url: '/api/v1/wiki/pages', method: 'get', params: { itemId, trash: true } }),
  restorePage: (pageId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/pages/${pageId}/restore`, method: 'post' }),

  // ===== 历史 / 搜索 =====
  history: (pageId: number) =>
    unwrap<HistoryView[]>({ url: `/api/v1/wiki/pages/${pageId}/history`, method: 'get' }),
  historyDetail: (historyId: number) =>
    unwrap<HistoryDetail>({ url: `/api/v1/wiki/pages/history-detail/${historyId}`, method: 'get' }),
  search: (itemId: number, keyword: string) =>
    unwrap<PageSummary[]>({ url: '/api/v1/wiki/pages', method: 'get', params: { itemId, keyword } }),
  globalSearch: (keyword: string) =>
    unwrap<GlobalSearchView[]>({ url: '/api/v1/wiki/pages/global-search', method: 'get', params: { keyword } }),

  // ===== 页面复制/排序 =====
  copyPage: (pageId: number) =>
    unwrap<PageDetail>({ url: `/api/v1/wiki/pages/${pageId}/copy`, method: 'post' }),
  movePage: (pageId: number, direction: 'up' | 'down') =>
    unwrap<void>({ url: `/api/v1/wiki/pages/${pageId}/move`, method: 'put', data: { direction } }),
  moveCatalog: (catalogId: number, direction: 'up' | 'down') =>
    unwrap<void>({ url: `/api/v1/wiki/catalogs/${catalogId}/move`, method: 'put', data: { direction } }),

  // ===== 导出 =====
  exportPageUrl: (pageId: number) => `/api/v1/wiki/pages/${pageId}/export`,
  exportItemUrl: (itemId: number) => `/api/v1/wiki/items/${itemId}/export`,

  // ===== 模板 =====
  listTemplates: () =>
    unwrap<TemplateView[]>({ url: '/api/v1/wiki/templates', method: 'get' }),

  // ===== 附件 =====
  uploadUrl: '/api/v1/wiki/attachments',

  // ===== 开放 API =====
  listApiKeys: (itemId: number) =>
    unwrap<ApiKeyView[]>({ url: `/api/v1/wiki/items/${itemId}/api-keys`, method: 'get' }),
  generateApiKey: (itemId: number, remark?: string) =>
    unwrap<ApiKeyView>({ url: `/api/v1/wiki/items/${itemId}/api-keys`, method: 'post', data: { remark } }),
  deleteApiKey: (itemId: number, keyId: number) =>
    unwrap<void>({ url: `/api/v1/wiki/items/${itemId}/api-keys/${keyId}`, method: 'delete' }),

  // ===== 分享/匿名浏览（不需要登录） =====
  verifyShare: (itemId: number, password?: string) =>
    request({ url: '/api/v1/wiki/share/verify', method: 'post', data: { itemId, password } }),
  viewItem: (itemId: number, token?: string) =>
    unwrap<ViewItem>({ url: `/api/v1/wiki/view/items/${itemId}`, method: 'get', headers: shareHeaders(token) }),
  viewCatalogs: (itemId: number, token?: string) =>
    unwrap<CatalogView[]>({ url: '/api/v1/wiki/view/catalogs', method: 'get', params: { itemId }, headers: shareHeaders(token) }),
  viewPages: (itemId: number, catId?: number, token?: string) =>
    unwrap<PageSummary[]>({ url: '/api/v1/wiki/view/pages', method: 'get', params: { itemId, catId }, headers: shareHeaders(token) }),
  viewPage: (pageId: number, token?: string) =>
    unwrap<PageDetail>({ url: `/api/v1/wiki/view/pages/${pageId}`, method: 'get', headers: shareHeaders(token) }),
}

function shareHeaders(token?: string) {
  return token ? { 'X-Share-Token': token } : {}
}

// 匿名浏览接口的类型（复用主类型）
export interface ViewItem {
  id: number
  itemName: string
  itemDescription?: string
  visibility: string
  creatorName?: string
  updatedAt: string
}

export interface TemplateView {
  id: number
  templateName: string
  templateContent?: string
  isBuiltin: number
  creatorName?: string
}

export interface AttachmentView {
  id: number
  itemId?: number
  originalName?: string
  fileSuffix?: string
  fileSize?: number
  contentType?: string
  url: string
}

export interface ApiKeyView {
  id: number
  apiKey: string
  apiSecret: string
  remark?: string
  createdByName?: string
  createdAt: string
}

export interface GlobalSearchView {
  itemId: number
  itemName: string
  pageId: number
  pageTitle: string
}

export const GROUP_LABEL: Record<number, string> = {
  0: '所有者',
  1: '可编辑',
  2: '只读',
  [-1]: '非成员',
}
