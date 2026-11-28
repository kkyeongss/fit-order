package kr.fitorder.module.uniform.price;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 녹색제품 정보조회 서비스 설정(fitorder.uniform.green-product).
 *
 * @param url      서비스 주소. 오퍼레이션은 getServiceList
 * @param keywords 제품명으로 찾을 말(prod_gubn=2). 결과를 합치고 같은 상품ID는 한 번만
 */
@ConfigurationProperties(prefix = "fitorder.uniform.green-product")
public record GreenProductProperties(String url, List<String> keywords) {

    public GreenProductProperties {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
