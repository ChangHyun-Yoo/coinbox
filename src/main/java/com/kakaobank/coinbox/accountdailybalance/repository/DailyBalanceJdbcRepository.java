package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.accountdailybalance.batch.DailyBalanceInsertRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * 일별 잔액을 한 번의 JDBC 배치로 저장한다. 조회는 JPA Native Query Repository가 담당한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DailyBalanceJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 이미 성공한 같은 계좌·기준일 스냅샷은 덮어쓰지 않고 없는 행만 추가한다.
     */
    public int batchInsertIfAbsent(List<DailyBalanceInsertRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        String sql = """
                insert into account_daily_balance (
                    account_daily_balance_id,
                    account_id,
                    balance_date,
                    closing_balance,
                    created_datetime,
                    updated_datetime
                ) values (?, ?, ?, ?, ?, ?)
                on duplicate key update
                    account_daily_balance_id = account_daily_balance_id
                """;

        int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            /**
             * 현재 배치 행을 PreparedStatement 파라미터 순서에 맞게 설정한다.
             */
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                DailyBalanceInsertRow row = rows.get(index);
                statement.setLong(1, row.accountDailyBalanceId());
                statement.setLong(2, row.accountId());
                statement.setObject(3, row.balanceDate());
                statement.setLong(4, row.closingBalance());
                statement.setObject(5, row.createdDatetime());
                statement.setObject(6, row.updatedDatetime());
            }

            /**
             * JDBC 드라이버가 처리할 전체 행 수를 반환한다.
             */
            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });

        int insertedCount = Arrays.stream(results)
                .map(result -> result > 0 ? 1 : 0)
                .sum();
        log.debug("일별 잔액 JDBC 일괄 저장을 완료했습니다. requested={}, inserted={}", rows.size(), insertedCount);
        return insertedCount;
    }
}
