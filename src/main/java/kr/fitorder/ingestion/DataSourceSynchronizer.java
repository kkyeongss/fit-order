package kr.fitorder.ingestion;

import kr.fitorder.common.EsgModule;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.SourceLoader;
import kr.fitorder.common.SourceSpec;
import kr.fitorder.common.esg.EsgItemRepository;
import kr.fitorder.common.esg.EsgModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작할 때 코드에 있는 적재기 중 data_source에 없는 원천을 application.yml 값으로 등록한다.
 * 이미 있는 원천은 건드리지 않는다(화면에서 고친 값이 우선).
 */
@Component
@Order(2)
public class DataSourceSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceSynchronizer.class);

    private final EsgModuleRegistry registry;
    private final EsgItemRepository items;
    private final DataSourceRepository sources;
    private final IngestionProperties seeds;

    public DataSourceSynchronizer(EsgModuleRegistry registry, EsgItemRepository items, DataSourceRepository sources,
                                  IngestionProperties seeds) {
        this.registry = registry;
        this.items = items;
        this.sources = sources;
        this.seeds = seeds;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (EsgModule module : registry.modules()) {
            LoadMethod itemMethod = items.findById(module.code()).map(i -> i.getLoadMethod())
                    .orElse(module.defaultLoadMethod());
            for (SourceLoader loader : module.loaders()) {
                if (sources.existsById(loader.sourceCode())) {
                    continue;
                }
                SourceSpec seed = seeds.sources().get(loader.sourceCode());
                if (seed == null) {
                    seed = new SourceSpec(loader.sourceCode(), null, null, null, null, null);
                    log.warn("원천 {}의 초기 설정이 없습니다. 데이터 관리 화면에서 컬럼 매핑을 입력하세요.", loader.sourceCode());
                }
                // 설정에 방식이 없으면 항목의 일괄 설정을 따른다. 단 API 주소가 없으면 API로 둘 수 없어 업로드.
                LoadMethod method = seed.method() != null ? seed.method()
                        : itemMethod == LoadMethod.API && (seed.url() == null || seed.url().isBlank())
                        ? LoadMethod.UPLOAD : itemMethod;
                sources.save(new DataSource(loader.sourceCode(), module.code(), seed, method, "system"));
                log.info("데이터 원천 등록: {} ({}, {})", loader.sourceCode(), seed.name(), method);
            }
        }
    }
}
