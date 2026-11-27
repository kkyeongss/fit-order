package kr.fitorder.admin;

import org.springframework.stereotype.Service;

/** 단가 입력, CSV 적재, 사이즈 매핑·ESG 항목 변경을 남긴다. 호출한 쪽 트랜잭션에 함께 묶인다. */
@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void record(String username, String action, String target, String detail) {
        logs.save(new AuditLog(username, action, target, detail));
    }
}
