package com.kakaobank.coinbox.coinbox.repository;

import com.kakaobank.coinbox.coinbox.entity.CoinBox;
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
@DisplayName("저금통 Repository")
class CoinBoxRepositoryTest {

    @Autowired
    private CoinBoxRepository coinBoxRepository;

    @Test
    @DisplayName("저금통은 개설 당일부터 동전모으기가 활성화된다")
    void savesEnabledCoinBox() {
        // given: 개설 당일이 설정된 저금통을 준비한다.
        LocalDate openDate = LocalDate.of(2026, 8, 27);
        CoinBox coinBox = CoinBox.create(30L, 10L, openDate);

        // when: 저금통 설정을 저장하고 조회한다.
        coinBoxRepository.saveAndFlush(coinBox);
        CoinBox foundCoinBox = coinBoxRepository.findById(30L).orElseThrow();

        // then: 계좌 연결과 동전모으기 설정이 유지된다.
        assertThat(foundCoinBox)
                .returns(10L, CoinBox::getAccountId)
                .returns(true, CoinBox::isCoinSavingEnabled)
                .returns(openDate, CoinBox::getCoinSavingStartDate);
    }

    @Test
    @DisplayName("하나의 계좌에는 저금통 설정을 하나만 저장한다")
    void rejectsDuplicatedAccountId() {
        // given: 같은 계좌를 참조하는 저금통 설정 두 개를 준비한다.
        LocalDate openDate = LocalDate.of(2026, 8, 27);
        coinBoxRepository.saveAndFlush(CoinBox.create(31L, 11L, openDate));

        // when & then: account_id UK가 두 번째 설정을 거부한다.
        assertThatThrownBy(() -> coinBoxRepository.saveAndFlush(CoinBox.create(32L, 11L, openDate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
