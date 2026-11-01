<template>
  <div class="wiki-view" :class="{ 'is-full': isFull }">
    <header class="view-head">
      <div class="head-left">
        <span class="item-name">{{ item?.itemName || t('wiki.doc') }}</span>
        <el-tag v-if="item" size="small" :type="item.visibility === 'public' ? 'success' : 'warning'">
          {{ item.visibility === 'public' ? t('wiki.publicShare') : t('wiki.passwordShare') }}
        </el-tag>
      </div>
      <div class="head-right">
        <el-button text size="small" class="lang-btn" @click="toggleLang">
          {{ locale === 'zh-CN' ? 'EN' : '中文' }}
        </el-button>
        <el-button text size="small" @click="exportZip" v-if="unlocked">{{ t('wiki.exportMarkdown') }}</el-button>
      </div>
    </header>

    <div class="view-body">
      <!-- 未解锁: 输入访问密码 -->
      <div v-if="!unlocked" class="lock-panel">
        <el-card v-if="needPassword" class="lock-card">
          <h3>{{ item?.itemName || t('wiki.privateDoc') }}</h3>
          <p class="lock-tip">{{ t('wiki.lockTip') }}</p>
          <el-input
            v-model="password"
            type="password"
            :placeholder="t('wiki.passwordPlaceholder')"
            show-password
            @keyup.enter="unlock"
          />
          <el-button type="primary" style="width: 100%; margin-top: 12px" :loading="loading" @click="unlock">
            {{ t('wiki.unlock') }}
          </el-button>
        </el-card>
        <el-empty v-else-if="error" :description="error" />
      </div>

      <!-- 已解锁: 左树右文 -->
      <el-container v-else class="view-container">
        <el-aside width="280px" class="view-aside">
          <el-input v-model="keyword" :placeholder="t('wiki.searchPlaceholder')" clearable :prefix-icon="Search" @input="onSearch" />
          <div v-if="searchMode" class="search-result">
            <div v-for="p in searchResult" :key="p.id" class="search-item" @click="selectPage(p.id)">
              <el-icon class="search-ico"><component :is="iconFor(p.pageTitle)" /></el-icon><span>{{ p.pageTitle }}</span>
            </div>
            <el-empty v-if="searchResult.length === 0" :description="t('wiki.noResult')" :image-size="60" />
          </div>
          <el-tree
            v-else
            :data="treeData"
            node-key="key"
            default-expand-all
            :expand-on-click-node="false"
            highlight-current
            @node-click="onNodeClick"
          >
            <template #default="{ data, node }">
              <span class="tree-node">
                <el-icon v-if="data.type === 'catalog'">
                  <FolderOpened v-if="node.expanded" /><Folder v-else />
                </el-icon>
                <el-icon v-else><component :is="iconFor(data.label)" /></el-icon>
                <span class="label" :title="data.label">{{ data.label }}</span>
              </span>
            </template>
          </el-tree>
        </el-aside>
        <el-main ref="mainEl" class="view-main" :class="{ 'has-toc': toc.length > 0 && tocOpen }">
          <div class="doc-card">
            <div class="doc-card-head">
              <h2 class="page-title">{{ current?.pageTitle }}</h2>
              <div class="title-actions" v-if="current">
                <el-tooltip :content="t('wiki.copyMarkdown')" placement="top">
                  <el-button text @click="copyPage"><el-icon><CopyDocument /></el-icon></el-button>
                </el-tooltip>
                <el-tooltip :content="isFull ? t('wiki.exitFullscreen') : t('wiki.fullscreen')" placement="top">
                  <el-button text @click="toggleFull"><el-icon><FullScreen /></el-icon></el-button>
                </el-tooltip>
              </div>
            </div>
            <div class="page-meta" v-if="current">{{ current.authorName }} · {{ current.updatedAt?.replace('T', ' ').slice(0, 19) }}</div>
            <div ref="previewEl" class="preview-box" />
          </div>
        </el-main>
      </el-container>

      <!-- 右侧悬浮大纲 (ShowDoc 式 Catalog): 提取本页标题锚点, 点击定位 -->
      <aside class="toc-panel" v-if="tocOpen && toc.length > 0">
        <div class="toc-head">{{ t('wiki.toc') }} <span class="toc-close" @click="tocOpen = false">×</span></div>
        <div
          v-for="item in toc"
          :key="item.id"
          :class="['toc-item', `lv${item.level}`, { active: item.id === activeId }]"
          @click="jumpTo(item.id)"
        >
          {{ item.text }}
        </div>
      </aside>
      <button class="toc-fab" v-if="!tocOpen && toc.length > 0 && !isFull" :title="t('wiki.toc')" @click="tocOpen = true">
        <el-icon><List /></el-icon>
      </button>
      <button class="exit-full" v-if="isFull" @click="toggleFull">
        <el-icon><FullScreen /></el-icon> {{ t('wiki.exitFullscreen') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Aim,
  Bell,
  CirclePlus,
  Clock,
  Collection,
  Connection,
  Delete,
  Document,
  Folder,
  FolderOpened,
  Key,
  MapLocation,
  Odometer,
  Paperclip,
  Picture,
  Printer,
  Reading,
  Search,
  Setting,
  ShoppingCart,
  Tickets,
  Upload,
  Download,
  Van,
  WarningFilled,
  CopyDocument,
  FullScreen,
  List,
} from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { useI18n } from 'vue-i18n'
import { setLocale, type LocaleValue } from '@/i18n'
import { wikiApi, type CatalogView, type PageDetail, type PageSummary, type ViewItem } from '@/api/wiki'

const { t, locale } = useI18n()

function toggleLang() {
  setLocale((locale.value === 'zh-CN' ? 'en' : 'zh-CN') as LocaleValue)
}

/** 切语言后, 已构建的树节点里 "未分类" 标签跟着换 */
watch(locale, () => {
  const walk = (nodes: TreeNode[]) =>
    nodes.forEach((n) => {
      if (n.key === 'c:none') n.label = t('wiki.uncategorized')
      if (n.children) walk(n.children)
    })
  walk(treeData.value)
  // 代码块头部按钮是渲染期生成的 DOM, 切语言时同步文案
  previewEl.value?.querySelectorAll('.code-block').forEach((block) => {
    const ln = block.querySelector<HTMLButtonElement>('.code-ln')
    if (ln) {
      ln.textContent = t('wiki.lineNumbers')
      ln.title = t('wiki.lineNumbers')
    }
    const cp = block.querySelector<HTMLButtonElement>('.code-copy')
    if (cp && !cp.classList.contains('copied')) cp.textContent = t('wiki.copy')
  })
})

interface TreeNode {
  key: string
  label: string
  type: 'catalog' | 'page'
  pageId?: number
  children?: TreeNode[]
}

const route = useRoute()
const itemId = ref<number | null>(null)

const item = ref<ViewItem | null>(null)
const needPassword = ref(false)
const unlocked = ref(false)
const error = ref('')
const password = ref('')
const loading = ref(false)
const token = ref<string | undefined>(undefined)

const treeData = ref<TreeNode[]>([])
const current = ref<PageDetail | null>(null)
const keyword = ref('')
const searchMode = ref(false)
const searchResult = ref<PageSummary[]>([])
const previewEl = ref<HTMLDivElement>()
const mainEl = ref()
const toc = ref<Array<{ id: string; text: string; level: number }>>([])
const activeId = ref('')
const tocOpen = ref(true)
const isFull = ref(false)
let scrollAttached = false
let searchTimer: ReturnType<typeof setTimeout> | null = null

async function loadItem() {
  try {
    item.value = await wikiApi.viewItem(itemId.value!, token.value)
    needPassword.value = false
    await enterView()
  } catch {
    needPassword.value = true
  }
}

async function unlock() {
  loading.value = true
  try {
    const res: any = await wikiApi.verifyShare(itemId.value!, password.value)
    token.value = res.data?.data?.viewToken
    sessionStorage.setItem(`wiki-view-token-${itemId.value}`, token.value || '')
    item.value = await wikiApi.viewItem(itemId.value!, token.value)
    needPassword.value = false
    await enterView()
  } catch {
    ElMessage.error(t('wiki.unlockFailed'))
  } finally {
    loading.value = false
  }
}

async function enterView() {
  unlocked.value = true
  const [catalogs, pages] = await Promise.all([
    wikiApi.viewCatalogs(itemId.value!, token.value),
    wikiApi.viewPages(itemId.value!, undefined, token.value),
  ])
  const pageNodesOf = (catId: number | null) =>
    pages
      .filter((p) => (catId === null ? !p.catId : p.catId === catId))
      .map((p) => ({ key: `p:${p.id}`, label: p.pageTitle, type: 'page' as const, pageId: p.id }))
  const toNodes = (c: CatalogView): TreeNode => ({
    key: `c:${c.id}`,
    label: c.catName,
    type: 'catalog',
    children: [...(c.children || []).map(toNodes), ...pageNodesOf(c.id)],
  })
  treeData.value = [
    ...catalogs.map(toNodes),
    { key: 'c:none', label: t('wiki.uncategorized'), type: 'catalog', children: pageNodesOf(null) },
  ]
  // 默认打开第一个页面
  const first = pages[0]
  if (first) await selectPage(first.id)
  else await nextTick(() => Vditor.preview(previewEl.value!, t('wiki.emptyDoc'), { after: () => { enhanceCodeBlocks(); buildToc() } } as never))
}

async function selectPage(pageId: number) {
  current.value = await wikiApi.viewPage(pageId, token.value)
  await nextTick()
  // 正文里的 [TOC] 占位标记由右侧大纲面板承担, 渲染前剔除
  const content = (current.value?.pageContent || '').replace(/^\s*\[TOC\]\s*$/gim, '')
  Vditor.preview(previewEl.value!, content, {
    hljs: { style: 'github-dark', lineNumber: false },
    mode: 'light',
    after: () => {
      enhanceCodeBlocks()
      buildToc()
      attachScroll()
    },
  } as never)
}

/** 复制文本: Clipboard API 失败时 (页面未聚焦等) 回退 execCommand */
async function copyText(text: string) {
  try {
    await navigator.clipboard!.writeText(text)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.cssText = 'position:fixed;opacity:0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    ta.remove()
  }
}

/** 代码块增强: 包一层头部条 (语言标签 + 行号开关 + 复制按钮), GitBook/Mintlify 式 */
function enhanceCodeBlocks() {
  const root = previewEl.value
  if (!root) return
  root.querySelectorAll('pre').forEach((pre) => {
    if (pre.parentElement?.classList.contains('code-body')) return
    const code = pre.querySelector('code')
    const lang = code?.className.match(/language-(\w+)/)?.[1] || 'text'
    const wrap = document.createElement('div')
    wrap.className = 'code-block'
    const head = document.createElement('div')
    head.className = 'code-head'
    const langSpan = document.createElement('span')
    langSpan.className = 'code-lang'
    langSpan.textContent = lang
    // 行号槽: 与代码区同字号同行高, 默认隐藏, 开关切换
    const lines = (code?.textContent || '').replace(/\n$/, '').split('\n').length
    const gutter = document.createElement('div')
    gutter.className = 'code-gutter'
    gutter.setAttribute('aria-hidden', 'true')
    for (let i = 1; i <= lines; i++) {
      const n = document.createElement('span')
      n.textContent = String(i)
      gutter.appendChild(n)
    }
    const lnBtn = document.createElement('button')
    lnBtn.type = 'button'
    lnBtn.className = 'code-ln'
    lnBtn.textContent = t('wiki.lineNumbers')
    lnBtn.title = t('wiki.lineNumbers')
    lnBtn.addEventListener('click', () => {
      wrap.classList.toggle('show-ln')
      lnBtn.classList.toggle('active')
    })
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.className = 'code-copy'
    btn.textContent = t('wiki.copy')
    btn.addEventListener('click', async () => {
      await copyText(code?.textContent || '')
      btn.textContent = t('wiki.codeCopied')
      btn.classList.add('copied')
      setTimeout(() => {
        btn.textContent = t('wiki.copy')
        btn.classList.remove('copied')
      }, 1500)
    })
    const actions = document.createElement('div')
    actions.className = 'code-actions'
    actions.append(lnBtn, btn)
    head.append(langSpan, actions)
    // 代码体: 行号槽绝对定位压在 pre 左侧, 横向滚动时保持固定
    const body = document.createElement('div')
    body.className = 'code-body'
    pre.parentNode?.insertBefore(wrap, pre)
    body.append(gutter, pre)
    wrap.append(head, body)
  })
}

/** 页面标题 → 语义图标: 按关键词命中, 兜底 Document */
const ICON_RULES: Array<[RegExp, Component]> = [
  [/错误|异常|error|bug|exception/i, WarningFilled],
  [/修改记录|变更|历史|changelog|history|日志|log/i, Clock],
  [/创建|新增|add|create/i, CirclePlus],
  [/删除|delete|remove/i, Delete],
  [/打印|print/i, Printer],
  [/扫描|scan/i, Aim],
  [/物流|运输|配送|shipping|delivery/i, Van],
  [/轨迹|跟踪|路由|track|trace|route/i, MapLocation],
  [/面单|单据|票据|ticket/i, Tickets],
  [/订单|order/i, ShoppingCart],
  [/接口|对接|api|集成|integrat/i, Connection],
  [/回调|通知|webhook|callback|notify/i, Bell],
  [/签名|加密|鉴权|认证|安全|密钥|auth|sign|token|key/i, Key],
  [/配置|设置|setting|config/i, Setting],
  [/上传|upload/i, Upload],
  [/下载|download/i, Download],
  [/图片|图像|image|picture/i, Picture],
  [/附件|attachment/i, Paperclip],
  [/查询|获取|详情|query|get|detail/i, Search],
  [/参数|字典|param/i, Collection],
  [/说明|指南|介绍|guide|readme|intro/i, Reading],
  [/首页|概览|overview|home/i, Odometer],
]
function iconFor(title: string): Component {
  const hit = ICON_RULES.find(([re]) => re.test(title))
  return hit ? hit[1] : Document
}

/** 从渲染结果提取 h1~h4 生成大纲 */
function buildToc() {
  const root = previewEl.value
  if (!root) return
  // Vditor.preview 直接注入裸 HTML (无 .vditor-reset 包装), 标题自带中文 id
  const heads = root.querySelectorAll('h1, h2, h3, h4')
  const list: Array<{ id: string; text: string; level: number }> = []
  heads.forEach((h, i) => {
    if (!h.id) h.id = `toc-${i}`
    list.push({ id: h.id, text: h.textContent?.trim() || '', level: Number(h.tagName.substring(1)) })
  })
  toc.value = list
}

function copyPage() {
  if (!current.value) return
  copyText(`# ${current.value.pageTitle}\n\n${current.value.pageContent || ''}`)
  ElMessage.success(t('wiki.copied'))
}

/** 网页内全屏 (类 ShowDoc): 隐藏站内头部/侧栏, 文档占满页面区域; ESC 或按钮退出 */
function toggleFull() {
  isFull.value = !isFull.value
  if (isFull.value) {
    document.addEventListener('keydown', onFullEsc)
  } else {
    document.removeEventListener('keydown', onFullEsc)
  }
}

function onFullEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') toggleFull()
}

