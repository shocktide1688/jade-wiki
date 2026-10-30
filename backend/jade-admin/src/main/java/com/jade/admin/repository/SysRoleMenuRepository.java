package com.jade.admin.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class SysRoleMenuRepository {

    @Inject
    EntityManager em;

    /**
     * 用 native SQL 查 sys_role.menu_id, 因为 sys_role_menu 是连接表,
     * 没建对应的 JPA 实体（避免 join 表的额外 JPA 维护成本）
     */
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        @SuppressWarnings("unchecked")
        List<Number> raw = em.createNativeQuery("SELECT menu_id FROM sys_role_menu WHERE role_id = ?1 ORDER BY menu_id")
                .setParameter(1, roleId)
                .getResultList();
        return raw.stream().map(Number::longValue).toList();
    }

    public void deleteByRoleId(Long roleId) {
        em.createNativeQuery("DELETE FROM sys_role_menu WHERE role_id = ?1")
                .setParameter(1, roleId)
                .executeUpdate();
    }

    /**
     * 幂等插入：先查后插，避免方言差异（PG/SQLite 的 ON CONFLICT 在 MySQL 不存在）
     */
    public void insert(Long roleId, Long menuId) {
        if (exists(roleId, menuId)) {
            return;
        }
        em.createNativeQuery("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?1, ?2)")
                .setParameter(1, roleId)
                .setParameter(2, menuId)
                .executeUpdate();
    }

    public boolean exists(Long roleId, Long menuId) {
        return !em.createNativeQuery("SELECT 1 FROM sys_role_menu WHERE role_id = ?1 AND menu_id = ?2")
                .setParameter(1, roleId)
                .setParameter(2, menuId)
                .getResultList()
                .isEmpty();
    }
}
