<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { setLocale, type LocaleValue } from '@/i18n'
import { useUserStore } from '@/stores/user'
import request, { BizError } from '@/utils/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t, locale } = useI18n()

const form = reactive({ username: 'admin', password: 'admin123', uuid: '', code: '' })
const loading = ref(false)
// 失败超次（1005）或验证码错误（1006）后显示验证码行
const captchaVisible = ref(false)
const captchaImage = ref('')

async function refreshCaptcha() {
  const res = await request({ url: '/api/v1/auth/captcha', method: 'GET' })
  const data = res.data.data as { uuid: string; image: string }
  captchaImage.value = data.image
  form.uuid = data.uuid
}

function toggleLang() {
  setLocale((locale.value === 'zh-CN' ? 'en' : 'zh-CN') as LocaleValue)
}

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success(t('login.success'))
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (e) {
    // 需要验证码/验证码错误 → 显示验证码行并换图（错误提示已在 request.ts 统一弹出）
    if (e instanceof BizError && (e.code === 1005 || e.code === 1006)) {
      captchaVisible.value = true
      form.code = ''
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <button class="lang-toggle" @click="toggleLang">{{ locale === 'zh-CN' ? 'EN' : '中文' }}</button>

    <div class="login-bg">
      <div class="brand">
        <div class="brand-tag">{{ t('login.tag') }}</div>
        <h1 class="title">Jade Platform</h1>
        <p class="subtitle">{{ t('login.slogan') }}</p>
      </div>
    </div>

    <div class="login-box">
      <h2 class="form-title">{{ t('login.welcome') }}</h2>
      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item :label="t('login.username')">
          <el-input v-model="form.username" :placeholder="t('login.usernamePlaceholder')" size="large" />
        </el-form-item>
        <el-form-item :label="t('login.password')">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item v-if="captchaVisible" :label="t('login.captcha')">
          <div class="captcha-row">
            <el-input
              v-model="form.code"
              :placeholder="t('login.captchaPlaceholder')"
              size="large"
              maxlength="4"
              @keyup.enter="handleLogin"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              :title="t('login.captchaPlaceholder')"
              alt="captcha"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          {{ t('login.submit') }}
        </el-button>
      </el-form>
      <p class="tip">{{ t('login.tip') }}</p>
    </div>
  </div>
</template>

<style scoped>
/* 现代工具风 (Linear 式): 暖调近黑 + 层次表面 + 发丝边框, 翡翠唯一强调色 */
.login-page {
  --bg: #08090a;
  --surface-1: #141519;
  --surface-2: #1c1d22;
  --hairline: rgba(255, 255, 255, 0.07);
  --text: #f4f5f6;
  --text-2: #9ba1a9;
  --text-3: #676d75;
  --accent: #3ecf8e;
  --accent-strong: #5edda4;
  --mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  height: 100vh;
  display: flex;
  background: var(--bg);
}
/* 语言切换: 右上角小按钮 */
.lang-toggle {
  position: absolute;
  top: 16px;
  right: 20px;
  z-index: 10;
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid var(--hairline);
  background: var(--surface-1);
  color: var(--text-2);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.lang-toggle:hover {
  color: var(--text);
  border-color: rgba(255, 255, 255, 0.14);
}
/* ---- 左侧品牌区 ---- */
.login-bg {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--hairline);
}
.brand { text-align: center; }
.brand-tag {
  display: inline-block;
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.18em;
  color: var(--accent);
  border: 1px solid var(--hairline);
  border-radius: 6px;
  padding: 5px 14px;
  margin-bottom: 28px;
  background: var(--surface-1);
}
.title {
  font-size: 56px;
  margin: 0 0 14px;
  letter-spacing: -0.03em;
  font-weight: 650;
  color: var(--text);
}
.subtitle {
  font-size: 15px;
  letter-spacing: 0.08em;
  color: var(--text-3);
  margin: 0;
}
/* ---- 右侧登录卡: 表面 + 发丝边框 ---- */
.login-box {
  width: 400px;
  align-self: center;
  margin-right: 9%;
  background: var(--surface-1);
  border: 1px solid var(--hairline);
  border-radius: 12px;
  padding: 40px 38px 30px;
}
.form-title {
  text-align: center;
  margin: 0 0 30px;
  letter-spacing: -0.01em;
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}
.login-box :deep(.el-form-item__label) {
  color: var(--text-2);
  font-size: 13px;
}
.login-box :deep(.el-input__wrapper) {
  background: var(--bg);
  box-shadow: 0 0 0 1px var(--hairline) inset;
  border-radius: 8px;
  transition: box-shadow 0.15s ease;
}
.login-box :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.14) inset;
}
.login-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.login-box :deep(.el-input__inner) {
  color: var(--text);
}
.login-box :deep(.el-input__inner::placeholder) {
  color: var(--text-3);
}
.login-box :deep(.el-input__suffix) {
  color: var(--text-3);
}
.login-btn {
  width: 100%;
  margin-top: 6px;
  background: var(--accent) !important;
  border: none !important;
  color: #04120c !important;
  font-weight: 600;
  letter-spacing: 0.2em;
  border-radius: 8px;
  transition: background-color 0.15s ease;
}
.login-btn:hover {
  background: var(--accent-strong) !important;
}
.tip {
  text-align: center;
  color: var(--text-3);
  font-size: 12px;
  margin-top: 18px;
  font-family: var(--mono);
  letter-spacing: 0.02em;
}
/* 窄屏堆叠 */
@media (max-width: 860px) {
  .login-page {
    flex-direction: column;
    justify-content: center;
    gap: 40px;
  }
  .login-bg {
    flex: none;
    border-right: none;
  }
  .title { font-size: 36px; }
  .login-box {
    margin: 0 auto;
    width: min(400px, 88vw);
  }
}
/* ===== 点缀层: 克制的质感细节 ===== */
/* 环境光: 顶部极淡翡翠渐变 */
.login-page {
  background:
    radial-gradient(800px 420px at 50% 0%, rgba(62, 207, 142, 0.07), transparent 70%),
    var(--bg);
}
/* 点阵纹理: 细点 + 顶部渐隐 */
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(ellipse at 50% 20%, #000 15%, transparent 65%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 20%, #000 15%, transparent 65%);
}
.login-bg,
.login-box {
  position: relative;
  z-index: 1;
}
/* 卡片顶缘高光 */
.login-box {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
/* 品牌区: 静态翡翠菱标 + 副标题下的渐变细线 */
.brand::before {
  content: '';
  display: block;
  width: 14px;
  height: 14px;
  margin: 0 auto 22px;
  border-radius: 3px;
  transform: rotate(45deg);
  background: linear-gradient(135deg, var(--accent-strong), #1e9e6a);
  box-shadow: 0 0 14px rgba(62, 207, 142, 0.35);
}
.subtitle::after {
  content: '';
  display: block;
  width: 72px;
  height: 1px;
  margin: 20px auto 0;
  background: linear-gradient(90deg, transparent, rgba(62, 207, 142, 0.5), transparent);
}

.captcha-row { display: flex; gap: 12px; width: 100%; }
.captcha-row .el-input { flex: 1; }
.captcha-img { height: 40px; border-radius: 4px; cursor: pointer; flex-shrink: 0; }
</style>
