package com.kakaobank.coinbox.accountcontract.repository;

import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountContractRepository extends JpaRepository<AccountContract, Long> {
}
