package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiItemMember;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Wiki 权限模型:
 *   项目所有者(0) > 可编辑成员(1) > 只读成员(2) > 非成员(-1)
 *   admin 角色绕过一切检查（管理端）
 */
@ApplicationScoped
public class WikiPermissionService {

    /** 当前操作者（从 JWT 解析） */
    public record Actor(Long userId, String username, boolean admin) {}

    public Actor actor(JsonWebToken jwt) {
        boolean admin = jwt.getGroups() != null && jwt.getGroups().contains("admin");
        return new Actor(Long.valueOf(jwt.getSubject()), jwt.getName(), admin);
    }

    /** 0=所有者 1=可编辑 2=只读 -1=非成员 */
    public int roleOf(WikiItem item, Actor actor) {
        if (item.creatorId.equals(actor.userId())) {
            return WikiItemMember.GROUP_OWNER;
        }
        WikiItemMember member = WikiItemMember.findByItemAndUser(item.id, actor.userId());
        return member == null ? -1 : member.memberGroup;
    }

    public WikiItem requireItem(Long itemId) {
        WikiItem item = WikiItem.findActiveById(itemId);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        return item;
    }

    public void requireReadable(WikiItem item, Actor actor) {
        if (actor.admin() || "public".equals(item.visibility) || roleOf(item, actor) >= 0) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该项目");
    }

    public void requireEditable(WikiItem item, Actor actor) {
        if (actor.admin()) {
            return;
        }
        int role = roleOf(item, actor);
        if (role == WikiItemMember.GROUP_OWNER || role == WikiItemMember.GROUP_EDITABLE) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权编辑该项目");
    }

    public void requireOwner(WikiItem item, Actor actor) {
        if (actor.admin()) {
            return;
        }
        if (roleOf(item, actor) != WikiItemMember.GROUP_OWNER) {
            throw new BizException(ResultCode.FORBIDDEN, "仅项目所有者可操作");
        }
    }
}
