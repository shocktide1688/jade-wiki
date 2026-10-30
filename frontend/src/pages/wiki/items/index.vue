<template>
  <div class="wiki-items">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="keyword"
          placeholder="搜索项目名称"
          clearable
          style="width: 240px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button type="primary" @click="openCreate">新建项目</el-button>
      </div>
      <el-popover placement="bottom-end" width="420" trigger="click" @show="focusGlobal">
        <template #reference>
          <el-input
            v-model="globalKeyword"
            placeholder="全局搜索文档内容 (跨项目)"
            style="width: 260px"
            @input="onGlobalInput"
          />
        </template>
        <div v-loading="globalLoading">
          <div v-for="r in globalResults" :key="r.pageId" class="global-item" @click="gotoPage(r)">
            <el-tag size="small" effect="plain">{{ r.itemName }}</el-tag>
            <span>{{ r.pageTitle }}</span>
          </div>
          <el-empty v-if="globalResults.length === 0" description="无结果" :image-size="50" />
        </div>
      </el-popover>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="还没有项目, 点击右上角新建" />

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="item in items" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="item-card" @click="openWorkbench(item)">
          <div class="card-head">
            <span class="title" :title="item.itemName">{{ item.itemName }}</span>
            <el-tag size="small" :type="item.visibility === 'public' ? 'success' : 'info'">
              {{ item.visibility === 'public' ? '公开' : '私有' }}
            </el-tag>
          </div>
          <div class="desc">{{ item.itemDescription || '暂无描述' }}</div>
          <div class="meta">
            <span>{{ item.creatorName }}</span>
            <el-tag size="small" effect="plain">{{ GROUP_LABEL[item.myRole] }}</el-tag>
            <span class="spacer" />
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, item)">
              <el-button text size="small" @click.stop>
                <el-icon><More /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="share">分享/浏览</el-dropdown-item>
                  <el-dropdown-item command="export">导出 Markdown</el-dropdown-item>
                  <el-dropdown-item v-if="item.myRole === 0" command="settings">项目设置</el-dropdown-item>
                  <el-dropdown-item v-if="item.myRole === 0" command="members">成员管理</el-dropdown-item>
                  <el-dropdown-item v-if="item.myRole === 0" command="apikeys">开放 API</el-dropdown-item>
                  <el-dropdown-item v-if="item.myRole === 0" command="delete" divided>删除项目</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建/设置 项目 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '项目设置' : '新建项目'"
      width="480px"
      @closed="resetForm"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.itemName" maxlength="128" placeholder="如: 用户中心 API 文档" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.itemDescription" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="form.visibility">
            <el-radio value="private">私有 (仅成员可见)</el-radio>
            <el-radio value="public">公开 (任何人可读)</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 开放 API -->
    <el-dialog v-model="apiKeysVisible" title="开放 API (CI 推送文档)" width="640px">
      <div class="member-add">
        <el-input v-model="apiKeyRemark" placeholder="备注(如: CI 机器人)" style="width: 220px" />
        <el-button type="primary" :loading="apiKeySaving" @click="genApiKey">生成 Key</el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 10px">
        用 api_key/api_secret 调 POST /api/open/wiki/page/save 即可免登录推送页面（按标题存在则覆盖, 不存在则创建）。
      </el-alert>
      <el-table :data="apiKeys" size="small">
        <el-table-column prop="apiKey" label="api_key" width="200" />
        <el-table-column prop="apiSecret" label="api_secret" width="260" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="delApiKey(row as ApiKeyView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 成员管理 -->
    <el-dialog v-model="membersVisible" title="成员管理" width="560px">
      <div class="member-add">
        <el-input v-model="memberForm.username" placeholder="用户名" style="width: 180px" />
        <el-select v-model="memberForm.memberGroup" style="width: 120px">
          <el-option label="可编辑" :value="1" />
          <el-option label="只读" :value="2" />
        </el-select>
        <el-button type="primary" :loading="memberSaving" @click="addMember">添加</el-button>
      </div>
      <el-table :data="members" size="small">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="角色" width="160">
          <template #default="{ row }">
            <el-select
              v-if="row.memberGroup !== 0"
              :model-value="row.memberGroup"
              size="small"
              @change="(v: number) => changeMemberRole(row as MemberView, v)"
            >
              <el-option label="可编辑" :value="1" />
              <el-option label="只读" :value="2" />
            </el-select>
            <el-tag v-else size="small" type="warning">所有者</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button
              v-if="row.memberGroup !== 0"
              text
              type="danger"
              size="small"
              @click="removeMember(row as MemberView)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { More } from '@element-plus/icons-vue'
import { wikiApi, GROUP_LABEL, type ApiKeyView, type GlobalSearchView, type ItemView, type MemberView } from '@/api/wiki'

const router = useRouter()

const items = ref<ItemView[]>([])
const loading = ref(false)
const keyword = ref('')

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref({ itemName: '', itemDescription: '', visibility: 'private' })

const membersVisible = ref(false)
const members = ref<MemberView[]>([])
const memberTargetId = ref<number | null>(null)
const memberSaving = ref(false)
const memberForm = ref({ username: '', memberGroup: 1 })

