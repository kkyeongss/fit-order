package kr.fitorder.module.uniform.price;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ExternalSource;
import kr.fitorder.ingestion.ExternalApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 한국환경산업기술원_녹색제품 정보조회 서비스 GW(공공데이터포털 15158379)에서 환경표지 근무복을 받는다.
 * 오퍼레이션 getServiceList, XML 응답 response/body/collection/dataEL. 검색은 제품명(prod_gubn=2)만 된다.
 */
@Component
public class GreenProductSource implements ExternalSource<GreenProduct> {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;

    private final GreenProductProperties props;
    private final ExternalApiProperties keys;
    private final RestClient client = RestClient.create();

    public GreenProductSource(GreenProductProperties props, ExternalApiProperties keys) {
        this.props = props;
        this.keys = keys;
    }

    @Override
    public String code() {
        return "keiti-green-product";
    }

    @Override
    public List<GreenProduct> fetch() {
        String key = keys.dataGoKr().decodedKey();
        Map<String, GreenProduct> products = new LinkedHashMap<>();
        for (String keyword : props.keywords()) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                Document doc = call(key, keyword, page);
                List<GreenProduct> rows = parse(doc);
                rows.forEach(p -> products.putIfAbsent(p.productId(), p));
                int total = intOf(text(doc.getDocumentElement(), "totalCount"));
                if (rows.isEmpty() || page * PAGE_SIZE >= total) {
                    break;
                }
            }
        }
        return new ArrayList<>(products.values());
    }

    private Document call(String key, String keyword, int page) {
        var uri = UriComponentsBuilder.fromUriString(props.url() + "/getServiceList")
                .queryParam("serviceKey", key)
                .queryParam("pageNo", page)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("prod_gubn", "2")
                .queryParam("prodValue", keyword)
                .encode().build().toUri();
        byte[] body;
        try {
            body = client.get().uri(uri).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new BusinessException("녹색제품 API에 연결할 수 없습니다. 망분리 환경이면 녹색제품 파일을 올리세요.");
        }
        if (body == null || body.length == 0) {
            throw new BusinessException("녹색제품 API 응답이 비어 있습니다.");
        }
        Document doc = xml(body);
        Element root = doc.getDocumentElement();
        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            String code = text(root, "returnReasonCode");
            String hint = "04".equals(code) ? " (기관 서버 오류. 활용신청 직후면 반영까지 1~2시간 걸릴 수 있음)"
                    : "30".equals(code) ? " (활용신청 승인 여부와 인증키를 확인하세요)" : "";
            throw new BusinessException("녹색제품 API 오류: " + text(root, "returnAuthMsg") + hint);
        }
        String resultCode = text(root, "resultCode");
        if (resultCode != null && !resultCode.equals("00")) {
            throw new BusinessException("녹색제품 API 오류: " + text(root, "resultMsg"));
        }
        return doc;
    }

    static List<GreenProduct> parse(Document doc) {
        NodeList list = doc.getElementsByTagName("dataEL");
        List<GreenProduct> result = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            String id = text(e, "prodProd");
            if (id == null) {
                continue;
            }
            result.add(new GreenProduct(id, text(e, "prodPrnm"), text(e, "ecosClnm"), text(e, "prodMdel"),
                    text(e, "prodRsid"), date(text(e, "prodRsdt")), date(text(e, "prodRedt")), text(e, "prodRsstnm"),
                    text(e, "vendVcnm"), text(e, "prodG2sn")));
        }
        return result;
    }

    static Document xml(byte[] body) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new BusinessException("녹색제품 API 응답을 읽을 수 없습니다(XML 아님).");
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node n = nodes.item(0);
        String v = n.getTextContent();
        return v == null || v.isBlank() ? null : v.strip();
    }

    /** '20240101', '2024-01-01', '2024.01.01' 모두 읽는다. */
    static LocalDate date(String v) {
        if (v == null) {
            return null;
        }
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return null;
        }
        return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static int intOf(String v) {
        try {
            return v == null ? 0 : Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
