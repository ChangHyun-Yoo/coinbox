package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicySnapshot;
import com.kakaobank.coinbox.coinsavingexecution.batch.CoinSavingCandidate;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;
import com.kakaobank.coinbox.coinsavingexecution.repository.CoinSavingExecutionRepository;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import com.kakaobank.coinbox.financialtransaction.service.InternalTransferService;
import com.kakaobank.coinbox.financialtransaction.service.TransferLedgerSpec;
import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
import com.kakaobank.coinbox.product.entity.ProductType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 동전모으기 후보 한 건의 잠금, 상태·정책 재검증, 이체와 실행 이력 저장을 조정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoinSavingService {

    private static final TransferLedgerSpec COIN_SAVING_LEDGER_SPEC = new TransferLedgerSpec(
            EntryCode.COINBOX,
            EntryCode.COINBOX.getDescription(),
            EntryCode.COIN_SAVING,
            EntryCode.COIN_SAVING.getDescription()
    );

    private final AccountRepository accountRepository;
    private final CoinBoxRepository coinBoxRepository;
    private final AccountContractRepository accountContractRepository;
    private final CoinSavingPolicyResolver coinSavingPolicyResolver;
    private final CoinSavingExecutionRepository coinSavingExecutionRepository;
    private final CoinSavingAmountCalculator amountCalculator;
    private final InternalTransferService internalTransferService;
    private final Snowflake snowflake;

    /**
     * 청크 크기 1의 트랜잭션에 참여해 한 후보의 금융 처리와 실행 이력을 함께 Commit한다.
     */
    @Transactional
    public CoinSavingResult execute(CoinSavingCandidate candidate) {
        validateCandidate(candidate);

        List<Long> sortedAccountIds = candidate.parentAccountId() < candidate.coinBoxAccountId()
                ? List.of(candidate.parentAccountId(), candidate.coinBoxAccountId())
                : List.of(candidate.coinBoxAccountId(), candidate.parentAccountId());
        List<Account> accounts = accountRepository.findAllByIdForUpdateOrderByAccountId(sortedAccountIds);
        if (accounts.size() != 2) {
            throw new IllegalStateException("Coin saving accounts are missing");
        }

        Account parentAccount = findAccount(accounts, candidate.parentAccountId());
        Account coinBoxAccount = findAccount(accounts, candidate.coinBoxAccountId());
        CoinBox coinBox = coinBoxRepository.findByAccountIdForUpdate(candidate.coinBoxAccountId())
                .orElseThrow(() -> new IllegalStateException("CoinBox configuration is missing"));
        AccountContract contract = accountContractRepository
                .findLatestByAccountIdForUpdate(candidate.coinBoxAccountId())
                .orElseThrow(() -> new IllegalStateException("CoinBox contract is missing"));

        validateRelationships(candidate, parentAccount, coinBoxAccount, coinBox);
        if (coinSavingExecutionRepository.existsByCoinBoxIdAndExecutionDate(
                coinBox.getCoinBoxId(), candidate.executionDate())) {
            return CoinSavingResult.duplicate();
        }
        if (!isSavingSettingEffective(coinBox, candidate.executionDate())) {
            return CoinSavingResult.excluded();
        }
        if (parentAccount.getAccountStatus() != AccountStatus.ACTIVE
                || coinBoxAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            return saveSkipped(coinBox.getCoinBoxId(), candidate.executionDate(),
                    CoinSavingReasonCode.ACCOUNT_NOT_ACTIVE);
        }

        validateContract(contract, candidate.executionDate());
        CoinBoxPolicySnapshot policy = coinSavingPolicyResolver.resolve(contract.getProductVersionId());

        CoinSavingCalculation calculation = amountCalculator.calculate(
                candidate.previousClosingBalance(),
                parentAccount.getBalance(),
                coinBoxAccount.getBalance(),
                policy.maxAmount()
        );
        if (!calculation.transferable()) {
            return saveSkipped(
                    coinBox.getCoinBoxId(),
                    candidate.executionDate(),
                    calculation.reasonCode()
            );
        }

        TransferResult transfer = internalTransferService.transferLocked(
                parentAccount,
                coinBoxAccount,
                calculation.savingAmount(),
                COIN_SAVING_LEDGER_SPEC
        );
        CoinSavingExecution execution = CoinSavingExecution.success(
                snowflake.nextId(),
                coinBox.getCoinBoxId(),
                transfer.transactionId(),
                candidate.executionDate(),
                calculation.savingAmount()
        );
        coinSavingExecutionRepository.save(execution);

        log.debug("동전모으기 성공 이력을 생성했습니다. coinBoxId={}, transactionId={}",
                coinBox.getCoinBoxId(), transfer.transactionId());
        return CoinSavingResult.success(calculation.savingAmount(), transfer.transactionId());
    }

    /**
     * 자금 이동 없이 끝난 업무 사유를 일별 실행 이력으로 저장한다.
     */
    private CoinSavingResult saveSkipped(
            Long coinBoxId,
            LocalDate executionDate,
            CoinSavingReasonCode reasonCode
    ) {
        coinSavingExecutionRepository.save(CoinSavingExecution.skipped(
                snowflake.nextId(), coinBoxId, executionDate, reasonCode
        ));
        log.debug("동전모으기 건너뜀 이력을 생성했습니다. coinBoxId={}, reasonCode={}", coinBoxId, reasonCode);
        return CoinSavingResult.skipped(reasonCode);
    }

    /**
     * Reader가 전달한 후보에 처리에 필요한 식별자와 실행일이 있는지 확인한다.
     */
    private void validateCandidate(CoinSavingCandidate candidate) {
        if (candidate == null
                || candidate.coinBoxId() == null
                || candidate.coinBoxAccountId() == null
                || candidate.parentAccountId() == null
                || candidate.executionDate() == null) {
            throw new IllegalArgumentException("CoinSavingCandidate has required null values");
        }
        if (candidate.coinBoxAccountId().equals(candidate.parentAccountId())) {
            throw new IllegalArgumentException("CoinBox and parent account must be different");
        }
    }

    /**
     * 오름차순으로 잠근 계좌 목록에서 요청한 계좌를 찾는다.
     */
    private Account findAccount(List<Account> accounts, Long accountId) {
        return accounts.stream()
                .filter(account -> account.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked account is missing"));
    }

    /**
     * 후보와 잠금 후 조회한 고객·계좌·저금통 연결 관계를 재검증한다.
     */
    private void validateRelationships(
            CoinSavingCandidate candidate,
            Account parentAccount,
            Account coinBoxAccount,
            CoinBox coinBox
    ) {
        if (!coinBox.getCoinBoxId().equals(candidate.coinBoxId())
                || !coinBox.getAccountId().equals(coinBoxAccount.getAccountId())
                || !candidate.parentAccountId().equals(coinBoxAccount.getParentAccountId())
                || !parentAccount.getCustomerId().equals(coinBoxAccount.getCustomerId())
                || parentAccount.getProductType() != ProductType.DEMAND_DEPOSIT
                || coinBoxAccount.getProductType() != ProductType.COINBOX) {
            throw new IllegalStateException("CoinBox account relationship is invalid");
        }
    }

    /**
     * 동전모으기가 켜져 있고 시작일 다음 날부터 적용되는지 확인한다.
     */
    private boolean isSavingSettingEffective(CoinBox coinBox, LocalDate executionDate) {
        return coinBox.isCoinSavingEnabled()
                && coinBox.getCoinSavingStartDate() != null
                && coinBox.getCoinSavingStartDate().isBefore(executionDate);
    }

    /**
     * 실행일에 저금통 계약이 ACTIVE이며 유효기간 안에 있는지 확인한다.
     */
    private void validateContract(AccountContract contract, LocalDate executionDate) {
        if (contract.getContractStatus() != ContractStatus.ACTIVE
                || executionDate.isBefore(contract.getContractStartDate())
                || !executionDate.isBefore(contract.getContractEndDate())) {
            throw new IllegalStateException("CoinBox contract is not effective");
        }
    }
}
