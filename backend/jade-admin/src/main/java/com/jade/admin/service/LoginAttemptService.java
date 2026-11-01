package com.jade.admin.service;

import com.jade.admin.entity.SysConfig;
import com.jade.admin.entity.SysLoginLog;
import com.jade.admin.repository.SysConfigRepository;
import com.jade.admin.repository.SysLoginLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

/**
 * 登录失败计数（基于 sys_login_log 登录日志表）
 *
 * 本项目未接 Redis：进程内计数会因重启/热重载清零，导致"验证码行出现后
 * 重启后端即可绕过"。改为直接从登录日志统计——日志由 LoginLogListener 在
 * 登录事务中同步落库，重启不丢、多实例天然一致。
 *
 * 失败次数 = 最近 lockTime 分钟内、且晚于最近一次成功登录的失败记录数：
 *  - 登录成功后自然归零（成功日志成为新的起点），无需显式重置
 *  - 窗口过期自动失效，语义与 Redis 版的 TTL 计数一致
 *
 * 配置来自 sys_config（V5 预置键，改库即生效）。
 */
@ApplicationScoped
public class LoginAttemptService {

    private static final String CFG_CAPTCHA_ENABLED = "sys.account.captchaEnabled";
    private static final String CFG_RETRY_COUNT = "sys.account.passwordRetryCount";
    private static final String CFG_LOCK_TIME = "sys.account.lockTime";
    private static final int DEFAULT_RETRY_COUNT = 5;
    private static final int DEFAULT_LOCK_TIME_MINUTES = 10;

    @Inject
    SysLoginLogRepository loginLogRepository;

    @Inject
    SysConfigRepository configRepository;

    /**
     * 是否要求验证码：开关开启始终要求；否则失败次数达到阈值后要求
     */
    public boolean captchaRequired(String username) {
        if (Boolean.parseBoolean(getConfig("sys.captcha.enabled", "false"))) {
            return true;
        }
        return failCount(username) >= retryCount();
    }

    public int failCount(String username) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(lockTimeMinutes());
        LocalDateTime lastSuccess = lastSuccessAt(username);
        // 无成功记录时用 epoch 兜底; LocalDateTime.MIN 超出 PG timestamp 范围会报错
        LocalDateTime since = lastSuccess == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : lastSuccess;
        return (int) loginLogRepository.count(
                "lower(username) = ?1 and status = 0 and loginTime > ?2 and loginTime > ?3",
                normalize(username), windowStart, since);
    }

    /** 失败记录由 LoginLogListener 同步落库（AuthService 已 fire LoginEvent），无需额外记账 */
    public void recordFailure(String username) {
    }

    /** 成功记录已落库，失败计数自然归零 */
    public void reset(String username) {
    }

    private LocalDateTime lastSuccessAt(String username) {
        SysLoginLog last = loginLogRepository.find(
                "lower(username) = ?1 and status = 1 order by loginTime desc",
                normalize(username)).firstResult();
        return last == null ? null : last.loginTime;
    }

    private String getConfig(String key, String defaultValue) {
        SysConfig c = configRepository.find("configKey = ?1 and deleted = false", key).firstResult();
        return c == null || c.configValue == null || c.configValue.isBlank() ? defaultValue : c.configValue;
    }

    private int retryCount() {
        try {
            return Math.max(1, Integer.parseInt(getConfig(CFG_RETRY_COUNT, String.valueOf(DEFAULT_RETRY_COUNT))));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_COUNT;
        }
    }

    private int lockTimeMinutes() {
        try {
            return Math.max(1, Integer.parseInt(getConfig(CFG_LOCK_TIME, String.valueOf(DEFAULT_LOCK_TIME_MINUTES))));
        } catch (NumberFormatException e) {
            return DEFAULT_LOCK_TIME_MINUTES;
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
