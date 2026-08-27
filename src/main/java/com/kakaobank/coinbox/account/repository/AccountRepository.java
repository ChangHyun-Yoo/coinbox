package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
