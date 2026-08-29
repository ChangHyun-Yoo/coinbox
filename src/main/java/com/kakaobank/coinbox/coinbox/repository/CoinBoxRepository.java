package com.kakaobank.coinbox.coinbox.repository;

import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 저금통 설정 저장과 계좌 기준 비관적 잠금 조회를 담당한다.
 */
public interface CoinBoxRepository extends JpaRepository<CoinBox, Long> {

    /**
     * 계좌에 연결된 저금통 설정을 잠가 동시 설정 변경을 막는다.
     */
    @Query(value = """
            select cb.*
            from coinbox cb
            where cb.account_id = :accountId
            for update
            """, nativeQuery = true)
    Optional<CoinBox> findByAccountIdForUpdate(@Param("accountId") Long accountId);
}
