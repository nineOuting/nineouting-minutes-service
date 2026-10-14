package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Template;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의록 양식 관리 (REQ-106~108). 개인 양식 + 기본 양식 1개 (8장 결정 5).
 * 양식을 고치거나 지워도 이미 만든 회의록의 칸은 그대로다.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateItemRepository itemRepository;

    /** 기본 양식이 먼저, 그다음 내 양식 */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long userId) {
        List<Template> templates = templateRepository.findUsableBy(userId);
        Map<Long, List<String>> items = itemRepository.findByTemplateIdInOrderByPosition(
                        templates.stream().map(Template::getId).toList()).stream()
                .collect(Collectors.groupingBy(TemplateItem::getTemplateId,
                        Collectors.mapping(TemplateItem::getTitle, Collectors.toList())));
        return templates.stream()
                .map(template -> new TemplateResponse(template.getId(), template.getName(), template.isDefault(),
                        items.getOrDefault(template.getId(), List.of())))
                .toList();
    }

    @Transactional
    public TemplateResponse create(Long userId, TemplateRequest request) {
        Template template = templateRepository.save(new Template(userId, request.name()));
        saveItems(template.getId(), request.items());
        return new TemplateResponse(template.getId(), template.getName(), false, request.items());
    }

    @Transactional
    public TemplateResponse update(Long templateId, Long userId, TemplateRequest request) {
        Template template = requireOwn(templateId, userId);
        template.rename(request.name());
        itemRepository.deleteByTemplateId(templateId);
        saveItems(templateId, request.items());
        return new TemplateResponse(templateId, template.getName(), false, request.items());
    }

    @Transactional
    public void delete(Long templateId, Long userId) {
        Template template = requireOwn(templateId, userId);
        itemRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
    }

    /** 다른 사람의 양식은 없는 것처럼(404), 기본 양식은 고칠 수 없다(403) */
    private Template requireOwn(Long templateId, Long userId) {
        Template template = templateRepository.findById(templateId)
                .filter(found -> found.isUsableBy(userId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (template.isDefault()) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_NOT_EDITABLE);
        }
        return template;
    }

    private void saveItems(Long templateId, List<String> titles) {
        for (int position = 0; position < titles.size(); position++) {
            itemRepository.save(new TemplateItem(templateId, position, titles.get(position)));
        }
    }
}
