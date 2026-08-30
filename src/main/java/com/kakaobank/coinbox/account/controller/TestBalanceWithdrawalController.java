package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.request.TestBalanceWithdrawalRequest;
import com.kakaobank.coinbox.account.response.TestBalanceWithdrawalResponse;
import com.kakaobank.coinbox.account.service.TestBalanceWithdrawalService;
import com.kakaobank.coinbox.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채점자와 개발자가 입출금계좌 잔고를 감소시킬 수 있는 로컬 테스트 전용 API다.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/internal/v1/test-account-withdrawals",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "테스트 잔고 출금")
public class TestBalanceWithdrawalController {

    private final TestBalanceWithdrawalService testBalanceWithdrawalService;

    /**
     * 입력한 입출금계좌의 현재 잔고에서 지정 금액을 뺀다.
     */
    @PostMapping
    @Operation(
            summary = "테스트 전용 계좌 잔고 출금",
            description = "입출금계좌의 ACCOUNT.balance만 감소시키는 로컬 테스트 API입니다. 금융거래와 계좌 원장은 생성하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "잔고 감소 성공",
                    content = @Content(schema = @Schema(implementation = TestBalanceWithdrawalResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "계좌번호 또는 금액 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "입출금계좌가 아니거나 잔고 부족",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public TestBalanceWithdrawalResponse withdraw(@Valid @RequestBody TestBalanceWithdrawalRequest request) {
        log.info("로컬 테스트 전용 계좌 잔고 감소를 요청했습니다.");
        return testBalanceWithdrawalService.withdraw(request.accountNumber(), request.amount());
    }
}
