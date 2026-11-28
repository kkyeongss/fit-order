package kr.fitorder.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/** 집계 기준: 중복 제거(DEDUP, 기본값) 또는 원자료(RAW). API에서는 dedup|raw. */
public enum Basis {
    DEDUP, RAW;

    /** 대소문자 구분 없이 읽는다. 비어 있으면 기본값 DEDUP. */
    @JsonCreator
    public static Basis of(String value) {
        if (value == null || value.isBlank()) {
            return DEDUP;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("basis는 dedup 또는 raw입니다: " + value);
        }
    }
}
