package com.kakaobank.coinbox.coinsavingexecution.repository;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("동전모으기 실행 Repository")
class CoinSavingExecutionRepositoryTest {

    @Autowired
    private CoinSavingExecutionRepository coinSavingExecutionRepository;

    @Test
    @DisplayName("같은 저금통과 실행일의 이력을 중복 저장할 수 없다")
    void rejectsDuplicatedCoinBoxAndExecutionDate() {
        // given: PK만 다르고 저금통과 실행일이 같은 두 건너뜀 이력을 준비한다.
        LocalDate executionDate = LocalDate.of(2026, 8, 27);
        CoinSavingExecution first = CoinSavingExecution.skipped(
                70L, 30L, executionDate, CoinSavingReasonCode.NO_SAVING_AMOUNT
        );
        CoinSavingExecution second = CoinSavingExecution.skipped(
                71L, 30L, executionDate, CoinSavingReasonCode.INSUFFICIENT_BALANCE
        );
        coinSavingExecutionRepository.saveAndFlush(first);

        // when & then: coinbox_id와 execution_date 복합 UK가 중복 저장을 거부한다.
        assertThatThrownBy(() -> coinSavingExecutionRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("하나의 금융거래를 여러 실행 이력에 연결할 수 없다")
    void rejectsDuplicatedTransactionId() {
        // given: 서로 다른 저금통 실행이 같은 금융거래를 참조한다.
        CoinSavingExecution first = CoinSavingExecution.success(
                72L, 30L, 80L, LocalDate.of(2026, 8, 27), 670L
        );
        CoinSavingExecution second = CoinSavingExecution.success(
                73L, 31L, 80L, LocalDate.of(2026, 8, 28), 570L
        );
        coinSavingExecutionRepository.saveAndFlush(first);

        // when & then: transaction_id UK가 두 번째 연결을 거부한다.
        assertThatThrownBy(() -> coinSavingExecutionRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저금통과 실행일로 처리 이력 존재 여부를 확인한다")
    void checksExecutionExistenceByCoinBoxAndDate() {
        // given: 특정 저금통의 건너뜀 이력을 저장한다.
        LocalDate executionDate = LocalDate.of(2026, 8, 27);
        coinSavingExecutionRepository.saveAndFlush(CoinSavingExecution.skipped(
                74L, 32L, executionDate, CoinSavingReasonCode.COINBOX_LIMIT_REACHED
        ));

        // when: 같은 저금통과 실행일로 존재 여부를 조회한다.
        boolean exists = coinSavingExecutionRepository.existsByCoinBoxIdAndExecutionDate(32L, executionDate);

        // then: 이미 처리된 실행으로 확인된다.
        assertThat(exists).isTrue();
    }
}
