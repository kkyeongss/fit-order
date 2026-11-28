package kr.fitorder.common.web;

import kr.fitorder.common.Basis;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** 쿼리 파라미터 basis=dedup|raw (대소문자 무관). */
@Component
public class BasisConverter implements Converter<String, Basis> {

    @Override
    public Basis convert(String source) {
        return Basis.of(source);
    }
}
