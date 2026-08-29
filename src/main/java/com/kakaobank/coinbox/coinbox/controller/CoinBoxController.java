package com.kakaobank.coinbox.coinbox.controller;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.coinbox.request.OpenCoinBoxRequest;
import com.kakaobank.coinbox.coinbox.response.EligibleAccountResponse;
import com.kakaobank.coinbox.coinbox.response.EligibleAccountsResponse;
import com.kakaobank.coinbox.coinbox.response.EmptyCoinBoxResponse;
import com.kakaobank.coinbox.coinbox.response.OpenCoinBoxResponse;
import com.kakaobank.coinbox.coinbox.response.TerminateCoinBoxResponse;
import com.kakaobank.coinbox.coinbox.service.CoinBoxService;
import com.kakaobank.coinbox.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 저금통 가입 가능 조회, 개설, 비우기와 해지 HTTP 요청을 처리한다.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/coinboxes", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "저금통")
public class CoinBoxController {

    private final CoinBoxService coinBoxService;

    /**
     * 인증 고객이 저금통의 근거계좌로 선택할 수 있는 계좌를 조회한다.
     */
    @GetMapping("/eligible-accounts")
    @Operation(
            summary = "저금통 가입 가능 계좌 조회",
            description = "이용 중인 저금통이 없는 고객에게 개설 가능한 ACTIVE 입출금계좌를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EligibleAccountsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 헤더 형식 또는 고객 가입 조건 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "고객을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 저금통을 이용 중이거나 가입 가능한 계좌가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public EligibleAccountsResponse findEligibleAccounts(
            @Parameter(
                    name = "X-Customer-Id",
                    description = "인증·게이트웨이 계층이 검증 후 전달한 고객 ID",
                    required = true,
                    example = "710000000000000001"
            )
            @RequestHeader("X-Customer-Id") @NotNull @Positive Long customerId
    ) {
        List<EligibleAccountResponse> accounts = coinBoxService.findEligibleAccounts(customerId).stream()
                .map(EligibleAccountResponse::from)
                .toList();
        return new EligibleAccountsResponse(accounts);
    }

    /**
     * 선택한 입출금계좌를 근거계좌로 새 저금통을 개설한다.
     */
    @PostMapping
    @Operation(
            summary = "저금통 개설",
            description = "선택한 입출금계좌를 근거계좌로 계좌·계약·저금통 설정을 함께 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "개설 성공",
                    content = @Content(schema = @Schema(implementation = OpenCoinBoxResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 또는 고객 가입 조건 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "고객 또는 선택 계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "중복 가입 또는 선택 계좌가 가입 조건을 충족하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "계좌번호 생성 실패 또는 서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "적용 가능한 저금통 정책이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<OpenCoinBoxResponse> openCoinBox(
            @Parameter(
                    name = "X-Customer-Id",
                    description = "인증·게이트웨이 계층이 검증 후 전달한 고객 ID",
                    required = true,
                    example = "710000000000000001"
            )
            @RequestHeader("X-Customer-Id") @NotNull @Positive Long customerId,
            @Valid @RequestBody OpenCoinBoxRequest request
    ) {
        log.debug("저금통 개설 요청을 처리합니다. customerId={}", customerId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(OpenCoinBoxResponse.from(
                        coinBoxService.openCoinBox(customerId, request.parentAccountId())
                ));
    }

    /**
     * 저금통의 잠금 후 확정 잔액 전액을 연결 입출금계좌로 이전한다.
     */
    @PostMapping("/{accountNumber}/empty")
    @Operation(
            summary = "저금통 비우기",
            description = "저금통 현재 잔액 전액을 서버가 확인한 연결 입출금계좌로 이체합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "비우기 성공",
                    content = @Content(schema = @Schema(implementation = EmptyCoinBoxResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 헤더 또는 계좌번호 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "인증 고객 소유의 저금통을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "저금통이 비어 있거나 계좌 거래가 불가능함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public EmptyCoinBoxResponse emptyCoinBox(
            @Parameter(
                    name = "X-Customer-Id",
                    description = "인증·게이트웨이 계층이 검증 후 전달한 고객 ID",
                    required = true,
                    example = "710000000000000001"
            )
            @RequestHeader("X-Customer-Id") @NotNull @Positive Long customerId,
            @Parameter(
                    description = "하이픈 없는 13자리 저금통 계좌번호",
                    required = true,
                    example = "3310000012345"
            )
            @PathVariable @Pattern(regexp = "[0-9]{13}") String accountNumber
    ) {
        log.debug("저금통 비우기 요청을 처리합니다. customerId={}", customerId);
        return EmptyCoinBoxResponse.from(coinBoxService.emptyCoinBox(customerId, accountNumber));
    }

    /**
     * 잔액 정리 후 저금통 계좌, 계약과 동전모으기 설정을 종료한다.
     */
    @DeleteMapping("/{accountNumber}")
    @Operation(
            summary = "저금통 해지",
            description = "잔액이 있으면 연결 입출금계좌로 전액 이전한 뒤 계좌·계약·동전모으기를 종료합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "해지 성공",
                    content = @Content(schema = @Schema(implementation = TerminateCoinBoxResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 헤더 또는 계좌번호 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "인증 고객 소유의 저금통을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 해지됐거나 해지 가능한 데이터·계좌 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public TerminateCoinBoxResponse terminateCoinBox(
            @Parameter(
                    name = "X-Customer-Id",
                    description = "인증·게이트웨이 계층이 검증 후 전달한 고객 ID",
                    required = true,
                    example = "710000000000000001"
            )
            @RequestHeader("X-Customer-Id") @NotNull @Positive Long customerId,
            @Parameter(
                    description = "하이픈 없는 13자리 저금통 계좌번호",
                    required = true,
                    example = "3310000012345"
            )
            @PathVariable @Pattern(regexp = "[0-9]{13}") String accountNumber
    ) {
        log.debug("저금통 해지 요청을 처리합니다. customerId={}", customerId);
        return TerminateCoinBoxResponse.from(coinBoxService.terminateCoinBox(customerId, accountNumber));
    }
}
