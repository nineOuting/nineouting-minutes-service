package com.nineouting.backend.minutes.service;

import com.nineouting.backend.minutes.config.StorageProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 양식 배경 이미지는 브라우저가 S3에 직접 올리고, 보여 줄 때도 잠깐만 쓸 수 있는 주소를 만든다 (REQ-157) */
@Component
@RequiredArgsConstructor
public class BackgroundStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public String uploadUrl(String key, String contentType) {
        return s3Presigner.presignPutObject(request -> request
                        .signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(put -> put.bucket(properties.bucket()).key(key).contentType(contentType)))
                .url().toString();
    }

    public String downloadUrl(String key) {
        return s3Presigner.presignGetObject(request -> request
                        .signatureDuration(properties.downloadUrlTtl())
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)))
                .url().toString();
    }

    /** 올라간 파일의 크기(바이트). 아직 올라가지 않았으면 빈 값 */
    public Optional<Long> size(String key) {
        try {
            return Optional.of(s3Client.headObject(head -> head.bucket(properties.bucket()).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
