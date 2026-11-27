package kr.fitorder.common;

/** 사용자가 고칠 수 있는 요청 오류(400). 메시지는 화면에 그대로 보인다. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
