package kr.fitorder.common;

import java.util.List;

/** 적재할 행 묶음을 점검해 중복 표시·품질 플래그를 단다. 행은 지우지 않는다. */
public interface QualityChecker<T> {

    void inspect(List<T> rows);
}
