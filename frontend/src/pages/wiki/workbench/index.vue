<template>
  <el-container class="workbench">
    <el-aside width="300px" class="aside">
      <div class="aside-head">
        <span class="item-name" :title="item?.itemName">{{ item?.itemName || '...' }}</span>
        <span class="aside-actions">
          <el-tooltip content="新建页面" placement="bottom">
            <el-button text size="small" @click="openNewPage"><el-icon><Plus /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="新建目录" placement="bottom">
            <el-button text size="small" @click="openNewCatalog"><el-icon><FolderAdd /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="回收站" placement="bottom">
            <el-button text size="small" @click="openTrash"><el-icon><Delete /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="返回项目列表" placement="bottom">
            <el-button text size="small" @click="router.push('/wiki/items')">
              <el-icon><Back /></el-icon>
            </el-button>
          </el-tooltip>
        </span>
      </div>

      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索本文档..."
        clearable
        class="search"
        @input="onSearchInput"
      />

      <div v-loading="loading" class="tree-wrap">
        <!-- 搜索结果 -->
        <div v-if="searchMode" class="search-result">
          <div
            v-for="page in searchResult"
            :key="page.id"
            class="search-item"
            @click="selectPage(page.id)"
          >
            <el-icon><Document /></el-icon>
            <span>{{ page.pageTitle }}</span>
          </div>
          <el-empty v-if="searchResult.length === 0" description="无结果" :image-size="60" />
        </div>

        <!-- 目录树 -->
        <el-tree
          v-else
          :data="treeData"
          node-key="key"
          :default-expand-all="true"
          :expand-on-click-node="false"
          highlight-current
          @node-click="onNodeClick"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon v-if="data.type === 'catalog'"><Folder /></el-icon>
              <el-icon v-else><Document /></el-icon>
              <span class="label" :title="data.label">{{ data.label }}</span>
              <span class="node-actions" @click.stop>
                <el-dropdown
                  trigger="click"
                  @command="(cmd: string) => (data.type === 'catalog' ? catalogCommand(cmd, data) : pageCommand(cmd, data))"
                >
                  <el-icon class="more"><More /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="up">上移</el-dropdown-item>
                      <el-dropdown-item command="down">下移</el-dropdown-item>
                      <template v-if="data.type === 'catalog'">
                        <el-dropdown-item command="rename" divided>重命名</el-dropdown-item>
                        <el-dropdown-item command="delete">删除目录</el-dropdown-item>
                      </template>
                      <template v-else>
                        <el-dropdown-item command="copy" divided>复制页面</el-dropdown-item>
                        <el-dropdown-item command="delete">移入回收站</el-dropdown-item>
                      </template>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </el-aside>

    <el-main class="main">
      <div class="editor-head">
        <el-input
          v-model="form.pageTitle"
          placeholder="选择左侧页面, 或新建一个"
          class="title-input"
          :disabled="!current"
          maxlength="255"
        />
        <el-button @click="openHistory" :disabled="!current">历史版本</el-button>
        <el-tooltip content="导出本页 Markdown">
          <el-button @click="exportPage" :disabled="!current"><el-icon><Download /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="导出项目 Markdown zip">
          <el-button @click="exportItem"><el-icon><FolderChecked /></el-icon></el-button>
        </el-tooltip>
        <el-button type="primary" :disabled="!current" :loading="saving" @click="save">
          保存
        </el-button>
      </div>
      <div class="editor-meta" v-if="current">
        <span>{{ current.authorName }}</span>
        <span>v{{ current.version }}</span>
        <span>{{ current.updatedAt?.replace('T', ' ').slice(0, 19) }}</span>
      </div>
      <div ref="vditorEl" class="vditor-box" />
    </el-main>

    <!-- 新建页面 -->
    <el-dialog v-model="newPageVisible" title="新建页面" width="420px">
      <el-form label-width="80px">
        <el-form-item label="页面标题" required>
          <el-input v-model="newPageTitle" maxlength="255" placeholder="如: 登录接口" @keyup.enter="submitNewPage" />
        </el-form-item>
        <el-form-item label="所属目录">
          <el-tree-select
            v-model="newPageCatId"
            :data="catalogOptions"
            check-strictly
            clearable
            placeholder="未分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="模板">
          <el-select v-model="newPageTemplateId" clearable placeholder="空白页面" style="width: 100%">
            <el-option
              v-for="t in templates"
              :key="t.id"
              :value="t.id"
              :label="`${t.templateName}${t.isBuiltin === 1 ? ' (内置)' : ''}`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newPageVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitNewPage">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建目录 -->
    <el-dialog v-model="newCatalogVisible" title="新建目录" width="420px">
      <el-form label-width="80px">
        <el-form-item label="目录名称" required>
          <el-input v-model="newCatalogName" maxlength="128" placeholder="如: 用户模块" @keyup.enter="submitNewCatalog" />
        </el-form-item>
        <el-form-item label="父目录">
          <el-tree-select
            v-model="newCatalogParentId"
            :data="catalogOptions"
            check-strictly
            clearable
            placeholder="顶级目录"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newCatalogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNewCatalog">创建</el-button>
      </template>
    </el-dialog>

    <!-- 回收站 -->
    <el-drawer v-model="trashVisible" title="回收站" size="360px">
      <div v-for="page in trashList" :key="page.id" class="trash-item">
        <el-icon><Document /></el-icon>
        <span class="label">{{ page.pageTitle }}</span>
        <el-button text type="primary" size="small" @click="restore(page)">恢复</el-button>
      </div>
      <el-empty v-if="trashList.length === 0" description="回收站是空的" :image-size="60" />
    </el-drawer>

    <!-- 历史版本 -->
    <el-drawer v-model="historyVisible" title="历史版本" size="420px">
      <div v-for="h in historyList" :key="h.id" class="trash-item">
        <el-icon><Clock /></el-icon>
        <span class="label">
          {{ h.pageTitle }}
          <div class="sub">{{ h.authorName }} · {{ h.createdAt?.replace('T', ' ').slice(0, 19) }}</div>
        </span>
        <el-button text type="primary" size="small" @click="viewHistory(h)">查看</el-button>
      </div>
      <el-empty v-if="historyList.length === 0" description="暂无历史版本" :image-size="60" />
    </el-drawer>

    <!-- 历史内容 -->
    <el-dialog v-model="historyContentVisible" :title="`历史版本: ${historyContent?.pageTitle || ''}`" width="70%">
      <pre class="history-pre">{{ historyContent?.pageContent }}</pre>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, FolderAdd, Delete, Back, Document, Folder, More, Clock, Download, FolderChecked,
} from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { wikiApi, type CatalogView, type HistoryDetail, type HistoryView, type ItemView, type PageDetail, type PageSummary, type TemplateView } from '@/api/wiki'

