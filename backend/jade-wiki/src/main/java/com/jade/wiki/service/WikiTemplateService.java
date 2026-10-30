package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.entity.WikiTemplate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiTemplateService {

    public List<WikiTemplate> list() {
        return WikiTemplate.listActive();
    }

    @Transactional
    public WikiTemplate create(Actor actor, String name, String content) {
        WikiTemplate template = new WikiTemplate();
        template.templateName = name;
        template.templateContent = content;
        template.isBuiltin = 0;
        template.creatorId = actor.userId();
        template.creatorName = actor.username();
        template.persist();
        return template;
    }

    @Transactional
    public void delete(Actor actor, Long templateId) {
        WikiTemplate template = WikiTemplate.findById(templateId);
        if (template == null || template.deleted) {
            throw new BizException(ResultCode.NOT_FOUND, "模板不存在");
        }
        if (template.isBuiltin == 1) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "系统内置模板不可删除");
        }
        if (!actor.admin() && !actor.userId().equals(template.creatorId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除自己创建的模板");
        }
        template.deleted = true;
    }
}
