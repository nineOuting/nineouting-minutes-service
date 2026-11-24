package com.nineouting.backend.minutes.service;

import com.nineouting.backend.minutes.config.StorageProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 양식 배경 이미지(REQ-157)와 가져올 양식 파일(REQ-195)은 브라우저가 S3에 직접 올리고,
 * 보여 주거나 AI 서버가 내려받을 때도 잠깐만 쓸 수 있는 주소를 만든다
 */
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
        return downloadUrl(key, properties.downloadUrlTtl());
    }

    public String downloadUrl(String key, Duration ttl) {
        return s3Presigner.presignGetObject(request -> request
                        .signatureDuration(ttl)
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)))
                .url().toString();
    }

    /** 다 쓴 파일을 지운다. 이미 없어도 괜찮다 */
    public void delete(String key) {
        s3Client.deleteObject(delete -> delete.bucket(properties.bucket()).key(key));
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