function jumpTo(id: string) {
  activeId.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function attachScroll() {
  const el = (mainEl.value?.$el ?? mainEl.value) as HTMLElement | undefined
  if (!el || scrollAttached) return
  scrollAttached = true
  el.addEventListener('scroll', onScroll, { passive: true })
}

/** 滚动时高亮当前所在章节 */
function onScroll() {
  const root = previewEl.value
  if (!root) return
  const mainRect = ((mainEl.value?.$el ?? mainEl.value) as HTMLElement).getBoundingClientRect()
  let cur = ''
  root.querySelectorAll('h1, h2, h3, h4').forEach((h) => {
    if (h.getBoundingClientRect().top - mainRect.top <= 90) cur = h.id
  })
  // 还没滚到任何标题时, 高亮第一个
  if (!cur) {
    const first = root.querySelector('h1, h2, h3, h4')
    cur = first ? first.id : ''
  }
  activeId.value = cur
}

function onNodeClick(data: TreeNode) {
  if (data.type === 'page' && data.pageId) selectPage(data.pageId)
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    const kw = keyword.value.trim()
    searchMode.value = kw.length > 0
    if (kw) {
      // 匿名搜索: 简化为在前端已加载的页面列表里过滤标题
      const pages = await wikiApi.viewPages(itemId.value!, undefined, token.value)
      searchResult.value = pages.filter((p) => p.pageTitle.toLowerCase().includes(kw.toLowerCase()))
    }
  }, 300)
}