interface TreeNode {
  key: string
  label: string
  type: 'catalog' | 'page'
  catalogId?: number
  pageId?: number
  children?: TreeNode[]
}

const route = useRoute()
const router = useRouter()

const itemId = ref<number | null>(null)
const item = ref<ItemView | null>(null)
const loading = ref(false)
const saving = ref(false)

const treeData = ref<TreeNode[]>([])
const catalogs = ref<CatalogView[]>([])
const keyword = ref('')
const searchMode = ref(false)
const searchResult = ref<PageSummary[]>([])

const current = ref<PageDetail | null>(null)
const form = ref({ pageTitle: '', pageContent: '' })
const dirty = ref(false)

const newPageVisible = ref(false)
const newPageTitle = ref('')
const newPageCatId = ref<number | null>(null)

const newCatalogVisible = ref(false)
const newCatalogName = ref('')
const newCatalogParentId = ref<number | null>(null)

const trashVisible = ref(false)
const trashList = ref<PageSummary[]>([])

const historyVisible = ref(false)
const historyList = ref<HistoryView[]>([])
const historyContentVisible = ref(false)
const historyContent = ref<HistoryDetail | null>(null)

const templates = ref<TemplateView[]>([])
const newPageTemplateId = ref<number | null>(null)

const vditorEl = ref<HTMLDivElement>()
let vditor: Vditor | null = null
let editorReady = false
let pendingContent: string | null = null
let searchTimer: ReturnType<typeof setTimeout> | null = null

const catalogOptions = computed(() => toOptions(catalogs.value))
function toOptions(list: CatalogView[]): Array<{ value: number; label: string; children?: unknown[] }> {
  return list.map((c) => ({ value: c.id, label: c.catName, children: toOptions(c.children || []) }))
}

