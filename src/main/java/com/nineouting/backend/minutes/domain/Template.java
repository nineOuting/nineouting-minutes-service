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
 * 회의록 양식 (REQ-119~121). ownerUserId가 없으면 기본 양식(누구나 고를 수 있고 고칠 수 없음).
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

    public Template(Long ownerUserId, String name) {
        this.ownerUserId = ownerUserId;
        this.name = name;
    }

    public boolean isDefault() {
        return ownerUserId == null;
    }

    public boolean isUsableBy(Long userId) {
        return isDefault() || ownerUserId.equals(userId);
    }

    public void rename(String name) {
        this.name = name;
    }
}
