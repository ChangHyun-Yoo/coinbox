package com.kakaobank.coinbox.coinbox.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 저금통 가입에 사용할 수 있는 입출금계좌 목록이다.
 */
@Schema(description = "저금통 가입 가능 계좌 목록")
public record EligibleAccountsResponse(
        @Schema(description = "가입 가능한 입출금계좌. 가입 가능한 계좌가 없으면 API는 예외를 반환")
        List<EligibleAccountResponse> accounts
) {

    /**
     * 외부에서 응답 목록을 변경하지 못하도록 불변 복사한다.
     */
    public EligibleAccountsResponse {
        accounts = List.copyOf(accounts);
    }
}
