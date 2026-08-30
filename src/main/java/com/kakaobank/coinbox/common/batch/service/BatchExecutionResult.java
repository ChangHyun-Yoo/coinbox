package com.kakaobank.coinbox.common.batch.service;

import org.springframework.batch.core.BatchStatus;

import java.time.LocalDate;

/**
 * 스케줄 실행과 수동 실행이 공통으로 사용하는 배치 실행 결과입니다.
 */
public record BatchExecutionResult(
        String jobExecutionId,
        String jobName,
        BatchStatus status,
        LocalDate executionDate
) {
}
