package com.kakaobank.coinbox.common.batch.service;

import com.kakaobank.coinbox.accountdailybalance.batch.DailyBalanceJobConfig;
import com.kakaobank.coinbox.coinsavingexecution.batch.CoinSavingJobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 자동 스케줄과 운영자 수동 요청에 동일한 업무 날짜 파라미터로 Spring Batch Job을 실행합니다.
 *
 * <p>Job과 Step이 자체 트랜잭션을 관리하므로 이 실행 조정 서비스에는 외부 트랜잭션을 적용하지 않습니다.</p>
 */
@Slf4j
@Service
public class BatchExecutionService {

    private final JobOperator jobOperator;
    private final Job dailyBalanceJob;
    private final Job coinSavingJob;
    private final Clock clock;
    private final AtomicLong launchSequence = new AtomicLong();

    /**
     * 이름이 같은 Job을 명확히 주입하고 서울 기준 시계를 보관합니다.
     */
    public BatchExecutionService(
            JobOperator jobOperator,
            @Qualifier(DailyBalanceJobConfig.JOB_NAME) Job dailyBalanceJob,
            @Qualifier(CoinSavingJobConfig.JOB_NAME) Job coinSavingJob,
            Clock clock
    ) {
        this.jobOperator = jobOperator;
        this.dailyBalanceJob = dailyBalanceJob;
        this.coinSavingJob = coinSavingJob;
        this.clock = clock;
    }

    /**
     * 지정 실행일의 전날을 잔액 기준일로 설정해 일별 최종 잔액 Job을 실행합니다.
     */
    public BatchExecutionResult executeDailyBalance(LocalDate executionDate) {
        JobParameters parameters = commonParameters(executionDate)
                .addLocalDate("balanceDate", executionDate.minusDays(1))
                .toJobParameters();
        return run(dailyBalanceJob, parameters, executionDate);
    }

    /**
     * 지정 실행일과 전날을 조회 기준으로 설정해 동전모으기 Job을 실행합니다.
     */
    public BatchExecutionResult executeCoinSaving(LocalDate executionDate) {
        JobParameters parameters = commonParameters(executionDate)
                .addLocalDate("previousDate", executionDate.minusDays(1))
                .toJobParameters();
        return run(coinSavingJob, parameters, executionDate);
    }

    /**
     * 같은 업무 날짜를 안전하게 재실행할 수 있도록 실행별 식별 파라미터를 함께 생성합니다.
     */
    private JobParametersBuilder commonParameters(LocalDate executionDate) {
        if (executionDate == null) {
            throw new IllegalArgumentException("executionDate must not be null");
        }
        return new JobParametersBuilder()
                .addLocalDate("executionDate", executionDate)
                .addLocalDateTime("launchedAt", LocalDateTime.now(clock))
                .addLong("launchSequence", launchSequence.incrementAndGet());
    }

    /**
     * Job을 시작하고 수동 실행 응답과 스케줄 로그에 사용할 최소 결과를 반환합니다.
     */
    private BatchExecutionResult run(Job job, JobParameters parameters, LocalDate executionDate) {
        try {
            JobExecution execution = jobOperator.start(job, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                log.warn("배치 Job이 완료 상태가 아닙니다. jobName={}, status={}", job.getName(), execution.getStatus());
            }
            return new BatchExecutionResult(
                    String.valueOf(execution.getId()),
                    job.getName(),
                    execution.getStatus(),
                    executionDate
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to launch batch job: " + job.getName(), exception);
        }
    }
}
