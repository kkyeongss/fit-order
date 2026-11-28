package kr.fitorder.module.uniform.analysis;

import java.util.Collection;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * '95 (근무복 주문현황)'처럼 이름 끝에 출처가 붙은 사이즈들이 모두 같은 출처면 꼬리표를 떼고, 출처가 섞여 있으면(점퍼) 그대로 둔다.
 * 화면(frontend/src/format.ts의 splitSizeSource)과 같은 규칙이다.
 */
public final class SizeLabels {

    private static final Pattern WITH_SOURCE = Pattern.compile("^(.*) \\((.+)\\)$");

    private SizeLabels() {
    }

    /** 이름들에서 공통 출처가 있으면 그 출처를 뗀 이름을 돌려주는 함수. 없으면 그대로. */
    public static UnaryOperator<String> withoutCommonSource(Collection<String> names) {
        Set<String> sources = names.stream().map(n -> {
            Matcher m = WITH_SOURCE.matcher(n);
            return m.matches() ? m.group(2) : "";
        }).collect(Collectors.toSet());
        if (sources.size() == 1 && !sources.contains("")) {
            return n -> {
                Matcher m = WITH_SOURCE.matcher(n);
                return m.matches() ? m.group(1) : n;
            };
        }
        return n -> n;
    }

    /** 공통 출처(없으면 null). 화면·엑셀에서 '사이즈 코드는 ○○ 기준'이라고 알려줄 때 쓴다. */
    public static String commonSource(Collection<String> names) {
        Set<String> sources = names.stream().map(n -> {
            Matcher m = WITH_SOURCE.matcher(n);
            return m.matches() ? m.group(2) : "";
        }).collect(Collectors.toSet());
        return sources.size() == 1 && !sources.contains("") ? sources.iterator().next() : null;
    }
}
