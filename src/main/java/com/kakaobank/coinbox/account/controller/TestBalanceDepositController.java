package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.request.TestBalanceDepositRequest;
import com.kakaobank.coinbox.account.response.TestBalanceDepositResponse;
import com.kakaobank.coinbox.account.service.TestBalanceDepositService;
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
 * 채점자와 개발자가 특정 계좌의 잔고를 준비할 수 있는 로컬 테스트 전용 API다.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/internal/v1/test-account-deposits",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "테스트 잔고 충전")
public class TestBalanceDepositController {

    private final TestBalanceDepositService testBalanceDepositService;

    /**
     * 입력한 계좌의 현재 잔고에 지정 금액을 더한다.
     */
    @PostMapping
    @Operation(
            summary = "테스트 전용 계좌 잔고 충전",
            description = "ACCOUNT.balance만 증가시키는 로컬 테스트 API입니다. 금융거래와 계좌 원장은 생성하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "잔고 증가 성공",
                    content = @Content(schema = @Schema(implementation = TestBalanceDepositResponse.class))
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
                    description = "입출금계좌가 아닌 계좌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public TestBalanceDepositResponse deposit(@Valid @RequestBody TestBalanceDepositRequest request) {
        log.info("로컬 테스트 전용 계좌 잔고 증가를 요청했습니다.");
        return testBalanceDepositService.deposit(request.accountNumber(), request.amount());
    }
}
