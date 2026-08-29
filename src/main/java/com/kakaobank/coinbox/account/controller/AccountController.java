package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.response.ActiveAccountResponse;
import com.kakaobank.coinbox.account.service.AccountQueryService;
import com.kakaobank.coinbox.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 인증 고객의 계좌 조회 요청을 검증하고 조회 서비스에 전달한다.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "계좌")
public class AccountController {

    private final AccountQueryService accountQueryService;

    /**
     * ACTIVE 최상위 계좌와 1단계 자식 계좌를 계층형 응답으로 반환한다.
     */
    @GetMapping
    @Operation(
            summary = "고객 ACTIVE 계좌 조회",
            description = "인증 고객의 ACTIVE 계좌를 최상위 계좌와 1단계 childAccount 구조로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActiveAccountResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 헤더 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "고객을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "계좌·상품 또는 부모·자식 관계의 정합성 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public List<ActiveAccountResponse> findActiveAccounts(
            @Parameter(
                    name = "X-Customer-Id",
                    description = "인증·게이트웨이 계층이 검증 후 전달한 고객 ID",
                    required = true,
                    example = "710000000000000001"
            )
            @RequestHeader("X-Customer-Id") @NotNull @Positive Long customerId
    ) {
        log.debug("고객 ACTIVE 계좌 목록을 조회합니다. customerId={}", customerId);
        return accountQueryService.findActiveAccounts(customerId);
    }
}
