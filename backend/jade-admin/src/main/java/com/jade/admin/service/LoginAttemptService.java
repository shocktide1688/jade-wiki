package com.jade.admin.service;

import com.jade.admin.entity.SysConfig;
import com.jade.admin.repository.SysConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 登录失败计数（按用户名维度）
 *
 * 本项目未接入 Redis：计数存进程内（InMemoryTtlStore）。
 * 单机语义：重启归零、多实例各自计数。
 *
 * 计数窗口 = sys.account.lockTime 分钟：每次失败刷新 TTL，窗口内无新失败则归零；
 * 连续失败达到 sys.account.passwordRetryCount 后要求验证码，直到登录成功重置。
 * 配置来自 sys_config（V5 预置键，改库即生效）。
 */
@ApplicationScoped
public class LoginAttemptService {

    private static final String KEY_PREFIX = "login:fail:";
    private static final String CFG_CAPTCHA_ENABLED = "sys.account.captchaEnabled";
    private static final String CFG_RETRY_COUNT = "sys.account.passwordRetryCount";
    private static final String CFG_LOCK_TIME = "sys.account.lockTime";
    private static final int DEFAULT_RETRY_COUNT = 5;
    private static final int DEFAULT_LOCK_TIME_MINUTES = 10;

    @Inject
    SysConfigRepository configRepository;

    private final InMemoryTtlStore memory = new InMemoryTtlStore();

    /**
     * 是否要求验证码：captchaEnabled=true 始终要求；否则失败次数达到阈值后要求
     */
    public boolean captchaRequired(String username) {
        if (Boolean.parseBoolean(getConfig(CFG_CAPTCHA_ENABLED, "false"))) {
            return true;
        }
        int count = failCount(username);
        return count > 0 && count >= retryCount();
    }

    public int failCount(String username) {
        String v = memory.get(KEY_PREFIX + normalize(username));
        return v == null ? 0 : Integer.parseInt(v);
    }

    /** 记录一次失败：计数 +1 并刷新窗口 */
    public void recordFailure(String username) {
        memory.increment(KEY_PREFIX + normalize(username), lockTimeMinutes() * 60L);
    }

    /** 登录成功后清除计数 */
    public void reset(String username) {
        memory.del(KEY_PREFIX + normalize(username));
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
