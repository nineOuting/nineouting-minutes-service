package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Template;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.dto.TemplateItemRequest;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의록 양식 관리 (REQ-119~121). 개인 양식 + 기본 양식 1개 (8장 결정 5).
 * 양식을 고치거나 지워도 이미 만든 회의록의 칸은 그대로다.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateItemRepository itemRepository;
    private final TemplateSettings settings;

    /** 기본 양식이 먼저, 그다음 내 양식 */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long userId) {
        List<Template> templates = templateRepository.findUsableBy(userId);
        Map<Long, List<TemplateItem>> items = itemRepository.findByTemplateIdInOrderByPosition(
                        templates.stream().map(Template::getId).toList()).stream()
                .collect(Collectors.groupingBy(TemplateItem::getTemplateId));
        return templates.stream()
                .map(template -> response(template, items.getOrDefault(template.getId(), List.of())))
                .toList();
    }

    @Transactional
    public TemplateResponse create(Long userId, TemplateRequest request) {
        Template template = templateRepository.save(new Template(userId, request.name(), request.description(),
                settings.write(request.layout())));
        return response(template, saveItems(template.getId(), request.items()));
    }

    @Transactional
    public TemplateResponse update(Long templateId, Long userId, TemplateRequest request) {
        Template template = requireOwn(templateId, userId);
        template.change(request.name(), request.description(), settings.write(request.layout()));
        itemRepository.deleteByTemplateId(templateId);
        return response(template, saveItems(templateId, request.items()));
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

    private List<TemplateItem> saveItems(Long templateId, List<TemplateItemRequest> requests) {
        List<TemplateItem> saved = new ArrayList<>();
        for (int position = 0; position < requests.size(); position++) {
            TemplateItemRequest request = requests.get(position);
            saved.add(itemRepository.save(new TemplateItem(templateId, position, request.title(), request.description(),
                    settings.write(request.settings()))));
        }
        return saved;
    }

    private TemplateResponse response(Template template, List<TemplateItem> items) {
        return new TemplateResponse(template.getId(), template.getName(), template.getDescription(), template.isDefault(),
                items.stream()
                        .map(item -> new TemplateResponse.TemplateItemResponse(item.getTitle(), item.getDescription(),
                                settings.read(item.getSettings())))
                        .toList(),
                settings.read(template.getLayout()));
    }
}
