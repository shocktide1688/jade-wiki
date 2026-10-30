<template>
  <div class="wiki-view" :class="{ 'is-full': isFull }">
    <header class="view-head">
      <div class="head-left">
        <span class="item-name">{{ item?.itemName || '文档' }}</span>
        <el-tag v-if="item" size="small" :type="item.visibility === 'public' ? 'success' : 'warning'">
          {{ item.visibility === 'public' ? '公开分享' : '密码分享' }}
        </el-tag>
      </div>
      <div class="head-right">
        <el-button text size="small" @click="exportZip" v-if="unlocked">导出 Markdown</el-button>
      </div>
    </header>

    <div class="view-body">
      <!-- 未解锁: 输入访问密码 -->
      <div v-if="!unlocked" class="lock-panel">
        <el-card v-if="needPassword" class="lock-card">
          <h3>{{ item?.itemName || '私密文档' }}</h3>
          <p class="lock-tip">该项目为私密分享, 请输入访问密码</p>
          <el-input
            v-model="password"
            type="password"
            placeholder="访问密码"
            show-password
            @keyup.enter="unlock"
          />
          <el-button type="primary" style="width: 100%; margin-top: 12px" :loading="loading" @click="unlock">
            查看文档
          </el-button>
        </el-card>
        <el-empty v-else-if="error" :description="error" />
      </div>

      <!-- 已解锁: 左树右文 -->
      <el-container v-else class="view-container">
        <el-aside width="280px" class="view-aside">
          <el-input v-model="keyword" placeholder="输入关键字" clearable :prefix-icon="Search" @input="onSearch" />
          <div v-if="searchMode" class="search-result">
            <div v-for="p in searchResult" :key="p.id" class="search-item" @click="selectPage(p.id)">
              <el-icon><Document /></el-icon><span>{{ p.pageTitle }}</span>
            </div>
            <el-empty v-if="searchResult.length === 0" description="无结果" :image-size="60" />
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
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon v-if="data.type === 'catalog'"><Folder /></el-icon>
                <el-icon v-else><Document /></el-icon>
                <span class="label">{{ data.label }}</span>
              </span>
            </template>
          </el-tree>
        </el-aside>
        <el-main ref="mainEl" class="view-main" :class="{ 'has-toc': toc.length > 0 && tocOpen }">
          <div class="doc-card">
            <div class="doc-card-head">
              <h2 class="page-title">{{ current?.pageTitle }}</h2>
              <div class="title-actions" v-if="current">
                <el-tooltip content="复制 Markdown" placement="top">
                  <el-button text @click="copyPage"><el-icon><CopyDocument /></el-icon></el-button>
                </el-tooltip>
                <el-tooltip :content="isFull ? '退出全屏' : '全屏阅读'" placement="top">
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
        <div class="toc-head">目录 <span class="toc-close" @click="tocOpen = false">×</span></div>
        <div
          v-for="item in toc"
          :key="item.id"
          :class="['toc-item', `lv${item.level}`, { active: item.id === activeId }]"
          @click="jumpTo(item.id)"
        >
          {{ item.text }}
        </div>
      </aside>
      <button class="toc-fab" v-if="!tocOpen && toc.length > 0 && !isFull" title="目录" @click="tocOpen = true">
        <el-icon><List /></el-icon>
      </button>
      <button class="exit-full" v-if="isFull" @click="toggleFull">
        <el-icon><FullScreen /></el-icon> 退出全屏
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, Folder, Search, CopyDocument, FullScreen, List } from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { wikiApi, type CatalogView, type PageDetail, type PageSummary, type ViewItem } from '@/api/wiki'

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
    ElMessage.error('密码错误或不可访问')
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
    { key: 'c:none', label: '未分类', type: 'catalog', children: pageNodesOf(null) },
  ]
  // 默认打开第一个页面
  const first = pages[0]
  if (first) await selectPage(first.id)
  else await nextTick(() => Vditor.preview(previewEl.value!, '# 空文档\n\n该项目还没有内容。', { after: () => buildToc() } as never))
}

