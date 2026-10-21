package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.minutes.dto.CommentRequest;
import com.nineouting.backend.minutes.dto.CommentResponse;
import com.nineouting.backend.minutes.dto.ReviewResponse;
import com.nineouting.backend.minutes.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 검토 요청·현황·동의·코멘트·확정 (REQ-106~110) */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** REQ-106, 108 */
    @PostMapping("/api/minutes/{minutesId}/review-request")
    public ApiResponse<Void> requestReview(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        reviewService.requestReview(minutesId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-109 */
    @GetMapping("/api/minutes/{minutesId}/reviews")
    public ApiResponse<List<ReviewResponse>> reviews(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        return ApiResponse.ok(reviewService.reviews(minutesId, user.userId()));
    }

    /** REQ-107, 110 */
    @PostMapping("/api/minutes/{minutesId}/approve")
    public ApiResponse<Void> approve(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        reviewService.approve(minutesId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-108 */
    @PostMapping("/api/minutes/{minutesId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> comment(@CurrentUser AuthenticatedUser user,
                                                                @PathVariable Long minutesId,
                                                                @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reviewService.comment(minutesId, user.userId(), request)));
    }

    @GetMapping("/api/minutes/{minutesId}/comments")
    public ApiResponse<List<CommentResponse>> comments(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        return ApiResponse.ok(reviewService.comments(minutesId, user.userId()));
    }

    @PostMapping("/api/minutes/{minutesId}/comments/{commentId}/resolve")
    public ApiResponse<Void> resolve(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId,
                                     @PathVariable Long commentId) {
        reviewService.resolveComment(minutesId, commentId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-107: 작성자가 확정 */
    @PostMapping("/api/minutes/{minutesId}/confirm")
    public ApiResponse<Void> confirm(@CurrentUser AuthenticatedUser user, @PathVariable Long minutesId) {
        reviewService.confirmByWriter(minutesId, user.userId());
        return ApiResponse.ok();
    }
}
