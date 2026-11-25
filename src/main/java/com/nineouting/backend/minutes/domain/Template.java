package com.nineouting.backend.minutes.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회의록 양식 (REQ-119~121). ownerUserId와 workspaceId가 모두 없으면 공용 기본 양식(누구나 고를 수 있고 고칠 수 없음).
 * personalDefault는 공용 기본 양식을 고쳐 만든 내 기본 양식 (QA 64). 내 목록에서 공용 기본 양식 자리를 대신한다.
 * workspaceId가 있으면 워크스페이스 양식함의 사본 (REQ-213): 그 워크스페이스 멤버가 쓰고 아무도 고치지 않는다
 */
@Getter
@Entity
@Table(name = "templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100)
    private String description;

    /** 레이아웃 설정 JSON (TemplateSettings) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String layout;

    @Column(name = "personal_default", nullable = false)
    private boolean personalDefault;

    @Column(name = "workspace_id")
    private Long workspaceId;

    public Template(Long ownerUserId, String name, String description, String layout) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.description = description;
        this.layout = layout;
    }

    /** 공용 기본 양식을 처음 고칠 때 만드는 내 기본 양식 */
    public static Template personalDefault(Long ownerUserId, String name, String description, String layout) {
        Template template = new Template(ownerUserId, name, description, layout);
        template.personalDefault = true;
        return template;
    }

    /** REQ-213 워크스페이스 기본 양식으로 정한 양식의 사본 (이름, 설명, 배치를 그대로 옮긴다) */
    public static Template workspaceCopy(Long workspaceId, Template source) {
        Template template = new Template(null, source.name, source.description, source.layout);
        template.workspaceId = workspaceId;
        return template;
    }

    public boolean isDefault() {
        return ownerUserId == null && workspaceId == null;
    }

    public boolean isWorkspaceTemplate() {
        return workspaceId != null;
    }

    /** 공용 기본 양식이나 내 양식. 워크스페이스 양식은 멤버인지 따로 확인한다 */
    public boolean isUsableBy(Long userId) {
        return isDefault() || (ownerUserId != null && ownerUserId.equals(userId));
    }

    public void change(String name, String description, String layout) {
        this.name = name;
        this.description = description;
        this.layout = layout;
    }
}
