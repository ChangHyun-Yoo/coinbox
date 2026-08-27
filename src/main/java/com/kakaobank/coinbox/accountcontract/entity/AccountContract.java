package com.kakaobank.coinbox.accountcontract.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "ACCOUNT_CONTRACT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountContract extends BaseEntity {

    public static final LocalDate ACTIVE_CONTRACT_END_DATE = LocalDate.of(9999, 12, 31);

    @Id
    @Column(nullable = false, updatable = false)
    private Long accountContractId;

    @Column(nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false)
    private Long productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus contractStatus;

    @Column(nullable = false, updatable = false)
    private LocalDate contractStartDate;

    @Column(nullable = false)
    private LocalDate contractEndDate;

    /**
     * 계약 당시 선택한 상품 버전과 미종료일을 함께 저장해 정책 적용 기준을 고정한다.
     */
    public static AccountContract create(
            Long accountContractId,
            Long accountId,
            Long productVersionId,
            LocalDate contractStartDate
    ) {
        AccountContract accountContract = new AccountContract();
        accountContract.accountContractId = accountContractId;
        accountContract.accountId = accountId;
        accountContract.productVersionId = productVersionId;
        accountContract.contractStatus = ContractStatus.ACTIVE;
        accountContract.contractStartDate = contractStartDate;
        accountContract.contractEndDate = ACTIVE_CONTRACT_END_DATE;
        return accountContract;
    }

    /**
     * 활성 계약을 실제 종료일로 마감한다. 계약 이력의 기간이 역전되지 않도록 시작일 이후만 허용한다.
     */
    public void terminate(LocalDate terminationDate) {
        if (contractStatus != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Only active contract can be terminated");
        }
        if (terminationDate == null || !contractStartDate.isBefore(terminationDate)) {
            throw new IllegalArgumentException("terminationDate must be after contractStartDate");
        }
        contractStatus = ContractStatus.TERMINATED;
        contractEndDate = terminationDate;
    }
}
