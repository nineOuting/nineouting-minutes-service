package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.common.client.UserSummary;

/** 참석자·작성자·코멘트 작성자 표시용. user-service에 없으면 이름이 null이다. */
public record PersonResponse(Long userId, String name, String profileImageUrl) {

    public static PersonResponse of(Long userId, UserSummary user) {
        return user == null
                ? new PersonResponse(userId, null, null)
                : new PersonResponse(userId, user.name(), user.profileImageUrl());
    }
}
