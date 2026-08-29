package com.kakaobank.coinbox.common.batch;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 서울 시간 기준 실행일을 JobParameter로 고정해 배치 업무 날짜가 서버 기본 시간대에 흔들리지 않게 한다.
 */
@Slf4j
@Component
public class BatchScheduler {

    private final JobOperator jobOperator;
    private final Job dailyBalanceJob;
    private final Job coinSavingJob;
    private final Clock clock;
    private final AtomicLong launchSequence = new AtomicLong();

    /**
     * 이름이 같은 Job을 명확히 주입하고 서울 기준 시계를 보관한다.
     */
    public BatchScheduler(
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
     * 매일 자정에 전날의 최종 잔액 스냅샷 Job을 시작한다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void launchDailyBalanceJob() {
        launchDailyBalanceJob(LocalDate.now(clock));
    }

    /**
     * 평일 오전 10시에 공휴일과 무관하게 동전모으기 Job을 시작한다.
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI", zone = "Asia/Seoul")
    public void launchCoinSavingJob() {
        launchCoinSavingJob(LocalDate.now(clock));
    }

    /**
     * 지정 실행일의 전날을 잔액 기준일로 설정해 Job을 실행한다.
     */
    public JobExecution launchDailyBalanceJob(LocalDate executionDate) {
        JobParameters parameters = commonParameters(executionDate)
                .addLocalDate("balanceDate", executionDate.minusDays(1))
                .toJobParameters();
        return run(dailyBalanceJob, parameters);
    }

    /**
     * 지정 실행일과 전날을 동전모으기 조회 기준으로 설정해 Job을 실행한다.
     */
    public JobExecution launchCoinSavingJob(LocalDate executionDate) {
        JobParameters parameters = commonParameters(executionDate)
                .addLocalDate("previousDate", executionDate.minusDays(1))
                .toJobParameters();
        return run(coinSavingJob, parameters);
    }

    /**
     * 재실행을 구분할 공통 날짜, 시각과 순번 파라미터를 생성한다.
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
     * Job을 시작하고 비정상 종료 상태와 실행 예외를 호출자에게 알린다.
     */
    private JobExecution run(Job job, JobParameters parameters) {
        try {
            JobExecution execution = jobOperator.start(job, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                log.warn("배치 Job이 완료 상태가 아닙니다. jobName={}, status={}", job.getName(), execution.getStatus());
            }
            return execution;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to launch batch job: " + job.getName(), exception);
        }
    }
}
