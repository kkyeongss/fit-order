package kr.fitorder.module.uniform.price;

import java.math.BigDecimal;

/**
 * 나라장터 쇼핑몰 계약상품 하나.
 *
 * @param key      쇼핑계약번호 + 순번(중복 제거용). 파일에 없으면 null
 * @param specName 물품규격명(prdctSpecNm) 예) '근무복, 다원, DA-0202, 소방공무원하계용(하의/남성용)'
 * @param price    계약가격금액(cntrctPrceAmt)
 * @param eco      의무구매대상인증에 환경표지제품이 있는지
 */
public record MarketItem(String key, String specName, BigDecimal price, String unit, boolean eco) {

    static final String ECO_LABEL = "환경표지";

    static boolean isEco(String dutyPurchaseCert) {
        return dutyPurchaseCert != null && dutyPurchaseCert.contains(ECO_LABEL);
    }
}
