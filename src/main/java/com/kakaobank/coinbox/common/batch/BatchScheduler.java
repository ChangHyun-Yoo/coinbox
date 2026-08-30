package com.kakaobank.coinbox.common.batch;

import com.kakaobank.coinbox.common.batch.service.BatchExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 서울 시간 기준 스케줄 실행일을 공통 배치 실행 서비스에 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final BatchExecutionService batchExecutionService;
    private final Clock clock;

    /**
     * 매일 자정에 전날의 최종 잔액 스냅샷 Job을 시작합니다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void launchDailyBalanceJob() {
        LocalDate executionDate = LocalDate.now(clock);
        log.info("일별 최종 잔액 배치 스케줄을 실행합니다. executionDate={}", executionDate);
        batchExecutionService.executeDailyBalance(executionDate);
    }

    /**
     * 평일 오전 10시에 공휴일과 무관하게 동전모으기 Job을 시작합니다.
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI", zone = "Asia/Seoul")
    public void launchCoinSavingJob() {
        LocalDate executionDate = LocalDate.now(clock);
        log.info("동전모으기 배치 스케줄을 실행합니다. executionDate={}", executionDate);
        batchExecutionService.executeCoinSaving(executionDate);
    }
}
