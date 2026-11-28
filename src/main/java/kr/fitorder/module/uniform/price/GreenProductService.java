package kr.fitorder.module.uniform.price;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ResultsChangedEvent;
import kr.fitorder.ingestion.CsvUploadIngestion;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환경표지 인증 근무복 목록. 발주 추천 화면에서 품목군별로 녹색제품을 살 수 있는지(유효 인증 제품·기업 수)를 보여준다.
 * 가격은 이 서비스에 없어 조달청 쇼핑몰의 환경표지제품 가격(MarketPrice.ecoMedianPrice)과 함께 본다.
 */
@Service
public class GreenProductService {

    public record ProductView(GreenProduct product, UniformItemGroup group, boolean valid) {
    }

    /** group이 null이면 품목군을 정하지 못한 근무복(예: 제품명이 '근무복'뿐). */
    public record GroupSummary(UniformItemGroup group, String label, int validCount, int companyCount,
                               List<String> examples) {
    }

    public record FetchResult(int productCount, int validCount, List<GroupSummary> groups) {
    }

    private final GreenProductSource api;
    private final MarketPriceService classifier;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public GreenProductService(GreenProductSource api, MarketPriceService classifier, JdbcTemplate jdbc,
                               AuditService audit, ApplicationEventPublisher events) {
        this.api = api;
        this.classifier = classifier;
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public FetchResult fetch(String user) {
        return save(api.fetch(), "API", user);
    }

    /**
     * 망분리 환경: 녹색제품정보시스템에서 받은 목록을 CSV로 올린다.
     * 컬럼: 상품ID, 상품명(필수), 소분류명, 모델명, 환경표지인증번호, 인증시작일, 인증종료일, 인증상태, 인증기업명, G2B식별번호
     */
    @Transactional
    public FetchResult upload(byte[] content, String user) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.contains("�")) {
            text = new String(content, CsvUploadIngestion.charsetOf("CP949"));
        }
        text = text.startsWith("﻿") ? text.substring(1) : text;
        List<GreenProduct> products = new ArrayList<>();
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get()
                .parse(new StringReader(text))) {
            if (!parser.getHeaderNames().contains("상품명")) {
                throw new BusinessException("필요한 컬럼이 없습니다: 상품명 / 받은 컬럼: " + parser.getHeaderNames());
            }
            for (CSVRecord r : parser) {
                String id = get(r, "상품ID");
                products.add(new GreenProduct(id != null ? id : "line-" + r.getRecordNumber(), r.get("상품명"),
                        get(r, "소분류명"), get(r, "모델명"), get(r, "환경표지인증번호"),
                        GreenProductSource.date(get(r, "인증시작일")), GreenProductSource.date(get(r, "인증종료일")),
                        get(r, "인증상태"), get(r, "인증기업명"), get(r, "G2B식별번호")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return save(products, "UPLOAD", user);
    }

    @Transactional(readOnly = true)
    public List<ProductView> list() {
        LocalDate today = LocalDate.now();
        return jdbc.query("select * from green_product order by item_group nulls last, name", (rs, i) -> {
            GreenProduct p = new GreenProduct(rs.getString("product_id"), rs.getString("name"),
                    rs.getString("category"), rs.getString("model"), rs.getString("cert_no"),
                    rs.getObject("cert_from", LocalDate.class), rs.getObject("cert_to", LocalDate.class),
                    rs.getString("cert_status"), rs.getString("company"), rs.getString("g2b_id"));
            String g = rs.getString("item_group");
            return new ProductView(p, g == null ? null : UniformItemGroup.valueOf(g), p.valid(today));
        });
    }

    /** 품목군별 유효 인증 제품 수. 데이터가 없는 품목군은 0으로 나온다. */
    @Transactional(readOnly = true)
    public List<GroupSummary> summary() {
        List<ProductView> products = list();
        List<GroupSummary> result = new ArrayList<>();
        List<UniformItemGroup> groups = new ArrayList<>(Arrays.stream(UniformItemGroup.values())
                .filter(g -> g != UniformItemGroup.UNKNOWN).toList());
        groups.add(null);
        for (UniformItemGroup g : groups) {
            List<ProductView> valid = products.stream().filter(p -> p.group() == g && p.valid()).toList();
            if (g == null && valid.isEmpty()) {
                continue;
            }
            result.add(new GroupSummary(g, g == null ? "근무복(품목 구분 없음)" : g.label(), valid.size(),
                    (int) valid.stream().map(p -> p.product().company()).filter(Objects::nonNull).distinct().count(),
                    valid.stream().map(p -> p.product().name()).distinct().limit(3).toList()));
        }
        return result;
    }

    private FetchResult save(List<GreenProduct> products, String method, String user) {
        if (products.isEmpty()) {
            throw new BusinessException("받은 녹색제품이 없습니다.");
        }
        jdbc.update("delete from green_product");
        for (GreenProduct p : products) {
            UniformItemGroup group = classifier.classifyText(p.name()).orElse(null);
            jdbc.update("""
                    insert into green_product (product_id, name, category, model, cert_no, cert_from, cert_to,
                        cert_status, company, g2b_id, item_group, method, fetched_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (product_id) do nothing""",
                    p.productId(), p.name(), p.category(), p.model(), p.certNo(), p.certFrom(), p.certTo(),
                    p.certStatus(), p.company(), p.g2bId(), group == null ? null : group.name(), method, user);
        }
        List<GroupSummary> groups = summary();
        int valid = groups.stream().mapToInt(GroupSummary::validCount).sum();
        audit.record(user, "GREEN_PRODUCT_" + method, "keiti-green-product",
                products.size() + "개 제품, 유효 인증 " + valid);
        events.publishEvent(new ResultsChangedEvent("녹색제품 갱신"));
        return new FetchResult(products.size(), valid, groups);
    }

    private static String get(CSVRecord r, String column) {
        return r.isMapped(column) && !r.get(column).isBlank() ? r.get(column) : null;
    }
}
