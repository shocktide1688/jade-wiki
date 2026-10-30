package com.jade.wiki.service;

import com.jade.admin.entity.SysOperLog;
import com.jade.admin.repository.SysOperLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

import static com.jade.wiki.service.WikiPermissionService.Actor;

/**
 * 操作日志记录器（显式调用, 不依赖 AOP 织入）
 *
 * 背景: Quarkus dev 模式下主模块 REST 资源类上的 @Log 拦截器不织入
 * （admin jar 内的 controller 可织入, 原因在 ArC augment 细节）,
 * 业务写操作改为在 service 层显式记录, 行为确定且可携带业务上下文。
 *
 * 必须在 @Transactional 方法内调用（同事务落库）; 任何异常不影响业务。
 */
@ApplicationScoped
public class WikiOperLogRecorder {

    @Inject
    SysOperLogRepository operLogRepository;

    public void record(Actor actor, String title, int businessType, String detail) {
        try {
            SysOperLog log = new SysOperLog();
            log.title = title;
            log.businessType = (short) businessType;
            log.method = detail;
            if (actor != null) {
                log.userId = actor.userId();
                log.username = actor.username();
            }
            log.operTime = LocalDateTime.now();
            operLogRepository.persist(log);
        } catch (Exception ignored) {
            // 日志失败不影响业务
        }
    }
}