async function selectPage(pageId: number) {
  current.value = await wikiApi.viewPage(pageId, token.value)
  await nextTick()
  Vditor.preview(previewEl.value!, current.value?.pageContent || '', {
    hljs: { style: 'github-dark', lineNumber: false },
    mode: 'light',
    after: () => {
      buildToc()
      attachScroll()
    },
  } as never)
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
  navigator.clipboard?.writeText(`# ${current.value.pageTitle}\n\n${current.value.pageContent || ''}`)
  ElMessage.success('已复制 Markdown')
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
.wiki-view {
  min-height: 100vh;
  background: #f6f8fa;
  display: flex;
  flex-direction: column;
}
.view-head {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.item-name {
  font-weight: 600;
  font-size: 16px;
}
.view-body {
  flex: 1;
  display: flex;
}
.lock-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.lock-card {
  width: 360px;
  text-align: center;
}
.lock-tip {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 16px;
}
.view-container {
  flex: 1;
  height: calc(100vh - 61px);
  padding: 0 16px;
  gap: 16px;
}
/* 目录: 白色圆角卡片 */
.view-aside {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  margin: 16px 0;
  padding: 14px 12px;
}
/* 树节点: 选中蓝底左竖条 */
.view-aside :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 6px;
  margin-bottom: 2px;
}
.view-aside :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background: #e8f3ff;
  color: #1c6dd0;
  box-shadow: inset 3px 0 0 #1c6dd0;
}
.ico-dir { color: #f8a13a; }
.ico-page { color: #4a9df8; }
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
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
}
.search-item:hover {
  background: #f5f7fa;
}
.view-main {
  background: transparent;
  flex: 1;
  min-width: 0;
  padding: 0;
  overflow: auto;
}
/* 有大纲时给右侧留出面板空间 */
.view-main.has-toc {
  margin-right: 250px;
}
/* 内容: 居中白色圆角卡片 (ShowDoc 式) */
.doc-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(31, 45, 61, 0.08);
  max-width: 960px;
  margin: 16px auto 32px;
  padding: 40px 56px 72px;
}
.doc-card:fullscreen {
  border-radius: 0;
  max-width: none;
  overflow: auto;
  padding: 40px max(56px, calc((100% - 900px) / 2));
}
.doc-card-head {
  position: relative;
  text-align: center;
  border-bottom: 1px solid #f0f2f5;
  padding-bottom: 20px;
  margin-bottom: 28px;
}
.title-actions {
  position: absolute;
  right: 0;
  top: 2px;
}
.page-title {
  margin: 0;
  text-align: center;
  font-size: 28px;
}
.page-meta {
  color: #909399;
  font-size: 12px;
  text-align: center;
  margin-top: 12px;
}
.preview-box {
  border: none;
}
/* ---- ShowDoc 风阅读排版 ----
   注: Vditor.preview 注入的是裸 HTML, 选择器直接命中 .preview-box 后代 */
.preview-box {
  font-size: 15px;
  line-height: 1.8;
}
/* 参数表: 蓝色表头 + 浅边框宽松行距 */
.preview-box :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}
.preview-box :deep(th),
.preview-box :deep(td) {
  border: 1px solid #e8ecf1;
  padding: 10px 18px;
}
.preview-box :deep(th) {
  background: #edf4ff;
  color: #1c6dd0;
  font-weight: 600;
}
/* 返回示例: mac 终端风深色代码块 */
.preview-box :deep(pre) {
  position: relative;
  background: #292c33;
  border-radius: 8px;
  padding: 32px 16px 14px;
}
.preview-box :deep(pre::before) {
  content: '';
  position: absolute;
  top: 12px;
  left: 14px;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #ff5f56;
  box-shadow: 19px 0 0 #ffbd2e, 38px 0 0 #27c93f;
}
.preview-box :deep(pre code) {
  background: transparent;
  color: #e8eaed;
}
/* 锚点定位预留顶部空间 */
.preview-box :deep(h1),
.preview-box :deep(h2),
.preview-box :deep(h3),
.preview-box :deep(h4) {
  scroll-margin-top: 16px;
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
  box-shadow: none;
  min-height: 100vh;
  padding: 48px max(56px, calc((100% - 900px) / 2)) 96px;
}
.exit-full {
  position: fixed;
  top: 20px;
  right: 24px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.exit-full:hover {
  color: #1c6dd0;
  border-color: #1c6dd0;
}
/* ---- 右侧悬浮大纲 ---- */
.toc-panel {
  position: fixed;
  right: 20px;
  top: 104px;
  width: 220px;
  max-height: calc(100vh - 150px);
  overflow: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 10px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
.toc-head {
  font-weight: 600;
  font-size: 13px;
  color: #1c6dd0;
  padding: 2px 14px 8px;
  border-bottom: 1px solid #f0f2f5;
}
.toc-close {
  float: right;
  color: #909399;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.toc-close:hover {
  color: #1c6dd0;
}
.toc-fab {
  position: fixed;
  right: 20px;
  top: 104px;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1c6dd0;
  z-index: 10;
}
.toc-item {
  font-size: 12.5px;
  color: #606266;
  padding: 5px 14px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-left: 2px solid transparent;
}
.toc-item:hover {
  color: #1c6dd0;
}
.toc-item.active {
  color: #1c6dd0;
  border-left-color: #1c6dd0;
  background: #f0f7ff;
}
.toc-item.lv1 { padding-left: 14px; font-weight: 600; }
.toc-item.lv2 { padding-left: 24px; }
.toc-item.lv3 { padding-left: 34px; }
.toc-item.lv4 { padding-left: 44px; }
</style>
