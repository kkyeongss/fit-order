package kr.fitorder.common.esg;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.fitorder.common.EsgModule;
import kr.fitorder.common.SourceLoader;
import org.springframework.stereotype.Component;

/** 등록된 ESG 모듈과 원천별 적재기를 찾는다. 항목을 조건문으로 가르지 않기 위한 단일 진입점. */
@Component
public class EsgModuleRegistry {

    public record RegisteredLoader(EsgModule module, SourceLoader loader) {
    }

    private final Map<String, EsgModule> modules = new LinkedHashMap<>();
    private final Map<String, RegisteredLoader> loaders = new LinkedHashMap<>();

    public EsgModuleRegistry(List<EsgModule> modules) {
        for (EsgModule module : modules) {
            if (this.modules.putIfAbsent(module.code(), module) != null) {
                throw new IllegalStateException("ESG 항목 코드 중복: " + module.code());
            }
            for (SourceLoader loader : module.loaders()) {
                if (loaders.putIfAbsent(loader.sourceCode(), new RegisteredLoader(module, loader)) != null) {
                    throw new IllegalStateException("원천 코드 중복: " + loader.sourceCode());
                }
            }
        }
    }

    public Collection<EsgModule> modules() {
        return modules.values();
    }

    public Optional<EsgModule> module(String code) {
        return Optional.ofNullable(modules.get(code));
    }

    public Optional<RegisteredLoader> loader(String sourceCode) {
        return Optional.ofNullable(loaders.get(sourceCode));
    }
}