function initEditor() {
  if (!vditorEl.value) return
  const token = localStorage.getItem('jade_access_token') || ''
  vditor = new Vditor(vditorEl.value, {
    height: 'calc(100vh - 170px)',
    mode: 'ir',
    placeholder: '开始编写 API 文档, 支持 Markdown...',
    cache: { enable: false },
    counter: { enable: true },
    upload: {
      url: wikiApi.uploadUrl,
      fieldName: 'file',
      headers: { Authorization: `Bearer ${token}` },
      multiple: false,
      // 后端返回 R<AttachmentView>, 转成 vditor 需要的 succMap 结构
      format: (_files: File[], text: string) => {
        try {
          const res = JSON.parse(text)
          const url = res?.data?.url as string | undefined
          const name = res?.data?.originalName as string | undefined
          if (url) {
            return JSON.stringify({ msg: '', code: 0, data: { errFiles: [], succMap: { [name || 'file']: url } } })
          }
          return JSON.stringify({ msg: res?.message || '上传失败', code: -1, data: null })
        } catch {
          return text
        }
      },
    },
    after() {
      editorReady = true
      if (pendingContent !== null) {
        vditor?.setValue(pendingContent)
        pendingContent = null
      }
    },
    input() {
      dirty.value = true
    },
  })
}

function setEditorContent(content: string) {
  if (editorReady) {
    vditor?.setValue(content || '')
  } else {
    pendingContent = content || ''
  }
}

async function loadTree() {
  if (!itemId.value) return
  const [tree, pages] = await Promise.all([
    wikiApi.catalogTree(itemId.value),
    wikiApi.listPages(itemId.value),
  ])
  catalogs.value = tree

  const catalogNodes = tree.map((c) => ({
    key: `c:${c.id}`,
    label: c.catName,
    type: 'catalog' as const,
    catalogId: c.id,
    children: [
      ...(c.children || []).length ? toNodes(c) : [],
    ],
  }))

  function toNodes(c: CatalogView): TreeNode[] {
    return (c.children || []).map((child) => ({
      key: `c:${child.id}`,
      label: child.catName,
      type: 'catalog' as const,
      catalogId: child.id,
      children: toNodes(child),
    }))
  }

  // 未分类页面挂在根级
  const pageNodesOf = (catId: number | null | undefined) =>
    pages
      .filter((p) => (catId == null ? !p.catId : p.catId === catId))
      .map((p) => ({ key: `p:${p.id}`, label: p.pageTitle, type: 'page' as const, pageId: p.id }))

  const roots: TreeNode[] = catalogNodes.map((node, idx) => ({
    ...node,
    children: [
      ...((catalogNodes[idx] as TreeNode).children || []),
      ...pageNodesOf(node.catalogId),
    ],
  }))
  roots.push({ key: 'c:none', label: '未分类', type: 'catalog', children: pageNodesOf(null) })
  treeData.value = roots
}

async function onNodeClick(data: TreeNode) {
  if (data.type === 'page' && data.pageId) {
    await selectPage(data.pageId)
  }
}

async function selectPage(pageId: number) {
  const detail = await wikiApi.getPage(pageId)
  current.value = detail
  form.value = { pageTitle: detail.pageTitle, pageContent: detail.pageContent }
  dirty.value = false
  setEditorContent(detail.pageContent)
}

async function save() {
  if (!itemId.value || !current.value) return
  saving.value = true
  try {
    const content = vditor?.getValue() || ''
    const updated = await wikiApi.updatePage(current.value.id, {
      pageTitle: form.value.pageTitle,
      pageContent: content,
      catId: current.value.catId ?? null,
      sNumber: current.value.sNumber,
      version: current.value.version,
    })
    current.value = updated
    form.value.pageContent = updated.pageContent
    dirty.value = false
    ElMessage.success('已保存')
    await loadTree()
  } catch {
    // 4009 冲突: request 拦截器已弹错误提示
  } finally {
    saving.value = false
  }
}

async function openNewPage() {
  newPageTitle.value = ''
  newPageCatId.value = null
  newPageTemplateId.value = null
  if (templates.value.length === 0) {
    templates.value = await wikiApi.listTemplates()
  }
  newPageVisible.value = true
}

async function submitNewPage() {
  if (!itemId.value || !newPageTitle.value.trim()) {
    ElMessage.warning('请填写页面标题')
    return
  }
  saving.value = true
  try {
    const template = templates.value.find((t) => t.id === newPageTemplateId.value)
    const page = await wikiApi.createPage(itemId.value, {
      pageTitle: newPageTitle.value.trim(),
      pageContent: template?.templateContent || '',
      catId: newPageCatId.value,
    })
    newPageVisible.value = false
    await loadTree()
    await selectPage(page.id)
  } finally {
    saving.value = false
  }
}

function openNewCatalog() {
  newCatalogName.value = ''
  newCatalogParentId.value = null
  newCatalogVisible.value = true
}

