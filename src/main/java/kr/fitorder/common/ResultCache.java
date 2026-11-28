package kr.fitorder.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계산 결과를 메모리에 잠깐 둔다(DB에는 저장하지 않는다). 데이터가 바뀌면 바로 버린다.
 *
 * <p>작은 서버(CPU가 느림)에서 같은 화면을 다시 열 때마다 계산하지 않게 하려는 장치다.
 * 낡은 결과가 남지 않게 '데이터 버전'을 키에 넣는다. 변경이 커밋되면 버전이 올라가고, 계산하는 동안 버전이
 * 바뀌었으면 그 결과는 저장하지 않는다(옛 데이터로 계산한 값이 새 버전 이름으로 남는 일이 없다).
 */
@Component
public class ResultCache {

    /** 입력값(연도·인원 등)을 마음대로 바꿔 보내도 메모리가 무한히 늘지 않게 하는 상한 */
    private static final int MAX_ENTRIES = 200;

    private final AtomicLong version = new AtomicLong();
    private final ConcurrentHashMap<String, Object> entries = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> compute) {
        long v = version.get();
        String versioned = v + "|" + key;
        Object cached = entries.get(versioned);
        if (cached != null) {
            return (T) cached;
        }
        T value = compute.get();
        if (version.get() == v) {
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
            entries.put(versioned, value);
        }
        return value;
    }

    public void clear() {
        version.incrementAndGet();
        entries.clear();
    }

    /** 변경이 커밋된 뒤에만 버린다. 트랜잭션 밖에서 발행되면 바로 버린다. */
    @TransactionalEventListener(fallbackExecution = true)
    void onDataChanged(EsgDataChangedEvent event) {
        clear();
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onResultsChanged(ResultsChangedEvent event) {
        clear();
    }

    int size() {
        return entries.size();
    }
}