function exportZip() {
  window.open(`/api/v1/wiki/items/${itemId.value}/export`, '_blank')
}

onMounted(async () => {
  itemId.value = Number(route.params.itemId)
  token.value = sessionStorage.getItem(`wiki-view-token-${itemId.value}`) || undefined
  await loadItem()
})
</script>

<style scoped>
/* ===== Jade Wiki · 现代工具风阅读器 (Linear 式克制深色) =====
   暖调近黑底 + 层次表面 + 发丝边框; 翡翠只做唯一强调色, 克制使用 */
.wiki-view {
  --bg: #08090a;
  --surface-1: #141519;
  --surface-2: #1c1d22;
  --surface-3: #26272e;
  --hairline: rgba(255, 255, 255, 0.07);
  --hairline-strong: rgba(255, 255, 255, 0.14);
  --text: #f4f5f6;
  --text-2: #9ba1a9;
  --text-3: #676d75;
  --accent: #3ecf8e;
  --accent-strong: #5edda4;
  --mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: var(--text-2);
  background: var(--bg);
}
/* ---- 顶部导航: 纯色 + 发丝线 ---- */
.view-head {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--bg);
  border-bottom: 1px solid var(--hairline);
  padding: 0 22px;
  height: 56px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.lang-btn {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
}
.item-name {
  font-weight: 600;
  font-size: 15px;
  letter-spacing: -0.01em;
  color: var(--text);
}
.view-head :deep(.el-tag) {
  background: var(--surface-2);
  border-color: var(--hairline);
  color: var(--text-2);
  font-family: var(--mono);
  font-size: 11px;
}
.view-head :deep(.el-button) {
  color: var(--text-2);
  transition: color 0.15s ease;
}
.view-head :deep(.el-button:hover) {
  color: var(--text);
  background: var(--surface-2);
}
.view-body {
  flex: 1;
  display: flex;
}
/* ---- 密码解锁面板 ---- */
.lock-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.lock-card {
  width: 380px;
  text-align: center;
  --el-card-bg-color: transparent;
  --el-card-border-color: var(--hairline);
  background: var(--surface-1);
  border-radius: 12px;
}
.lock-card h3 {
  margin: 0;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text);
}
.lock-tip {
  color: var(--text-3);
  font-size: 13px;
  margin: 8px 0 16px;
}
.lock-card :deep(.el-input__wrapper) {
  background: var(--bg);
  box-shadow: 0 0 0 1px var(--hairline) inset;
}
.lock-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.lock-card :deep(.el-input__inner) {
  color: var(--text);
}
.lock-card :deep(.el-button--primary) {
  background: var(--accent);
  border: none;
  color: #04120c;
  font-weight: 600;
  transition: background-color 0.15s ease;
}
.lock-card :deep(.el-button--primary:hover) {
  background: var(--accent-strong);
}
.lock-panel :deep(.el-empty__description p) {
  color: var(--text-3);
}
.view-container {
  flex: 1;
  height: calc(100vh - 57px);
  padding: 0 16px;
  gap: 16px;
}
/* ---- 左侧目录: 层次表面 + 发丝边框 ---- */
.view-aside {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  margin: 16px 0;
  padding: 14px 12px;
  overflow: auto;
}
.view-aside :deep(.el-input__wrapper) {
  background: var(--bg);
  box-shadow: 0 0 0 1px var(--hairline) inset;
  transition: box-shadow 0.15s ease;
}
.view-aside :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.view-aside :deep(.el-input__inner) {
  color: var(--text);
}
.view-aside :deep(.el-tree) {
  background: transparent;
  color: var(--text-2);
}
.view-aside :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 6px;
  margin-bottom: 1px;
  color: var(--text-2);
  transition: background-color 0.15s ease, color 0.15s ease;
}
.view-aside :deep(.el-tree-node__content:hover) {
  background: var(--surface-2);
  color: var(--text);
}
.view-aside :deep(.el-tree-node__expand-icon) {
  color: var(--text-3);
}
/* 选中节点: 表面高亮 + 强调色指示条, 不发光 */
.view-aside :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background: var(--surface-3);
  color: var(--accent-strong);
  box-shadow: inset 2px 0 0 var(--accent);
}
.view-aside :deep(.el-empty__description p) {
  color: var(--text-3);
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tree-node :deep(.el-icon) {
  color: var(--text-3);
}
.view-aside :deep(.el-tree-node.is-current > .el-tree-node__content) .tree-node .el-icon {
  color: var(--accent);
}
.tree-node .label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-2);
  transition: background-color 0.15s ease, color 0.15s ease;
}
.search-item:hover {
  background: var(--surface-2);
  color: var(--text);
}
.search-item .search-ico {
  color: var(--text-3);
  flex-shrink: 0;
}
.search-item:hover .search-ico {
  color: var(--text);
}
.view-main {
  background: transparent;
  flex: 1;
  min-width: 0;
  padding: 0;
  overflow: auto;
}
/* 细滚动条 */
.view-main::-webkit-scrollbar,
.toc-panel::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.view-main::-webkit-scrollbar-thumb,
.toc-panel::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}
.view-main::-webkit-scrollbar-thumb:hover,
.toc-panel::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
.view-main::-webkit-scrollbar-track,
.toc-panel::-webkit-scrollbar-track {
  background: transparent;
}
/* 有大纲时给右侧留出面板空间 */
.view-main.has-toc {
  margin-right: 250px;
}
/* ---- 文档卡片: 表面 + 发丝边框, 无阴影无发光 ---- */
.doc-card {
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  max-width: 880px;
  margin: 24px auto 48px;
  padding: 48px 64px 80px;
}
.doc-card:fullscreen {
  border-radius: 0;
  max-width: none;
  overflow: auto;
  padding: 40px max(56px, calc((100% - 880px) / 2));
}
.doc-card-head {
  position: relative;
  text-align: left;
  border-bottom: 1px solid var(--hairline);
  padding-bottom: 20px;
  margin-bottom: 28px;
}
.title-actions {
  position: absolute;
  right: 0;
  top: 0;
}
.title-actions :deep(.el-button) {
  color: var(--text-3);
  transition: color 0.15s ease;
}
.title-actions :deep(.el-button:hover) {
  color: var(--text);
  background: var(--surface-2);
}
.page-title {
  margin: 0;
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text);
}
.page-meta {
  color: var(--text-3);
  font-family: var(--mono);
  font-size: 12px;
  letter-spacing: 0.02em;
  margin-top: 10px;
}
.preview-box {
  border: none;
}
/* ---- 阅读排版 ----
   注: Vditor.preview 注入的是裸 HTML, 选择器直接命中 .preview-box 后代 */
