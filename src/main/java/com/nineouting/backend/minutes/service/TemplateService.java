package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.client.AccessibleFolder;
import com.nineouting.backend.common.client.WorkspaceClient;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.minutes.domain.Template;
import com.nineouting.backend.minutes.domain.TemplateFavorite;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.domain.TemplateWorkspaceShare;
import com.nineouting.backend.minutes.dto.BackgroundUploadRequest;
import com.nineouting.backend.minutes.dto.BackgroundUploadResponse;
import com.nineouting.backend.minutes.dto.TemplateItemRequest;
import com.nineouting.backend.minutes.dto.TemplateRequest;
import com.nineouting.backend.minutes.dto.TemplateResponse;
import com.nineouting.backend.minutes.error.MinutesErrorCode;
import com.nineouting.backend.minutes.repository.TemplateFavoriteRepository;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import com.nineouting.backend.minutes.repository.TemplateWorkspaceShareRepository;
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
    private final TemplateWorkspaceShareRepository shareRepository;
    private final WorkspaceClient workspaceClient;
    private final Clock clock;

    /** 기본 양식이 먼저, 그다음 내 양식, 그다음 내 워크스페이스에 공유된 양식 (REQ-199) */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long userId) {
        List<Template> templates = new ArrayList<>(templateRepository.findUsableBy(userId));
        Set<Long> listed = templates.stream().map(Template::getId).collect(Collectors.toSet());
        List<Long> sharedIds = shareRepository.findByWorkspaceIdIn(workspaceIdsOf(userId)).stream()
                .map(TemplateWorkspaceShare::getTemplateId)
                .filter(id -> !listed.contains(id))
                .distinct()
                .toList();
        templates.addAll(templateRepository.findAllById(sharedIds));
        Map<Long, List<TemplateItem>> items = itemRepository.findByTemplateIdInOrderByPosition(
                        templates.stream().map(Template::getId).toList()).stream()
                .collect(Collectors.groupingBy(TemplateItem::getTemplateId));
        Set<Long> favorites = favoriteRepository.findByUserId(userId).stream()
                .map(TemplateFavorite::getTemplateId)
                .collect(Collectors.toSet());
        return templates.stream()
                .map(template -> {
                    TemplateResponse response = response(template, items.getOrDefault(template.getId(), List.of()),
                            favorites.contains(template.getId()));
                    return sharedIds.contains(template.getId()) ? response.asShared() : response;
                })
                .toList();
    }

    /** REQ-123 양식 즐겨찾기 등록·해제. 기본 양식도 된다. 여러 번 해도 결과가 같다 */
    @Transactional
    public void favorite(Long templateId, Long userId, boolean favorite) {
        templateRepository.findById(templateId)
                .filter(found -> isUsable(found, userId, workspaceIdsOf(userId)))
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
        LocalDateTime now = LocalDateTime.now(clock);
        templateRepository.touch(templateId, now);
        return response(template, saveItems(templateId, request.items()),
                favoriteRepository.existsByTemplateIdAndUserId(templateId, userId), now);
    }

    @Transactional
    public void delete(Long templateId, Long userId) {
        Template template = requireOwn(templateId, userId);
        shareRepository.deleteByTemplateId(templateId);
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

    /**
     * REQ-199 워크스페이스 기본 양식으로 정한 내 양식을 그 워크스페이스 멤버 모두 쓰게 한다 (화면이 기본 양식을 바꿀 때 부른다).
     * 내 양식만, 내가 멤버인 워크스페이스에만. 서비스 기본 양식은 원래 모두 쓰므로 공유하지 않는다
     */
    @Transactional
    public void share(Long templateId, Long workspaceId, Long userId, boolean shared) {
        Template template = requireOwn(templateId, userId);
        if (!workspaceIdsOf(userId).contains(workspaceId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        boolean exists = shareRepository.existsByTemplateIdAndWorkspaceId(template.getId(), workspaceId);
        if (shared && !exists) {
            shareRepository.save(new TemplateWorkspaceShare(template.getId(), workspaceId, LocalDateTime.now(clock)));
        } else if (!shared && exists) {
            shareRepository.deleteOne(template.getId(), workspaceId);
        }
    }

    /** 이 워크스페이스의 회의록에 이 양식을 쓸 수 있는지: 기본 양식, 내 양식, 이 워크스페이스에 공유된 양식 */
    @Transactional(readOnly = true)
    public boolean isUsableIn(Template template, Long userId, Long workspaceId) {
        return template.isUsableBy(userId)
                || (workspaceId != null && shareRepository.existsByTemplateIdAndWorkspaceId(template.getId(), workspaceId));
    }

    /** 회의록 화면이 양식 칸 설정을 그대로 그리도록 함께 준다 (참석자가 양식 목록에 없는 개인 양식이어도). 없으면 null */
    @Transactional(readOnly = true)
    public TemplateResponse snapshot(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findById(templateId)
                .map(template -> response(template, itemRepository.findByTemplateIdOrderByPosition(templateId), false))
                .orElse(null);
    }

    private boolean isUsable(Template template, Long userId, Set<Long> workspaceIds) {
        return template.isUsableBy(userId) || shareRepository.findByWorkspaceIdIn(workspaceIds).stream()
                .anyMatch(share -> share.getTemplateId().equals(template.getId()));
    }

    private Set<Long> workspaceIdsOf(Long userId) {
        return workspaceClient.accessibleFolders(userId).stream()
                .map(AccessibleFolder::workspaceId)
                .collect(Collectors.toSet());
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
        return response(template, items, favorite, template.getUpdatedAt());
    }

    private TemplateResponse response(Template template, List<TemplateItem> items, boolean favorite,
                                      LocalDateTime updatedAt) {
        return new TemplateResponse(template.getId(), template.getName(), template.getDescription(), template.isDefault(),
                items.stream()
                        .map(item -> new TemplateResponse.TemplateItemResponse(item.getTitle(), item.getDescription(),
                                settings.read(item.getSettings())))
                        .toList(),
                layoutToShow(template.getLayout()), favorite, updatedAt, false);
    }
}
