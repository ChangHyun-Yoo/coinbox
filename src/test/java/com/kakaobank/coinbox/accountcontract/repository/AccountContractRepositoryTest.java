package com.kakaobank.coinbox.accountcontract.repository;

import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("계좌 계약 Repository")
class AccountContractRepositoryTest {

    @Autowired
    private AccountContractRepository accountContractRepository;

    @Test
    @DisplayName("활성 계약은 상품 버전과 9999년 종료일을 저장한다")
    void savesActiveContractWithProductVersion() {
        // given: 저금통 상품 버전으로 시작하는 계약을 준비한다.
        AccountContract contract = AccountContract.create(
                20L,
                10L,
                200L,
                LocalDate.of(2026, 8, 27)
        );

        // when: 계약을 저장하고 조회한다.
        accountContractRepository.saveAndFlush(contract);
        AccountContract foundContract = accountContractRepository.findById(20L).orElseThrow();

        // then: 활성 상태와 상품 버전, 미종료일이 유지된다.
        assertThat(foundContract)
                .returns(200L, AccountContract::getProductVersionId)
                .returns(ContractStatus.ACTIVE, AccountContract::getContractStatus)
                .returns(AccountContract.ACTIVE_CONTRACT_END_DATE, AccountContract::getContractEndDate);
    }
}
