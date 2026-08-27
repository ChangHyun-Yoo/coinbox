package com.kakaobank.coinbox.coinbox.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 저금통 계좌에 종속된 고객별 동전모으기 설정을 관리한다.
 */
@Getter
@Entity
@Table(name = "COINBOX")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinBox extends BaseEntity {

    @Id
    @Column(name = "coinbox_id", nullable = false, updatable = false)
    private Long coinBoxId;

    @Column(nullable = false, unique = true, updatable = false)
    private Long accountId;

    @Column(nullable = false)
    private boolean coinSavingEnabled;

    private LocalDate coinSavingStartDate;

    /**
     * 개설 당일부터 동전모으기가 활성화된 저금통 설정을 생성한다.
     */
    public static CoinBox create(Long coinBoxId, Long accountId, LocalDate coinSavingStartDate) {
        if (coinSavingStartDate == null) {
            throw new IllegalArgumentException("coinSavingStartDate must not be null");
        }
        CoinBox coinBox = new CoinBox();
        coinBox.coinBoxId = coinBoxId;
        coinBox.accountId = accountId;
        coinBox.coinSavingEnabled = true;
        coinBox.coinSavingStartDate = coinSavingStartDate;
        return coinBox;
    }

    /**
     * 해지된 저금통이 배치 후보로 다시 선택되지 않도록 설정과 시작일을 함께 종료한다.
     */
    public void disableCoinSaving() {
        coinSavingEnabled = false;
        coinSavingStartDate = null;
    }
}
