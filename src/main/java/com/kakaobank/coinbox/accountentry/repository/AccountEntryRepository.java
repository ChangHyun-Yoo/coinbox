package com.kakaobank.coinbox.accountentry.repository;

import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {
}
