package com.nineouting.backend.minutes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 회의록·검토·코멘트·양식의 규칙 (DB 없이) */
class MinutesDomainTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    private static final long WRITER = 7L;

    @Test
    void 만들면_작성_중이고_검색_글은_회의명으로_시작한다() {
        Minutes minutes = minutes();

        assertThat(minutes.is(MinutesStatus.WRITING)).isTrue();
        assertThat(minutes.getContentVersion()).isZero();
        assertThat(minutes.getReviewRound()).isZero();
        assertThat(minutes.getSearchText()).isEqualTo("주간 회의");
    }

    @Test
    void 내용을_저장할_때마다_버전이_1씩_오르고_양식은_바꿀_때만_바뀐다() {
        Minutes minutes = minutes();

        assertThat(minutes.contentChanged(1L, "주간 회의\n결정 사항\n배포")).isEqualTo(1);
        assertThat(minutes.contentChanged(null, "주간 회의\n결정 사항\n배포 확정")).isEqualTo(2);

        assertThat(minutes.getTemplateId()).isEqualTo(1L);
        assertThat(minutes.getSearchText()).endsWith("배포 확정");
    }

    @Test
    void 검토를_요청할_때마다_검토_회차가_오른다() {
        Minutes minutes = minutes();

        minutes.requestReview();
        minutes.requestReview();

        assertThat(minutes.is(MinutesStatus.REVIEWING)).isTrue();
        assertThat(minutes.getReviewRound()).isEqualTo(2);
    }

    @Test
    void 자동_확정은_확정한_사람이_없고_작성자_확정은_작성자가_남는다() {
        Minutes auto = minutes();
        auto.confirm(NOW, null);
        Minutes byWriter = minutes();
        byWriter.confirm(NOW, WRITER);

        assertThat(auto.is(MinutesStatus.CONFIRMED)).isTrue();
        assertThat(auto.getConfirmedByUserId()).isNull();
        assertThat(auto.getConfirmedAt()).isEqualTo(NOW);
        assertThat(byWriter.getConfirmedByUserId()).isEqualTo(WRITER);
    }

    @Test
    void 작성자만_작성자로_본다() {
        assertThat(minutes().isWriter(WRITER)).isTrue();
        assertThat(minutes().isWriter(8L)).isFalse();
    }

    @Test
    void 검토는_미확인으로_시작하고_다시_요청하면_결정이_지워진다() {
        MinutesReview review = new MinutesReview(1L, 8L);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);

        review.decide(ReviewStatus.APPROVED, NOW);
        assertThat(review.isApproved()).isTrue();
        assertThat(review.getDecidedAt()).isEqualTo(NOW);

        review.reset();
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(review.getDecidedAt()).isNull();
    }

    @Test
    void 코멘트는_처음_반영한_시각을_유지한다() {
        MinutesComment comment = new MinutesComment(1L, 2L, "배포 일정", "날짜를 적어 주세요", 8L, NOW);

        comment.resolve(NOW.plusHours(1));
        comment.resolve(NOW.plusHours(2));

        assertThat(comment.getResolvedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void 기본_양식은_누구나_고르고_개인_양식은_만든_사람만_고른다() {
        Template defaults = new Template(null, "기본 양식", "", "{}");
        Template mine = new Template(WRITER, "내 양식", "", "{}");

        assertThat(defaults.isDefault()).isTrue();
        assertThat(defaults.isUsableBy(WRITER)).isTrue();
        assertThat(defaults.isUsableBy(8L)).isTrue();
        assertThat(mine.isDefault()).isFalse();
        assertThat(mine.isUsableBy(WRITER)).isTrue();
        assertThat(mine.isUsableBy(8L)).isFalse();
    }

    @Test
    void 회의를_옮기면_회의록_폴더도_바뀐다() {
        Minutes minutes = minutes();

        minutes.moveTo(99L);

        assertThat(minutes.getFolderId()).isEqualTo(99L);
    }

    private static Minutes minutes() {
        return new Minutes(31L, 3L, 4L, "주간 회의", NOW.minusDays(1), "3층 회의실", WRITER);
    }
}