async function load() {
  loading.value = true
  try {
    const res = await wikiApi.listItems({ keyword: keyword.value || undefined, page: 1, size: 100 })
    items.value = res.records
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { itemName: '', itemDescription: '', visibility: 'private' }
  formVisible.value = true
}

function openSettings(item: ItemView) {
  editingId.value = item.id
  form.value = {
    itemName: item.itemName,
    itemDescription: item.itemDescription || '',
    visibility: item.visibility,
  }
  formVisible.value = true
}

async function submitForm() {
  if (!form.value.itemName.trim()) {
    ElMessage.warning('请填写项目名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await wikiApi.updateItem(editingId.value, form.value)
      ElMessage.success('已保存')
    } else {
      const created = await wikiApi.createItem(form.value)
      ElMessage.success('项目已创建')
      formVisible.value = false
      openWorkbench(created)
      return
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleCommand(cmd: string, item: ItemView) {
  if (cmd === 'settings') openSettings(item)
  if (cmd === 'members') await openMembers(item)
  if (cmd === 'apikeys') await openApiKeys(item)
  if (cmd === 'share') openShare(item)
  if (cmd === 'export') exportZip(item)
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除项目「${item.itemName}」吗?`, '删除确认', { type: 'warning' })
    await wikiApi.deleteItem(item.id)
    ElMessage.success('已删除')
    await load()
  }
}

async function openMembers(item: ItemView) {
  memberTargetId.value = item.id
  members.value = await wikiApi.listMembers(item.id)
  membersVisible.value = true
}

async function addMember() {
  if (!memberTargetId.value || !memberForm.value.username.trim()) return
  memberSaving.value = true
  try {
    await wikiApi.addMember(memberTargetId.value, memberForm.value)
    members.value = await wikiApi.listMembers(memberTargetId.value)
    memberForm.value = { username: '', memberGroup: 1 }
    ElMessage.success('已添加')
  } finally {
    memberSaving.value = false
  }
}

async function changeMemberRole(row: MemberView, group: number) {
  if (!memberTargetId.value) return
  await wikiApi.updateMember(memberTargetId.value, row.id, { memberGroup: group })
  members.value = await wikiApi.listMembers(memberTargetId.value)
}

async function removeMember(row: MemberView) {
  if (!memberTargetId.value) return
  await wikiApi.removeMember(memberTargetId.value, row.id)
  members.value = await wikiApi.listMembers(memberTargetId.value)
}

function openWorkbench(item: ItemView) {
  router.push({ path: '/wiki/workbench', query: { itemId: String(item.id) } })
}

// ===== 全局搜索 =====
const globalKeyword = ref('')
const globalResults = ref<GlobalSearchView[]>([])
const globalLoading = ref(false)
let globalTimer: ReturnType<typeof setTimeout> | null = null

function focusGlobal() {}

function onGlobalInput() {
  if (globalTimer) clearTimeout(globalTimer)
  globalTimer = setTimeout(async () => {
    const kw = globalKeyword.value.trim()
    if (!kw) { globalResults.value = []; return }
    globalLoading.value = true
    try {
      globalResults.value = await wikiApi.globalSearch(kw)
    } finally {
      globalLoading.value = false
    }
  }, 300)
}

function gotoPage(r: GlobalSearchView) {
  router.push({ path: '/wiki/workbench', query: { itemId: String(r.itemId), pageId: String(r.pageId) } })
}

// ===== 分享/导出 =====
function openShare(item: ItemView) {
  const url = `${location.origin}/wiki/view/${item.id}`
  navigator.clipboard?.writeText(url).catch(() => {})
  ElMessageBox.alert(
    `浏览链接: <a href="${url}" target="_blank">${url}</a><br/>${item.visibility === 'public' ? '公开项目, 无需密码。' : '私密项目需访问密码(在项目设置中查看)。'}`,
    '分享',
    { dangerouslyUseHTMLString: true }
  )
}

function exportZip(item: ItemView) {
  window.open(wikiApi.exportItemUrl(item.id), '_blank')
}

// ===== 开放 API =====
const apiKeysVisible = ref(false)
const apiKeys = ref<ApiKeyView[]>([])
const apiKeyTargetId = ref<number | null>(null)
const apiKeySaving = ref(false)
const apiKeyRemark = ref('')

async function openApiKeys(item: ItemView) {
  apiKeyTargetId.value = item.id
  apiKeys.value = await wikiApi.listApiKeys(item.id)
  apiKeysVisible.value = true
}

async function genApiKey() {
  if (!apiKeyTargetId.value) return
  apiKeySaving.value = true
  try {
    await wikiApi.generateApiKey(apiKeyTargetId.value, apiKeyRemark.value || undefined)
    apiKeys.value = await wikiApi.listApiKeys(apiKeyTargetId.value)
    apiKeyRemark.value = ''
    ElMessage.success('已生成')
  } finally {
    apiKeySaving.value = false
  }
}

async function delApiKey(row: ApiKeyView) {
  if (!apiKeyTargetId.value) return
  await wikiApi.deleteApiKey(apiKeyTargetId.value, row.id)
  apiKeys.value = await wikiApi.listApiKeys(apiKeyTargetId.value)
}

function resetForm() {
  editingId.value = null
}

onMounted(load)
</script>

<style scoped>
.wiki-items {
  padding: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.item-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.card-head .title {
  font-weight: 600;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.desc {
  color: #909399;
  font-size: 13px;
  height: 38px;
  margin: 8px 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 12px;
}
.spacer {
  flex: 1;
}
.member-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.global-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  cursor: pointer;
  font-size: 13px;
}
.global-item:hover {
  background: #f5f7fa;
}
</style>
