package kr.fitorder.module.uniform.cleansing;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.QualityFlag;
import kr.fitorder.ingestion.DataSourceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 품질 리포트: 집계에 쓰는 활성 적재의 중복·품질 플래그·사이즈 매핑 현황. */
@Service
@Transactional(readOnly = true)
public class QualityReportService {

    private static final int SAMPLE_ROWS = 10;

    public record Sample(int lineNo, String rawValues) {
    }

    public record FlagCount(QualityFlag flag, String label, int rows, List<Sample> samples) {
    }

    public record SourceQuality(String source, String name, Integer baseYear, String fileName, OffsetDateTime loadedAt,
                                int rowCount, int duplicateRows, int flaggedRows, List<FlagCount> flags,
                                int sizeCodes, int unmappedSizeCodes) {
    }

    private final JdbcTemplate jdbc;
    private final DataSourceService sources;

    public QualityReportService(JdbcTemplate jdbc, DataSourceService sources) {
        this.jdbc = jdbc;
        this.sources = sources;
    }

    public List<SourceQuality> report() {
        Map<String, String> names = sources.names();
        List<SourceQuality> result = new ArrayList<>();
        jdbc.query("""
                select id, source, base_year, file_name, loaded_at, row_count, duplicate_count, flagged_count
                from load_history where active order by source""", rs -> {
            String source = rs.getString("source");
            long loadId = rs.getLong("id");
            boolean orders = count("select count(*) from uniform_order where load_id = ?", loadId) > 0;
            List<FlagCount> flags = orders ? flags(loadId) : List.of();
            int sizeCodes = orders ? count("""
                    select count(distinct raw_size) from uniform_order where load_id = ? and raw_size is not null""",
                    loadId) : 0;
            int unmapped = orders ? count("""
                    select count(distinct o.raw_size) from uniform_order o
                    left join size_mapping m on m.source = o.source and m.raw_size = o.raw_size
                    where o.load_id = ? and o.raw_size is not null and m.id is null""", loadId) : 0;
            result.add(new SourceQuality(source, names.getOrDefault(source, source),
                    (Integer) rs.getObject("base_year"), rs.getString("file_name"),
                    rs.getObject("loaded_at", OffsetDateTime.class), rs.getInt("row_count"),
                    rs.getInt("duplicate_count"), rs.getInt("flagged_count"), flags, sizeCodes, unmapped));
        });
        return result;
    }

    private List<FlagCount> flags(long loadId) {
        List<FlagCount> result = new ArrayList<>();
        for (QualityFlag flag : QualityFlag.values()) {
            String pattern = "%" + flag.name() + "%";
            int rows = count("select count(*) from uniform_order where load_id = ? and quality_flags like ?", loadId,
                    pattern);
            if (rows == 0) {
                continue;
            }
            List<Sample> samples = jdbc.query("""
                    select line_no, raw_values from uniform_order where load_id = ? and quality_flags like ?
                    order by line_no limit ?""",
                    (r, i) -> new Sample(r.getInt("line_no"), r.getString("raw_values")), loadId, pattern, SAMPLE_ROWS);
            result.add(new FlagCount(flag, flag.label(), rows, samples));
        }
        return result;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
