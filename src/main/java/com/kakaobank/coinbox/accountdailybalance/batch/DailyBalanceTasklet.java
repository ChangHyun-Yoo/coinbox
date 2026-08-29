package com.kakaobank.coinbox.accountdailybalance.batch;

import com.kakaobank.coinbox.accountdailybalance.repository.DailyBalanceJdbcRepository;
import com.kakaobank.coinbox.accountdailybalance.repository.DailyBalanceQueryRepository;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ACTIVE·RESTRICTED 계좌의 현재 잔액을 지정 기준일의 일별 최종 잔액으로 일괄 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyBalanceTasklet implements Tasklet {

    private static final String BALANCE_DATE_PARAMETER = "balanceDate";

    private final DailyBalanceQueryRepository queryRepository;
    private final DailyBalanceJdbcRepository jdbcRepository;
    private final Snowflake snowflake;
    private final Clock clock;

    /**
     * 대상 계좌를 읽고 같은 감사 시각을 적용한 일별 잔액 행을 일괄 저장한다.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate balanceDate = resolveBalanceDate(chunkContext);
        List<AccountBalanceSnapshot> targets = queryRepository.findSnapshotTargets();
        LocalDateTime auditedAt = LocalDateTime.now(clock);
        List<DailyBalanceInsertRow> rows = targets.stream()
                .map(target -> new DailyBalanceInsertRow(
                        snowflake.nextId(),
                        target.accountId(),
                        balanceDate,
                        target.balance(),
                        auditedAt,
                        auditedAt
                ))
                .toList();

        int insertedCount = jdbcRepository.batchInsertIfAbsent(rows);
        contribution.incrementWriteCount(insertedCount);
        log.debug("일별 최종 잔액 Tasklet을 완료했습니다. balanceDate={}, targets={}, inserted={}",
                balanceDate, targets.size(), insertedCount);
        return RepeatStatus.FINISHED;
    }

    /**
     * Job Parameter에서 저장 기준일을 타입에 맞게 해석한다.
     */
    private LocalDate resolveBalanceDate(ChunkContext chunkContext) {
        Object value = chunkContext.getStepContext().getJobParameters().get(BALANCE_DATE_PARAMETER);
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof String text) {
            return LocalDate.parse(text);
        }
        throw new IllegalArgumentException("balanceDate JobParameter is required");
    }
}
