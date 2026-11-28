package kr.fitorder.module.uniform.price;

import java.time.LocalDate;

/** 환경표지 인증 제품 하나(녹색제품 정보조회 서비스 getServiceList의 dataEL). */
public record GreenProduct(String productId, String name, String category, String model, String certNo,
                           LocalDate certFrom, LocalDate certTo, String certStatus, String company, String g2bId) {

    /** 오늘 기준 인증기간 안인지. 종료일이 없으면 상태값만 본다. */
    public boolean valid(LocalDate today) {
        boolean inPeriod = certTo == null || !certTo.isBefore(today);
        boolean statusOk = certStatus == null || !(certStatus.contains("만료") || certStatus.contains("취소"));
        return inPeriod && statusOk;
    }
}
