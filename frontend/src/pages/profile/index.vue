<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const profile = reactive<any>({ id: null, username: '', nickname: '', email: '', phone: '', status: 1, tenantId: null, createdAt: '' })
const pwdForm = reactive({ old: '', new: '' })
const saving = ref(false)
const savingPwd = ref(false)

async function load() {
  const res = await request({ url: '/api/v1/auth/me', method: 'GET' })
  Object.assign(profile, res.data.data)
  userStore.setUser(res.data.data)
}

async function saveProfile() {
  saving.value = true
  try {
    await request({ url: '/api/v1/auth/profile', method: 'PUT', data: profile })
    ElMessage.success('已保存')
    userStore.setUser(profile)
  } finally { saving.value = false }
}

async function changePassword() {
  if (!pwdForm.old || !pwdForm.new) return ElMessage.warning('请填写原密码和新密码')
  savingPwd.value = true
  try {
    await request({ url: `/api/v1/auth/password?old=${encodeURIComponent(pwdForm.old)}&new=${encodeURIComponent(pwdForm.new)}`, method: 'PUT' })
    ElMessage.success('密码已修改')
    pwdForm.old = ''
    pwdForm.new = ''
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally { savingPwd.value = false }
}

// ===== MCP 访问令牌 =====
const mcpTokens = ref<any[]>([])
const mcpSaving = ref(false)
const newMcpToken = ref('')

async function loadMcpTokens() {
  const res = await request({ url: '/api/v1/mcp/tokens', method: 'GET' })
  mcpTokens.value = res.data.data || []
}

async function genMcpToken() {
  mcpSaving.value = true
  try {
    const res = await request({ url: '/api/v1/mcp/tokens', method: 'POST', data: { remark: 'AI 助手' } })
    newMcpToken.value = res.data.data.token
    await loadMcpTokens()
  } finally { mcpSaving.value = false }
}

async function revokeMcpToken(row: any) {
  await request({ url: `/api/v1/mcp/tokens/${row.id}`, method: 'DELETE' })
  ElMessage.success('已吊销')
  await loadMcpTokens()
}

onMounted(() => { load(); loadMcpTokens() })
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card>
        <template #header><span>个人资料</span></template>
        <el-form :model="profile" label-width="100" label-position="right">
          <el-form-item label="ID">
            <el-input v-model="profile.id" disabled />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="profile.username" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="profile.nickname" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profile.email" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="profile.phone" />
          </el-form-item>
          <el-form-item label="状态">
            <el-tag :type="profile.status === 1 ? 'success' : 'danger'">
              {{ profile.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="注册时间">
            <el-input v-model="profile.createdAt" disabled />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveProfile" style="background: #00a86b; border-color: #00a86b;">保存</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="12">
      <el-card>
        <template #header><span>修改密码</span></template>
        <el-form :model="pwdForm" label-width="100" label-position="right">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.old" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.new" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="savingPwd" @click="changePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon>
          <p>修改密码后需用新密码重新登录</p>
          <p>建议：至少 8 位，包含字母+数字</p>
        </el-alert>
      </el-card>
    </el-col>
  </el-row>

  <el-row>
    <el-col :span="24">
      <el-card>
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>MCP 访问令牌 (AI 助手接入)</span>
            <el-button type="primary" size="small" :loading="mcpSaving" @click="genMcpToken">生成新令牌</el-button>
          </div>
        </template>
        <el-alert v-if="newMcpToken" type="success" :closable="false" style="margin-bottom:12px">
          <p>新令牌仅显示一次, 请立即复制: <code>{{ newMcpToken }}</code></p>
          <p style="margin-top:4px">接入示例: 客户端 MCP 配置 URL 填 <code>http://localhost:8082/mcp</code>, 请求头
            <code>X-Mcp-Token: &lt;令牌&gt;</code></p>
        </el-alert>
        <el-table :data="mcpTokens" size="small">
          <el-table-column prop="maskedToken" label="令牌" width="220" />
          <el-table-column prop="remark" label="备注" />
          <el-table-column prop="createdAt" label="创建时间" width="200">
            <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="danger" size="small" @click="revokeMcpToken(row)">吊销</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert type="info" :closable="false" show-icon style="margin-top:12px">
          令牌用于 AI 客户端 (Claude/ZCode/Cursor) 通过 MCP 协议读取你的文档, 权限与你的账号一致; 泄露后立即吊销。
        </el-alert>
      </el-card>
    </el-col>
  </el-row>
</template>
