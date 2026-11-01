import { createI18n } from 'vue-i18n'

/** 支持的语言 */
export const LOCALES = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
] as const

export type LocaleValue = (typeof LOCALES)[number]['value']

const STORAGE_KEY = 'jade-locale'

function detectLocale(): LocaleValue {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'zh-CN' || saved === 'en') return saved
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en'
}

export function setLocale(locale: LocaleValue) {
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

const messages = {
  'zh-CN': {
    wiki: {
      doc: '文档',
      publicShare: '公开分享',
      passwordShare: '密码分享',
      exportMarkdown: '导出 Markdown',
      privateDoc: '私密文档',
      lockTip: '该项目为私密分享, 请输入访问密码',
      passwordPlaceholder: '访问密码',
      unlock: '查看文档',
      unlockFailed: '密码错误或不可访问',
      searchPlaceholder: '输入关键字',
      noResult: '无结果',
      uncategorized: '未分类',
      emptyDoc: '# 空文档\n\n该项目还没有内容。',
      copyMarkdown: '复制 Markdown',
      copied: '已复制 Markdown',
      copy: '复制',
      codeCopied: '已复制',
      lineNumbers: '行号',
      fullscreen: '全屏阅读',
      exitFullscreen: '退出全屏',
      toc: '目录',
    },
    login: {
      welcome: '欢迎登录',
      username: '用户名',
      password: '密码',
      usernamePlaceholder: '请输入用户名',
      passwordPlaceholder: '请输入密码',
      submit: '登录',
      success: '登录成功',
      tip: '默认账号：admin / admin123',
      tag: 'JADE WIKI PLATFORM',
      slogan: '温润如玉 · 稳定如石',
    },
  },
  en: {
    wiki: {
      doc: 'Docs',
      publicShare: 'Public',
      passwordShare: 'Password protected',
      exportMarkdown: 'Export Markdown',
      privateDoc: 'Private Docs',
      lockTip: 'This project is shared privately. Enter the access password.',
      passwordPlaceholder: 'Access password',
      unlock: 'View Docs',
      unlockFailed: 'Wrong password or access denied',
      searchPlaceholder: 'Search',
      noResult: 'No results',
      uncategorized: 'Uncategorized',
      emptyDoc: '# Empty\n\nThis project has no content yet.',
      copyMarkdown: 'Copy Markdown',
      copied: 'Markdown copied',
      copy: 'Copy',
      codeCopied: 'Copied',
      lineNumbers: 'Lines',
      fullscreen: 'Fullscreen',
      exitFullscreen: 'Exit Fullscreen',
      toc: 'On this page',
    },
    login: {
      welcome: 'Welcome back',
      username: 'Username',
      password: 'Password',
      usernamePlaceholder: 'Enter your username',
      passwordPlaceholder: 'Enter your password',
      submit: 'Sign in',
      success: 'Signed in',
      tip: 'Default account: admin / admin123',
      tag: 'JADE WIKI PLATFORM',
      slogan: 'Gentle as jade, solid as stone',
    },
  },
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'en',
  messages,
})

document.documentElement.lang = i18n.global.locale.value