.preview-box {
  font-size: 15px;
  line-height: 1.75;
  color: var(--text-2);
}
.preview-box ::selection {
  background: rgba(62, 207, 142, 0.25);
  color: var(--text);
}
.preview-box :deep(h1),
.preview-box :deep(h2),
.preview-box :deep(h3),
.preview-box :deep(h4) {
  color: var(--text);
  font-weight: 600;
  letter-spacing: -0.01em;
  scroll-margin-top: 72px;
}
.preview-box :deep(h2) {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--hairline);
}
.preview-box :deep(a) {
  color: var(--accent);
  text-decoration: none;
  transition: color 0.15s ease;
}
.preview-box :deep(a:hover) {
  color: var(--accent-strong);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.preview-box :deep(strong) {
  color: var(--text);
}
.preview-box :deep(hr) {
  border: none;
  height: 1px;
  margin: 28px 0;
  background: var(--hairline);
}
.preview-box :deep(blockquote) {
  margin: 16px 0;
  padding: 4px 16px;
  border-left: 2px solid var(--hairline-strong);
  color: var(--text-2);
}
.preview-box :deep(img) {
  border-radius: 8px;
  border: 1px solid var(--hairline);
}
/* 行内代码: 等宽 + 表面底, 无边框; 长 URL 允许在任意字符断行 */
.preview-box :deep(code) {
  background: var(--surface-2);
  color: var(--accent-strong);
  border-radius: 5px;
  padding: 1px 6px;
  font-family: var(--mono);
  font-size: 0.88em;
  overflow-wrap: anywhere;
}
/* 表格: 发丝边框 + 表面表头 */
.preview-box :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}
.preview-box :deep(th),
.preview-box :deep(td) {
  border: 1px solid var(--hairline);
  padding: 9px 16px;
}
.preview-box :deep(th) {
  background: var(--surface-2);
  color: var(--text);
  font-weight: 600;
  font-size: 13px;
  text-align: left;
}
/* 覆盖 Vditor 自带的浅色行底 */
.preview-box :deep(tr),
.preview-box :deep(tr:nth-child(2n)) {
  background: transparent;
  transition: background-color 0.15s ease;
}
.preview-box :deep(tbody tr:hover) {
  background: rgba(255, 255, 255, 0.02);
}
/* 代码块: 深色表面 + 发丝边框 */
.preview-box :deep(pre) {
  background: #0c0d10;
  border: 1px solid var(--hairline);
  border-radius: 8px;
  padding: 14px 16px;
}
.preview-box :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #d3d8de;
  font-size: 13px;
  overflow-wrap: normal;
}
/* ---- 网页内全屏: 只留文档 ---- */
.wiki-view.is-full .view-head,
.wiki-view.is-full .view-aside,
.wiki-view.is-full .toc-panel,
.wiki-view.is-full .toc-fab {
  display: none;
}
.wiki-view.is-full .view-container {
  padding: 0;
  height: 100vh;
  gap: 0;
}
.wiki-view.is-full .view-main,
.wiki-view.is-full .view-main.has-toc {
  margin-right: 0;
  padding: 0;
}
.wiki-view.is-full .doc-card {
  margin: 0;
  max-width: none;
  border-radius: 0;
  border-left: none;
  border-right: none;
  min-height: 100vh;
  padding: 48px max(56px, calc((100% - 880px) / 2)) 96px;
}
.exit-full {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid var(--hairline);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.exit-full:hover {
  color: var(--text);
  border-color: var(--hairline-strong);
}
/* ---- 右侧悬浮大纲 ---- */
.toc-panel {
  position: fixed;
  right: 20px;
  top: 88px;
  width: 220px;
  max-height: calc(100vh - 140px);
  overflow: auto;
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 10px;
  padding: 8px 0;
  z-index: 10;
}
.toc-head {
  font-family: var(--mono);
  font-weight: 500;
  font-size: 11px;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: var(--text-3);
  padding: 4px 14px 8px;
  border-bottom: 1px solid var(--hairline);
}
.toc-close {
  float: right;
  color: var(--text-3);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  transition: color 0.15s ease;
}
.toc-close:hover {
  color: var(--text);
}
.toc-fab {
  position: fixed;
  right: 20px;
  top: 88px;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--surface-2);
  border: 1px solid var(--hairline);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  z-index: 10;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.toc-fab:hover {
  color: var(--text);
  border-color: var(--hairline-strong);
}
.toc-item {
  font-size: 12.5px;
  color: var(--text-3);
  padding: 5px 14px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-left: 2px solid transparent;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.toc-item:hover {
  color: var(--text);
}
.toc-item.active {
  color: var(--accent-strong);
  border-left-color: var(--accent);
}
.toc-item.lv1 { padding-left: 14px; font-weight: 600; }
.toc-item.lv2 { padding-left: 24px; }
.toc-item.lv3 { padding-left: 34px; }
.toc-item.lv4 { padding-left: 44px; }
/* ---- 窄屏适配 ---- */
@media (max-width: 1200px) {
  /* 空间不够时收起悬浮大纲, 把宽度还给正文 */
  .toc-panel,
  .toc-fab {
    display: none;
  }
  .view-main.has-toc {
    margin-right: 0;
  }
}
@media (max-width: 900px) {
  .view-container {
    padding: 0 10px;
    gap: 10px;
  }
  .view-aside {
    width: 200px !important;
    padding: 12px 8px;
  }
  .doc-card {
    padding: 28px 22px 56px;
  }
  .page-title {
    font-size: 24px;
  }
}
@media (max-width: 640px) {
  .view-aside {
    display: none;
  }
  .doc-card {
    margin: 10px auto 24px;
  }
}
/* ===== 点缀层: 克制的质感细节 (低透明度, 不喧宾夺主) ===== */
/* 环境光: 顶部极淡翡翠渐变 */
.wiki-view {
  background:
    radial-gradient(900px 380px at 50% -8%, rgba(62, 207, 142, 0.08), transparent 70%),
    var(--bg);
}
/* 点阵纹理: 1px 细点, 顶部渐隐, 工程图纸感 */
.wiki-view::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(ellipse at 50% 0%, #000 15%, transparent 65%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 0%, #000 15%, transparent 65%);
}
.view-head,
.view-body {
  position: relative;
  z-index: 1;
}
/* 顶栏品牌印记: 静态翡翠菱标 */
.item-name {
  display: flex;
  align-items: center;
}
.item-name::before {
  content: '';
  width: 9px;
  height: 9px;
  margin-right: 10px;
  border-radius: 2px;
  transform: rotate(45deg);
  background: linear-gradient(135deg, var(--accent-strong), #1e9e6a);
  box-shadow: 0 0 8px rgba(62, 207, 142, 0.35);
}
/* 顶栏底部: 品牌渐变发丝线 */
.view-head::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(62, 207, 142, 0.35), transparent);
  opacity: 0.6;
}
/* 面板顶缘高光: 光打在上沿的实体感 */
.doc-card,
.view-aside,
.toc-panel,
.lock-card {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
/* 元信息前的翡翠小点 */
.page-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-meta::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
}
/* 大纲激活项: 淡翡翠底 */
.toc-item.active {
  background: rgba(62, 207, 142, 0.07);
}
/* 代码块: 微亮底色 + 顶缘高光 */
.preview-box :deep(pre) {
  background: #0e1013;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}
/* 链接: 默认淡下划线, hover 加深 */
.preview-box :deep(a) {
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-color: rgba(62, 207, 142, 0.35);
}
.preview-box :deep(a:hover) {
  text-decoration: underline;
  text-decoration-color: var(--accent-strong);
}
/* 键盘焦点环 */
.wiki-view :deep(:focus-visible) {
  outline: 1px solid var(--accent);
  outline-offset: 1px;
}
/* ---- 代码块: 头部条 (语言标签 + 行号开关 + 复制按钮) ---- */
.preview-box :deep(.code-block) {
  margin: 16px 0;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: #0e1013;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}
.preview-box :deep(.code-block pre) {
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding: 14px 16px;
  line-height: 1.6;
  transition: padding-left 0.15s ease;
}
.preview-box :deep(.code-block pre code) {
  line-height: 1.6;
}
.preview-box :deep(.code-head) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px 6px 16px;
  border-bottom: 1px solid var(--hairline);
  background: rgba(255, 255, 255, 0.02);
}
.preview-box :deep(.code-lang) {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-3);
}
.preview-box :deep(.code-actions) {
  display: flex;
  align-items: center;
  gap: 6px;
}
.preview-box :deep(.code-copy),
.preview-box :deep(.code-ln) {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--text-3);
  background: transparent;
  border: 1px solid var(--hairline);
  border-radius: 6px;
  padding: 3px 10px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.preview-box :deep(.code-copy:hover),
.preview-box :deep(.code-ln:hover) {
  color: var(--text);
  border-color: var(--hairline-strong);
}
.preview-box :deep(.code-copy.copied) {
  color: var(--accent-strong);
  border-color: rgba(62, 207, 142, 0.4);
}
/* 行号开关激活态: 翡翠点缀 */
.preview-box :deep(.code-ln.active) {
  color: var(--accent-strong);
  border-color: rgba(62, 207, 142, 0.4);
  background: rgba(62, 207, 142, 0.08);
}
/* 行号槽: 绝对定位贴在代码左侧, 与代码同字号同行高保证对齐 */
.preview-box :deep(.code-body) {
  position: relative;
}
.preview-box :deep(.code-gutter) {
  display: none;
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 1;
  width: 44px;
  padding: 14px 0;
  overflow: hidden;
  background: #0e1013;
  border-right: 1px solid var(--hairline);
  font-family: var(--mono);
  font-size: 13px;
  line-height: 1.6;
  text-align: right;
  color: var(--text-3);
  user-select: none;
}
.preview-box :deep(.code-gutter span) {
  display: block;
  padding-right: 10px;
  opacity: 0.55;
}
.preview-box :deep(.code-block.show-ln .code-gutter) {
  display: block;
}
.preview-box :deep(.code-block.show-ln pre) {
  padding-left: 56px;
}
</style>
