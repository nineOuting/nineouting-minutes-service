package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Template;
import com.nineouting.backend.minutes.domain.TemplateFavorite;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.dto.BackgroundUploadRequest;
import com.nineouting.backend.minutes.dto.BackgroundUploadResponse;
import com.nineouting.backend.minutes.dto.TemplateItemRequest;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.TemplateFavoriteRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    /** REQ-157 레이아웃 안 배경 이미지 위치. 응답에는 잠깐 쓸 수 있는 주소(backgroundImageUrl)를 더한다 */
    static final String BACKGROUND_KEY = "backgroundImageKey";
    static final String BACKGROUND_URL = "backgroundImageUrl";
    private static final String BACKGROUND_PREFIX = "template-backgrounds/";
    private static final long MAX_BACKGROUND_BYTES = 5L * 1024 * 1024;
    private static final Set<String> BACKGROUND_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final TemplateRepository templateRepository;
    private final BackgroundStorage backgroundStorage;
    private final TemplateItemRepository itemRepository;
    private final TemplateSettings settings;
    private final TemplateFavoriteRepository favoriteRepository;
    private final Clock clock;

    /** 기본 양식이 먼저, 그다음 내 양식 */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long userId) {
        List<Template> templates = templateRepository.findUsableBy(userId);
        Map<Long, List<TemplateItem>> items = itemRepository.findByTemplateIdInOrderByPosition(
                        templates.stream().map(Template::getId).toList()).stream()
                .collect(Collectors.groupingBy(TemplateItem::getTemplateId));
        Set<Long> favorites = favoriteRepository.findByUserId(userId).stream()
                .map(TemplateFavorite::getTemplateId)
                .collect(Collectors.toSet());
        return templates.stream()
                .map(template -> response(template, items.getOrDefault(template.getId(), List.of()),
                        favorites.contains(template.getId())))
                .toList();
    }

    /** REQ-123 양식 즐겨찾기 등록·해제. 기본 양식도 된다. 여러 번 해도 결과가 같다 */
    @Transactional
    public void favorite(Long templateId, Long userId, boolean favorite) {
        templateRepository.findById(templateId)
                .filter(found -> found.isUsableBy(userId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean exists = favoriteRepository.existsByTemplateIdAndUserId(templateId, userId);
        if (favorite && !exists) {
            favoriteRepository.save(new TemplateFavorite(templateId, userId, LocalDateTime.now(clock)));
        } else if (!favorite && exists) {
            favoriteRepository.deleteByTemplateIdAndUserId(templateId, userId);
        }
    }

    @Transactional
    public TemplateResponse create(Long userId, TemplateRequest request) {
        Template template = templateRepository.save(new Template(userId, request.name(), request.description(),
                settings.write(layoutToSave(request.layout()))));
        return response(template, saveItems(template.getId(), request.items()), false);
    }

    @Transactional
    public TemplateResponse update(Long templateId, Long userId, TemplateRequest request) {
        Template template = requireOwn(templateId, userId);
        template.change(request.name(), request.description(), settings.write(layoutToSave(request.layout())));
        itemRepository.deleteByTemplateId(templateId);
        return response(template, saveItems(templateId, request.items()),
                favoriteRepository.existsByTemplateIdAndUserId(templateId, userId));
    }

    @Transactional
    public void delete(Long templateId, Long userId) {
        Template template = requireOwn(templateId, userId);
        itemRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
    }

    /** REQ-157 배경 이미지 업로드 주소. 양식을 저장할 때 imageKey를 layout.backgroundImageKey에 넣는다 */
    public BackgroundUploadResponse backgroundUploadUrl(Long userId, BackgroundUploadRequest request) {
        if (!BACKGROUND_TYPES.contains(request.contentType()) || request.size() > MAX_BACKGROUND_BYTES) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_BACKGROUND_INVALID);
        }
        String key = BACKGROUND_PREFIX + userId + "/" + UUID.randomUUID();
        return new BackgroundUploadResponse(key, backgroundStorage.uploadUrl(key, request.contentType()),
                request.contentType());
    }

    /**
     * 저장할 레이아웃: 응답에 붙였던 주소는 빼고, 배경 이미지 위치는 실제로 올라간 5MB 이하 파일인지 확인한다.
     * 다른 양식을 복사하면 같은 이미지를 함께 쓴다 (그래서 양식을 지워도 이미지는 지우지 않는다)
     */
    private Map<String, Object> layoutToSave(Map<String, Object> layout) {
        Map<String, Object> saved = new LinkedHashMap<>(layout);
        saved.remove(BACKGROUND_URL);
        Object key = saved.get(BACKGROUND_KEY);
        if (key == null || (key instanceof String text && text.isBlank())) {
            saved.remove(BACKGROUND_KEY);
            return saved;
        }
        if (!(key instanceof String text) || !text.startsWith(BACKGROUND_PREFIX)) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_BACKGROUND_INVALID);
        }
        long size = backgroundStorage.size(text)
                .orElseThrow(() -> new BusinessException(MinutesErrorCode.TEMPLATE_BACKGROUND_NOT_UPLOADED));
        if (size > MAX_BACKGROUND_BYTES) {
            throw new BusinessException(MinutesErrorCode.TEMPLATE_BACKGROUND_INVALID);
        }
        return saved;
    }

    /** 응답할 레이아웃: 배경 이미지가 있으면 1시간 동안 쓸 수 있는 주소를 더한다 */
    private Map<String, Object> layoutToShow(String json) {
        Map<String, Object> layout = new LinkedHashMap<>(settings.read(json));
        if (layout.get(BACKGROUND_KEY) instanceof String key && !key.isBlank()) {
            layout.put(BACKGROUND_URL, backgroundStorage.downloadUrl(key));
        }
        return layout;
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

    private TemplateResponse response(Template template, List<TemplateItem> items, boolean favorite) {
        return new TemplateResponse(template.getId(), template.getName(), template.getDescription(), template.isDefault(),
                items.stream()
                        .map(item -> new TemplateResponse.TemplateItemResponse(item.getTitle(), item.getDescription(),
                                settings.read(item.getSettings())))
                        .toList(),
                layoutToShow(template.getLayout()), favorite);
    }
}
