package com.kakaobank.coinbox.accountdailybalance.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일별 최종 잔액을 하나의 Tasklet Step 트랜잭션으로 실행한다.
 */
@Configuration
@Slf4j
public class DailyBalanceJobConfig {

    public static final String JOB_NAME = "dailyBalanceJob";
    public static final String STEP_NAME = "dailyBalanceStep";

    /**
     * 일별 잔액 Step 하나로 구성된 Job을 등록한다.
     */
    @Bean
    public Job dailyBalanceJob(JobRepository jobRepository, Step dailyBalanceStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(dailyBalanceStep)
                .build();
    }

    /**
     * 대량 스냅샷을 한 SQL 배치로 저장하는 Tasklet Step을 등록한다.
     */
    @Bean
    public Step dailyBalanceStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            DailyBalanceTasklet tasklet
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
