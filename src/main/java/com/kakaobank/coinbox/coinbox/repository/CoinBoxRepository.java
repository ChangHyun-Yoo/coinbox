package com.kakaobank.coinbox.coinbox.repository;

import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinBoxRepository extends JpaRepository<CoinBox, Long> {
}
