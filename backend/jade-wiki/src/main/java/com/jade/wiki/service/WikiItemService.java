package com.jade.wiki.service;

import com.jade.common.api.PageResult;
import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiItemMember;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiItemService {

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiOperLogRecorder operLog;

    @Inject
    SysUserRepository userRepository;

    public PageResult<WikiDtos.ItemView> page(Actor actor, String keyword, int page, int size) {
        PanacheQuery<WikiItem> query = WikiItem.findMine(actor.userId(), keyword);
        long total = query.count();
        List<WikiItem> items = query.page(Page.of(page - 1, size)).list();
        List<WikiDtos.ItemView> views = items.stream()
                .map(item -> toView(item, actor)).toList();
        return PageResult.of(views, total, page, size);
    }

    public WikiDtos.ItemView detail(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);
        return toView(item, actor);
    }

    @Transactional
    public WikiDtos.ItemView create(Actor actor, WikiDtos.ItemCreateRequest req) {
        WikiItem item = new WikiItem();
        item.tenantId = null; // M1: 个人项目不挂租户, 管理端接入后由 TenantContext 注入
        item.itemName = req.itemName();
        item.itemDescription = req.itemDescription();
        item.visibility = normalizeVisibility(req.visibility());
        item.password = req.password();
        item.creatorId = actor.userId();
        item.creatorName = actor.username();
        item.persist();
        operLog.record(actor, "文档管理", 1, "WikiItemService.create[item=" + item.id + "]");

        WikiItemMember owner = new WikiItemMember();
        owner.itemId = item.id;
        owner.userId = actor.userId();
        owner.memberGroup = WikiItemMember.GROUP_OWNER;
        owner.persist();

        return toView(item, actor);
    }

    @Transactional
    public WikiDtos.ItemView update(Actor actor, Long itemId, WikiDtos.ItemUpdateRequest req) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        item.itemName = req.itemName();
        item.itemDescription = req.itemDescription();
        item.visibility = normalizeVisibility(req.visibility());
        if (req.password() != null) {
            item.password = req.password().isBlank() ? null : req.password();
        }
        operLog.record(actor, "文档管理", 2, "WikiItemService.update[item=" + itemId + "]");
        return toView(item, actor);
    }

    @Transactional
    public void delete(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        item.deleted = true;
        operLog.record(actor, "文档管理", 3, "WikiItemService.delete[item=" + itemId + "]");
    }

    public List<WikiDtos.MemberView> members(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);
        return WikiItemMember.listByItem(itemId).stream().map(m -> {
            SysUser user = userRepository.findById(m.userId);
            return new WikiDtos.MemberView(m.id, m.userId,
                    user == null ? "已删除用户" : user.username,
                    user == null ? null : user.nickname,
                    m.memberGroup);
        }).toList();
    }

    @Transactional
    public void addMember(Actor actor, Long itemId, WikiDtos.MemberAddRequest req) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        SysUser user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "用户不存在: " + req.username()));
        if (WikiItemMember.findByItemAndUser(itemId, user.id) != null) {
            throw new BizException(ResultCode.CONFLICT, "该用户已是项目成员");
        }
        WikiItemMember member = new WikiItemMember();
        member.itemId = itemId;
        member.userId = user.id;
        member.memberGroup = normalizeGroup(req.memberGroup());
        member.persist();
        operLog.record(actor, "文档管理", 1, "WikiItemService.addMember[item=" + itemId + ",user=" + user.id + "]");
    }

    @Transactional
    public void updateMember(Actor actor, Long itemId, Long memberId, WikiDtos.MemberUpdateRequest req) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        WikiItemMember member = WikiItemMember.findById(memberId);
        if (member == null || !member.itemId.equals(itemId)) {
            throw new BizException(ResultCode.NOT_FOUND, "成员记录不存在");
        }
        if (member.memberGroup == WikiItemMember.GROUP_OWNER) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "不能修改所有者角色");
        }
        member.memberGroup = normalizeGroup(req.memberGroup());
        operLog.record(actor, "文档管理", 2, "WikiItemService.updateMember[item=" + itemId + ",member=" + memberId + "]");
    }

    @Transactional
    public void removeMember(Actor actor, Long itemId, Long memberId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        WikiItemMember member = WikiItemMember.findById(memberId);
        if (member == null || !member.itemId.equals(itemId)) {
            throw new BizException(ResultCode.NOT_FOUND, "成员记录不存在");
        }
        if (member.memberGroup == WikiItemMember.GROUP_OWNER) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "不能移除所有者");
        }
        member.delete();
        operLog.record(actor, "文档管理", 3, "WikiItemService.removeMember[item=" + itemId + ",member=" + memberId + "]");
    }

    private WikiDtos.ItemView toView(WikiItem item, Actor actor) {
        int role = item.creatorId.equals(actor.userId())
                ? WikiItemMember.GROUP_OWNER : roleOrMinusOne(item, actor);
        return new WikiDtos.ItemView(item.id, item.itemName, item.itemDescription,
                item.visibility, item.itemType, item.creatorId, item.creatorName,
                item.isArchive, item.createdAt, item.updatedAt,
                role, WikiItemMember.countByItem(item.id));
    }

    private int roleOrMinusOne(WikiItem item, Actor actor) {
        WikiItemMember m = WikiItemMember.findByItemAndUser(item.id, actor.userId());
        return m == null ? -1 : m.memberGroup;
    }

    private String normalizeVisibility(String visibility) {
        return "public".equals(visibility) ? "public" : "private";
    }

    private int normalizeGroup(Integer group) {
        if (group == null || group < WikiItemMember.GROUP_OWNER || group > WikiItemMember.GROUP_READONLY) {
            return WikiItemMember.GROUP_EDITABLE;
        }
        return group;
    }
}
