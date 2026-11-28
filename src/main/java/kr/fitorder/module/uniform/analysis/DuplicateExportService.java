package kr.fitorder.module.uniform.analysis;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import kr.fitorder.ingestion.DataSourceService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완전 중복 행 목록(CSV). 기관에 '이 행들이 실제 중복 주문인지' 확인을 요청할 때 첨부하는 근거 자료다.
 * 같은 행의 첫 줄 번호를 함께 보여 어떤 행과 겹치는지 알 수 있게 한다.
 */
@Service
public class DuplicateExportService {

    private static final String BOM = "﻿";

    private final JdbcTemplate jdbc;
    private final DataSourceService sources;

    public DuplicateExportService(JdbcTemplate jdbc, DataSourceService sources) {
        this.jdbc = jdbc;
        this.sources = sources;
    }

    @Transactional(readOnly = true)
    public byte[] csv() {
        var names = sources.names();
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVPrinter p = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            p.printRecord("출처", "파일 줄 번호", "같은 행이 처음 나온 줄", "주문일", "품목(원본)", "사이즈(원본)", "수량", "원본 값");
            jdbc.query("""
                    select o.source, o.line_no, o.order_date, o.raw_item, o.raw_size, o.quantity, o.raw_values,
                           (select min(f.line_no) from uniform_order_active f
                             where f.source = o.source and f.row_hash = o.row_hash and not f.duplicate) as first_line
                    from uniform_order_active o
                    where o.duplicate
                    order by o.source, o.line_no""", rs -> {
                try {
                    p.printRecord(names.getOrDefault(rs.getString("source"), rs.getString("source")), rs.getInt("line_no"),
                            rs.getObject("first_line"), rs.getObject("order_date"), rs.getString("raw_item"),
                            rs.getString("raw_size"), rs.getObject("quantity"), rs.getString("raw_values"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
