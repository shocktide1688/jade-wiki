package com.jade.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

@ApplicationScoped
public class SysUserRoleRepository implements PanacheRepository<Object> {

    @Inject
    EntityManager em;

    public void deleteByUserId(Long userId) {
        em.createNativeQuery("DELETE FROM sys_user_role WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
    }

    /**
     * 幂等插入：先查后插，避免方言差异（PG/SQLite 的 ON CONFLICT 在 MySQL 不存在）
     */
    public void insert(Long userId, Long roleId) {
        if (exists(userId, roleId)) {
            return;
        }
        em.createNativeQuery("INSERT INTO sys_user_role (user_id, role_id) VALUES (?1, ?2)")
                .setParameter(1, userId)
                .setParameter(2, roleId)
                .executeUpdate();
    }

    public boolean exists(Long userId, Long roleId) {
        return !em.createNativeQuery("SELECT 1 FROM sys_user_role WHERE user_id = ?1 AND role_id = ?2")
                .setParameter(1, userId)
                .setParameter(2, roleId)
                .getResultList()
                .isEmpty();
    }
}
