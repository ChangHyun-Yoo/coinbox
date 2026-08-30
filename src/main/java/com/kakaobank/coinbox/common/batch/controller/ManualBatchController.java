package com.kakaobank.coinbox.common.batch.controller;

import com.kakaobank.coinbox.common.batch.response.ManualBatchExecutionResponse;
import com.kakaobank.coinbox.common.batch.service.BatchExecutionService;
import com.kakaobank.coinbox.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 채점 및 운영 검증을 위해 지정 업무일의 배치 Job을 수동으로 시작합니다.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/internal/v1/batches", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "배치 수동 실행")
public class ManualBatchController {

    private final BatchExecutionService batchExecutionService;

    /**
     * 지정 실행일의 전날 잔액을 일별 최종 잔액으로 저장합니다.
     */
    @PostMapping("/daily-balance")
    @Operation(
            summary = "일별 최종 잔액 배치 수동 실행",
            description = "executionDate의 전날을 기준일로 사용해 일별 최종 잔액 Job을 즉시 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "실행 요청 처리 완료",
                    content = @Content(schema = @Schema(implementation = ManualBatchExecutionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "실행일 누락 또는 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Job 시작 또는 실행 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ManualBatchExecutionResponse executeDailyBalance(
            @Parameter(description = "배치 업무 실행일", required = true, example = "2026-08-30")
            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate executionDate
    ) {
        log.info("일별 최종 잔액 배치 수동 실행을 요청했습니다. executionDate={}", executionDate);
        return ManualBatchExecutionResponse.from(batchExecutionService.executeDailyBalance(executionDate));
    }

    /**
     * 지정 실행일을 기준으로 동전모으기를 처리합니다.
     */
    @PostMapping("/coin-saving")
    @Operation(
            summary = "동전모으기 배치 수동 실행",
            description = "executionDate와 그 전날 잔액을 기준으로 동전모으기 Job을 즉시 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "실행 요청 처리 완료",
                    content = @Content(schema = @Schema(implementation = ManualBatchExecutionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "실행일 누락 또는 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Job 시작 또는 실행 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ManualBatchExecutionResponse executeCoinSaving(
            @Parameter(description = "배치 업무 실행일", required = true, example = "2026-08-30")
            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate executionDate
    ) {
        log.info("동전모으기 배치 수동 실행을 요청했습니다. executionDate={}", executionDate);
        return ManualBatchExecutionResponse.from(batchExecutionService.executeCoinSaving(executionDate));
    }
}