async function submitNewCatalog() {
  if (!itemId.value || !newCatalogName.value.trim()) {
    ElMessage.warning('请填写目录名称')
    return
  }
  await wikiApi.createCatalog(itemId.value, {
    catName: newCatalogName.value.trim(),
    parentId: newCatalogParentId.value || 0,
  })
  newCatalogVisible.value = false
  await loadTree()
}

async function catalogCommand(cmd: string, data: TreeNode) {
  if (!data.catalogId) return
  if (cmd === 'rename') {
    const { value } = await ElMessageBox.prompt('新的目录名称', '重命名', {
      inputValue: data.label,
    })
    await wikiApi.renameCatalog(data.catalogId, { catName: value })
    await loadTree()
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm(
      `删除目录「${data.label}」? 目录下的页面会移入"未分类"。`,
      '删除确认',
      { type: 'warning' }
    )
    await wikiApi.deleteCatalog(data.catalogId)
    ElMessage.success('已删除')
    await loadTree()
  }
  if (cmd === 'up' || cmd === 'down') {
    await wikiApi.moveCatalog(data.catalogId!, cmd)
    await loadTree()
  }
}

/** 页面节点菜单: 排序/复制/删除 */
async function pageCommand(cmd: string, data: TreeNode) {
  if (!data.pageId) return
  if (cmd === 'up' || cmd === 'down') {
    await wikiApi.movePage(data.pageId, cmd)
    await loadTree()
  }
  if (cmd === 'copy') {
    const page = await wikiApi.copyPage(data.pageId)
    ElMessage.success('已复制')
    await loadTree()
    await selectPage(page.id)
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`将页面「${data.label}」移入回收站?`, '删除确认', { type: 'warning' })
    await wikiApi.deletePage(data.pageId)
    ElMessage.success('已移入回收站')
    if (current.value?.id === data.pageId) current.value = null
    await loadTree()
  }
}

function exportPage() {
  window.open(wikiApi.exportPageUrl(current.value!.id), '_blank')
}

function exportItem() {
  window.open(wikiApi.exportItemUrl(itemId.value!), '_blank')
}

async function openTrash() {
  if (!itemId.value) return
  trashList.value = await wikiApi.trash(itemId.value)
  trashVisible.value = true
}

async function restore(page: PageSummary) {
  await wikiApi.restorePage(page.id)
  ElMessage.success('已恢复')
  trashList.value = await wikiApi.trash(itemId.value!)
  await loadTree()
}

async function openHistory() {
  if (!current.value) return
  historyList.value = await wikiApi.history(current.value.id)
  historyVisible.value = true
}

async function viewHistory(h: HistoryView) {
  historyContent.value = await wikiApi.historyDetail(h.id)
  historyVisible.value = false
  historyContentVisible.value = true
}

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    const kw = keyword.value.trim()
    searchMode.value = kw.length > 0
    if (kw && itemId.value) {
      searchResult.value = await wikiApi.search(itemId.value, kw)
    }
  }, 300)
}

onMounted(async () => {
  const q = Number(route.query.itemId)
  if (!q) {
    router.replace('/wiki/items')
    return
  }
  itemId.value = q
  loading.value = true
  try {
    item.value = await wikiApi.itemDetail(q)
    await loadTree()
  } finally {
    loading.value = false
  }
  await nextTick()
  initEditor()
  // 全局搜索跳转: 直达指定页面
  const pid = Number(route.query.pageId)
  if (pid) await selectPage(pid)
})

onBeforeUnmount(() => {
  vditor?.destroy()
  vditor = null
})
</script>

<style scoped>
.workbench {
  height: calc(100vh - 84px);
}
.aside {
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  padding: 12px;
}
.aside-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.item-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.aside-actions {
  display: flex;
  flex-shrink: 0;
}
.search {
  margin-bottom: 10px;
}
.tree-wrap {
  flex: 1;
  overflow: auto;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.tree-node .label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.node-actions {
  visibility: hidden;
}
:deep(.el-tree-node__content:hover) .node-actions {
  visibility: visible;
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
  background: var(--el-fill-color-light);
}
.trash-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.trash-item .label {
  flex: 1;
  font-size: 13px;
}
.trash-item .sub {
  color: #909399;
  font-size: 12px;
}
.main {
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-head {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  align-items: center;
}
.title-input :deep(.el-input__inner) {
  font-weight: 600;
  font-size: 16px;
}
.editor-meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 12px;
  margin-bottom: 8px;
}
.vditor-box {
  flex: 1;
}
.history-pre {
  max-height: 60vh;
  overflow: auto;
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 8px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
</style>
