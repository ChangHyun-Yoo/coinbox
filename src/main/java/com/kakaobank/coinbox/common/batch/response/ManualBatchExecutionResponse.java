package com.kakaobank.coinbox.common.batch.response;

import com.kakaobank.coinbox.common.batch.service.BatchExecutionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.batch.core.BatchStatus;

import java.time.LocalDate;

/**
 * 운영자가 수동으로 시작한 배치 Job의 식별 정보와 실행 상태를 반환합니다.
 */
@Schema(description = "수동 배치 실행 결과")
public record ManualBatchExecutionResponse(
        @Schema(description = "Spring Batch JobExecution ID", example = "1")
        String jobExecutionId,
        @Schema(description = "실행한 Job 이름", example = "dailyBalanceJob")
        String jobName,
        @Schema(description = "Job 실행 상태", example = "COMPLETED")
        BatchStatus status,
        @Schema(description = "배치 업무 실행일", example = "2026-08-30", format = "date")
        LocalDate executionDate
) {

    /**
     * 내부 배치 실행 결과를 HTTP 응답 형식으로 변환합니다.
     */
    public static ManualBatchExecutionResponse from(BatchExecutionResult result) {
        return new ManualBatchExecutionResponse(
                result.jobExecutionId(),
                result.jobName(),
                result.status(),
                result.executionDate()
        );
    }
}
