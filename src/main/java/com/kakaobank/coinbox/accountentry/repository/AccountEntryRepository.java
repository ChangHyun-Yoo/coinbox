package com.kakaobank.coinbox.accountentry.repository;

import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 금융거래에 속한 계좌별 원장 내역을 저장하고 조회한다.
 */
public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {
}
