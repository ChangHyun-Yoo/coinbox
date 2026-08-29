package com.kakaobank.coinbox.coinsavingexecution.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 잠금 없는 후보 페이징과 후보별 독립 트랜잭션 처리를 연결하는 동전모으기 Chunk Job이다.
 */
@Configuration
@Slf4j
public class CoinSavingJobConfig {

    public static final String JOB_NAME = "coinSavingJob";
    public static final String STEP_NAME = "coinSavingStep";
    private static final int PAGE_SIZE = 1_000;
    private static final int CHUNK_SIZE = 1;

    /**
     * 동전모으기 Chunk Step 하나로 구성된 Job을 등록한다.
     */
    @Bean
    public Job coinSavingJob(JobRepository jobRepository, Step coinSavingStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(coinSavingStep)
                .build();
    }

    /**
     * 후보 한 건마다 독립 트랜잭션으로 처리하는 Chunk Step을 등록한다.
     */
    @Bean
    public Step coinSavingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<CoinSavingCandidate> coinSavingReader,
            CoinSavingItemWriter coinSavingItemWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<CoinSavingCandidate, CoinSavingCandidate>chunk(CHUNK_SIZE)
                .reader(coinSavingReader)
                .writer(coinSavingItemWriter)
                .transactionManager(transactionManager)
                .build();
    }

    /**
     * 잠금 없이 미처리 후보를 저금통 ID 순서로 페이징 조회한다.
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<CoinSavingCandidate> coinSavingReader(
            DataSource dataSource,
            @Value("#{jobParameters['executionDate']}") LocalDate executionDate,
            @Value("#{jobParameters['previousDate']}") LocalDate previousDate
    ) throws Exception {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
                select
                    cb.coinbox_id,
                    cb.account_id as coinbox_account_id,
                    ca.parent_account_id,
                    adb.closing_balance
                """);
        queryProvider.setFromClause("""
                from coinbox cb
                join account ca
                  on ca.account_id = cb.account_id
                left join account_daily_balance adb
                  on adb.account_id = ca.parent_account_id
                 and adb.balance_date = :previousDate
                """);
        queryProvider.setWhereClause("""
                where cb.coin_saving_enabled = true
                  and cb.coin_saving_start_date < :executionDate
                  and ca.parent_account_id is not null
                  and not exists (
                      select 1
                      from coin_saving_execution cse
                      where cse.coinbox_id = cb.coinbox_id
                        and cse.execution_date = :executionDate
                  )
                """);
        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("coinbox_id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        return new JdbcPagingItemReaderBuilder<CoinSavingCandidate>()
                .name("coinSavingReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of(
                        "executionDate", executionDate,
                        "previousDate", previousDate
                ))
                .pageSize(PAGE_SIZE)
                .fetchSize(PAGE_SIZE)
                .rowMapper((resultSet, rowNumber) -> new CoinSavingCandidate(
                        resultSet.getLong("coinbox_id"),
                        resultSet.getLong("coinbox_account_id"),
                        resultSet.getLong("parent_account_id"),
                        resultSet.getObject("closing_balance", Long.class),
                        executionDate
                ))
                .build();
    }
}
