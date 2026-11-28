package kr.fitorder.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResultCacheTest {

    private final ResultCache cache = new ResultCache();

    @Test
    void 같은_키는_한_번만_계산한다() {
        AtomicInteger calls = new AtomicInteger();

        String first = cache.get("a", () -> "값" + calls.incrementAndGet());
        String second = cache.get("a", () -> "값" + calls.incrementAndGet());

        assertThat(first).isSameAs(second);
        assertThat(calls).hasValue(1);
    }

    @Test
    void 데이터가_바뀌었다는_알림이_오면_다시_계산한다() {
        AtomicInteger calls = new AtomicInteger();
        cache.get("a", calls::incrementAndGet);

        cache.onDataChanged(new EsgDataChangedEvent("uniform", "적재"));
        cache.get("a", calls::incrementAndGet);
        cache.onResultsChanged(new ResultsChangedEvent("녹색제품"));
        cache.get("a", calls::incrementAndGet);

        assertThat(calls).hasValue(3);
    }

    @Test
    void 계산하는_동안_데이터가_바뀌면_그_결과는_저장하지_않는다() {
        AtomicInteger calls = new AtomicInteger();

        // 계산 도중에 변경이 커밋된 상황: 옛 데이터로 계산한 값이 새 버전 이름으로 남으면 안 된다
        cache.get("a", () -> {
            cache.clear();
            return calls.incrementAndGet();
        });
        Integer again = cache.get("a", calls::incrementAndGet);

        assertThat(again).isEqualTo(2);
        assertThat(calls).hasValue(2);
    }

    @Test
    void 오류가_난_계산은_저장하지_않는다() {
        try {
            cache.get("a", () -> {
                throw new BusinessException("데이터가 없습니다");
            });
        } catch (BusinessException ignored) {
            // 기대한 오류
        }

        assertThat(cache.size()).isZero();
        assertThat(cache.<String>get("a", () -> "나중에 성공")).isEqualTo("나중에 성공");
    }

    @Test
    void 입력을_마음대로_바꿔_보내도_메모리가_무한히_늘지_않는다() {
        for (int i = 0; i < 1000; i++) {
            int n = i;
            cache.get("k" + n, () -> n);
        }

        assertThat(cache.size()).isLessThanOrEqualTo(200);
    }
}
