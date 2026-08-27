package com.kakaobank.coinbox.accountentry.repository;

import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
import com.kakaobank.coinbox.accountentry.entity.EntryType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@DisplayName("계좌 원장 Repository")
class AccountEntryRepositoryTest extends MySqlTestContainer {

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @Test
    @DisplayName("비우기 출금 원장에 거래 일시와 통장 적요를 저장한다")
    void savesEmptyWithdrawalEntryWithDescription() {
        // given: 저금통 비우기로 4,360원을 출금한 원장을 준비한다.
        LocalDateTime transactionDatetime = LocalDateTime.of(2026, 8, 27, 14, 0);
        AccountEntry entry = AccountEntry.withdrawal(
                40L,
                10L,
                50L,
                EntryCode.COINBOX_EMPTY,
                4_360L,
                4_360L,
                transactionDatetime,
                "비우기"
        );

        // when: 계좌 원장을 저장하고 조회한다.
        accountEntryRepository.saveAndFlush(entry);
        AccountEntry foundEntry = accountEntryRepository.findById(40L).orElseThrow();

        // then: 출금 방향, 비우기 코드, 거래 일시와 적요가 유지된다.
        assertThat(foundEntry)
                .returns(EntryType.WITHDRAWAL, AccountEntry::getEntryType)
                .returns(EntryCode.COINBOX_EMPTY, AccountEntry::getEntryCode)
                .returns(0L, AccountEntry::getBalanceAfter)
                .returns(transactionDatetime, AccountEntry::getTransactionDatetime)
                .returns("비우기", AccountEntry::getEntryDescription);
    }
}
