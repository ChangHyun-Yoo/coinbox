package com.kakaobank.coinbox.accountdailybalance.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Auditing을 거치지 않는 JDBC 일괄 저장에 필요한 전체 컬럼을 명시한다.
 */
public record DailyBalanceInsertRow(
        Long accountDailyBalanceId,
        Long accountId,
        LocalDate balanceDate,
        Long closingBalance,
        LocalDateTime createdDatetime,
        LocalDateTime updatedDatetime
) {
}
