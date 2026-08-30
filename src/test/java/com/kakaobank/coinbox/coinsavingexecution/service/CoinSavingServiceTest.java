package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyQueryRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicySnapshot;
import com.kakaobank.coinbox.coinsavingexecution.batch.CoinSavingCandidate;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecutionStatus;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;
import com.kakaobank.coinbox.coinsavingexecution.repository.CoinSavingExecutionRepository;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import com.kakaobank.coinbox.financialtransaction.service.InternalTransferService;
import com.kakaobank.coinbox.financialtransaction.service.TransferLedgerSpec;
import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("동전모으기 Service")
class CoinSavingServiceTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 8, 28);

    private AccountRepository accountRepository;
    private CoinBoxRepository coinBoxRepository;
    private AccountContractRepository accountContractRepository;
    private CoinBoxPolicyQueryRepository policyQueryRepository;
    private CoinSavingExecutionRepository executionRepository;
    private InternalTransferService transferService;
    private Snowflake snowflake;
    private CoinSavingService coinSavingService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        coinBoxRepository = mock(CoinBoxRepository.class);
        accountContractRepository = mock(AccountContractRepository.class);
        policyQueryRepository = mock(CoinBoxPolicyQueryRepository.class);
        CoinSavingPolicyResolver policyResolver = new CoinSavingPolicyResolver(policyQueryRepository);
        executionRepository = mock(CoinSavingExecutionRepository.class);
        transferService = mock(InternalTransferService.class);
        snowflake = mock(Snowflake.class);
        coinSavingService = new CoinSavingService(
                accountRepository,
                coinBoxRepository,
                accountContractRepository,
                policyResolver,
                executionRepository,
                new CoinSavingAmountCalculator(),
                transferService,
                snowflake
        );
    }

    @Test
    @DisplayName("잠금 후 계산한 잔돈을 이체하고 SUCCESS 실행 이력을 저장한다")
    void savesSuccessfulCoinSavingExecution() {
        // given: 전일 잔돈 850원을 저축할 수 있는 정상 저금통이다.
        PreparedContext context = prepareContext(100_850L, 500L);
        when(transferService.transferLocked(
                eq(context.parentAccount()),
                eq(context.coinBoxAccount()),
                eq(850L),
                any(TransferLedgerSpec.class)
        ))
                .thenReturn(new TransferResult(70L, 850L, 100_000L, 1_350L));
        when(snowflake.nextId()).thenReturn(80L);

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 금융거래와 연결된 성공 실행 이력을 저장한다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.SUCCESS);
        assertThat(result.savingAmount()).isEqualTo(850L);
        ArgumentCaptor<CoinSavingExecution> executionCaptor = ArgumentCaptor.forClass(CoinSavingExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue())
                .returns(CoinSavingExecutionStatus.SUCCESS, CoinSavingExecution::getExecutionStatus)
                .returns(70L, CoinSavingExecution::getTransactionId)
                .returns(850L, CoinSavingExecution::getSavingAmount);
    }

    @Test
    @DisplayName("동일 날짜 실행 이력이 있으면 이체와 추가 이력을 만들지 않는다")
    void ignoresDuplicateExecution() {
        // given: 잠금 후 같은 날짜의 실행 이력이 확인된다.
        PreparedContext context = prepareContext(100_850L, 500L);
        when(executionRepository.existsByCoinBoxIdAndExecutionDate(30L, EXECUTION_DATE)).thenReturn(true);

        // when: 같은 후보를 다시 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 중복 결과만 반환하고 금융 처리는 하지 않는다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.DUPLICATE);
        verify(transferService, never()).transferLocked(any(), any(), any(), any());
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("잠금 후 동전모으기가 비활성화됐으면 실행 이력 없이 제외한다")
    void excludesDisabledCoinSaving() {
        // given: 후보 조회 후 동전모으기 설정이 꺼졌다.
        PreparedContext context = prepareContext(100_850L, 500L);
        context.coinBox().disableCoinSaving();

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 삭제한 비활성 사유 코드를 만들지 않고 제외한다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.EXCLUDED);
        verify(executionRepository, never()).save(any());
        verify(transferService, never()).transferLocked(any(), any(), any(), any());
    }

    @Test
    @DisplayName("계좌가 ACTIVE가 아니면 ACCOUNT_NOT_ACTIVE 이력을 저장한다")
    void skipsInactiveAccount() {
        // given: 잔액이 없는 근거계좌가 해지 상태로 변경됐다.
        Account inactiveParent = account(10L, ProductType.DEMAND_DEPOSIT, null, 0L);
        inactiveParent.close();
        PreparedContext context = prepareContext(inactiveParent, account(11L, ProductType.COINBOX, 10L, 500L));
        when(snowflake.nextId()).thenReturn(81L);

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 금융거래 없이 계좌 비정상 사유를 남긴다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(CoinSavingReasonCode.ACCOUNT_NOT_ACTIVE);
        verify(transferService, never()).transferLocked(any(), any(), any(), any());
    }

    @Test
    @DisplayName("저금통 한도에 도달했으면 금융거래 없이 SKIPPED 이력을 저장한다")
    void skipsReachedCoinBoxLimit() {
        // given: 저금통 잔액이 정책 최대 한도와 같다.
        PreparedContext context = prepareContext(100_850L, 100_000L);
        when(snowflake.nextId()).thenReturn(82L);

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 한도 도달 사유로 건너뛴다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(CoinSavingReasonCode.COINBOX_LIMIT_REACHED);
        verify(transferService, never()).transferLocked(any(), any(), any(), any());
    }

    @Test
    @DisplayName("필수 후보 값 누락과 동일 출입금 계좌를 처리 전에 거부한다")
    void rejectsInvalidCandidates() {
        // given: 필수 값이 단계별로 누락됐거나 두 계좌 ID가 같은 후보들이다.
        List<CoinSavingCandidate> invalidCandidates = Arrays.asList(
                null,
                new CoinSavingCandidate(null, 11L, 10L, 100_850L, EXECUTION_DATE),
                new CoinSavingCandidate(30L, null, 10L, 100_850L, EXECUTION_DATE),
                new CoinSavingCandidate(30L, 11L, null, 100_850L, EXECUTION_DATE),
                new CoinSavingCandidate(30L, 11L, 10L, 100_850L, null),
                new CoinSavingCandidate(30L, 11L, 11L, 100_850L, EXECUTION_DATE)
        );

        // when & then: DB 잠금 전에 잘못된 후보를 모두 거부한다.
        assertThat(invalidCandidates).allSatisfy(candidate -> assertThatThrownBy(
                () -> coinSavingService.execute(candidate)
        ).isInstanceOf(IllegalArgumentException.class));
        verify(accountRepository, never()).findAllByIdForUpdateOrderByAccountId(any());
    }

    @Test
    @DisplayName("잠금 후 계좌·저금통·계약·정책이 사라진 경우 각각 후보 처리를 중단한다")
    void rejectsMissingLockedResources() {
        // given: 정상 후보의 잠금 대상 데이터를 준비한다.
        PreparedContext context = prepareContext(100_850L, 500L);

        // when & then: 두 계좌 중 하나가 없으면 처리하지 않는다.
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 11L)))
                .thenReturn(List.of(context.parentAccount()));
        assertIllegalState(() -> coinSavingService.execute(context.candidate()), "accounts");

        // and: 계좌는 있지만 저금통 설정이 없으면 처리하지 않는다.
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 11L)))
                .thenReturn(List.of(context.parentAccount(), context.coinBoxAccount()));
        when(coinBoxRepository.findByAccountIdForUpdate(11L)).thenReturn(Optional.empty());
        assertIllegalState(() -> coinSavingService.execute(context.candidate()), "configuration");

        // and: 저금통 설정은 있지만 계약이 없으면 처리하지 않는다.
        when(coinBoxRepository.findByAccountIdForUpdate(11L)).thenReturn(Optional.of(context.coinBox()));
        when(accountContractRepository.findLatestByAccountIdForUpdate(11L)).thenReturn(Optional.empty());
        assertIllegalState(() -> coinSavingService.execute(context.candidate()), "contract");

        // and: 계약은 있지만 연결된 정책이 없으면 처리하지 않는다.
        when(accountContractRepository.findLatestByAccountIdForUpdate(11L))
                .thenReturn(Optional.of(context.contract()));
        when(policyQueryRepository.findByProductVersionId(50L)).thenReturn(Optional.empty());
        assertIllegalState(() -> coinSavingService.execute(context.candidate()), "policy");
    }

    @Test
    @DisplayName("후보와 잠금 데이터의 저금통·계좌 연결 관계를 항목별로 재검증한다")
    void rejectsInvalidAccountRelationships() {
        // given & when & then: 후보 저금통 ID가 실제 설정과 다르면 거부한다.
        assertInvalidRelationship(
                account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L),
                account(11L, ProductType.COINBOX, 10L, 500L),
                CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1)),
                new CoinSavingCandidate(31L, 11L, 10L, 100_850L, EXECUTION_DATE)
        );

        // and: 저금통 설정의 계좌 ID가 후보 계좌와 다르면 거부한다.
        assertInvalidRelationship(
                account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L),
                account(11L, ProductType.COINBOX, 10L, 500L),
                CoinBox.create(30L, 12L, EXECUTION_DATE.minusDays(1)),
                standardCandidate()
        );

        // and: 저금통 계좌가 가리키는 근거계좌 ID가 후보와 다르면 거부한다.
        assertInvalidRelationship(
                account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L),
                account(11L, ProductType.COINBOX, 12L, 500L),
                CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1)),
                standardCandidate()
        );

        // and: 두 계좌의 고객이 다르면 거부한다.
        assertInvalidRelationship(
                account(10L, 2L, ProductType.DEMAND_DEPOSIT, null, 10_000L),
                account(11L, 1L, ProductType.COINBOX, 10L, 500L),
                CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1)),
                standardCandidate()
        );

        // and: 근거계좌가 입출금통장이 아니면 거부한다.
        assertInvalidRelationship(
                account(10L, ProductType.MEETING_ACCOUNT, null, 10_000L),
                account(11L, ProductType.COINBOX, 10L, 500L),
                CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1)),
                standardCandidate()
        );

        // and: 저금통 계좌의 상품 유형이 COINBOX가 아니면 거부한다.
        assertInvalidRelationship(
                account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L),
                account(11L, ProductType.DEMAND_DEPOSIT, 10L, 500L),
                CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1)),
                standardCandidate()
        );
    }

    @Test
    @DisplayName("동전모으기 시작일이 없거나 실행일보다 이전이 아니면 이력 없이 제외한다")
    void excludesIneffectiveCoinSavingStartDate() {
        // given: 활성 표시는 남아 있지만 시작일이 없는 비정상 설정이다.
        Account parent = account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L);
        Account coinBoxAccount = account(11L, ProductType.COINBOX, 10L, 500L);
        AccountContract contract = AccountContract.create(40L, 11L, 50L, EXECUTION_DATE.minusDays(1));
        CoinBox missingStartDate = mock(CoinBox.class);
        when(missingStartDate.getCoinBoxId()).thenReturn(30L);
        when(missingStartDate.getAccountId()).thenReturn(11L);
        when(missingStartDate.isCoinSavingEnabled()).thenReturn(true);
        when(missingStartDate.getCoinSavingStartDate()).thenReturn(null);
        prepareExecutionContext(parent, coinBoxAccount, missingStartDate, contract, standardCandidate());

        // when & then: 시작일이 없으면 실행 이력을 만들지 않고 제외한다.
        assertThat(coinSavingService.execute(standardCandidate()).status())
                .isEqualTo(CoinSavingResultStatus.EXCLUDED);

        // and: 시작일이 실행 당일이어도 아직 대상이 아니므로 제외한다.
        CoinBox startsToday = CoinBox.create(30L, 11L, EXECUTION_DATE);
        prepareExecutionContext(parent, coinBoxAccount, startsToday, contract, standardCandidate());
        assertThat(coinSavingService.execute(standardCandidate()).status())
                .isEqualTo(CoinSavingResultStatus.EXCLUDED);
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("계약 상태·시작일·종료일이 실행일에 유효하지 않으면 처리하지 않는다")
    void rejectsIneffectiveContracts() {
        // given: 계좌와 저금통 설정은 정상이다.
        Account parent = account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L);
        Account coinBoxAccount = account(11L, ProductType.COINBOX, 10L, 500L);
        CoinBox coinBox = CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1));

        // when & then: 종료 상태 계약은 처리하지 않는다.
        AccountContract terminated = mockContract(
                ContractStatus.TERMINATED,
                EXECUTION_DATE.minusDays(1),
                EXECUTION_DATE.plusDays(1)
        );
        prepareExecutionContext(parent, coinBoxAccount, coinBox, terminated, standardCandidate());
        assertIllegalState(() -> coinSavingService.execute(standardCandidate()), "effective");

        // and: 실행일 이후 시작하는 계약은 처리하지 않는다.
        AccountContract startsLater = mockContract(
                ContractStatus.ACTIVE,
                EXECUTION_DATE.plusDays(1),
                AccountContract.ACTIVE_CONTRACT_END_DATE
        );
        prepareExecutionContext(parent, coinBoxAccount, coinBox, startsLater, standardCandidate());
        assertIllegalState(() -> coinSavingService.execute(standardCandidate()), "effective");

        // and: 실행일에 이미 종료되는 계약은 처리하지 않는다.
        AccountContract alreadyEnded = mockContract(
                ContractStatus.ACTIVE,
                EXECUTION_DATE.minusDays(1),
                EXECUTION_DATE
        );
        prepareExecutionContext(parent, coinBoxAccount, coinBox, alreadyEnded, standardCandidate());
        assertIllegalState(() -> coinSavingService.execute(standardCandidate()), "effective");
    }

    @Test
    @DisplayName("저금통 계좌 ID가 더 작아도 오름차순 잠금 후 중복 여부를 확인한다")
    void locksReverseRoleAccountIdsInAscendingOrder() {
        // given: 저금통 계좌 ID가 근거계좌 ID보다 작은 정상 관계다.
        Account parent = account(11L, ProductType.DEMAND_DEPOSIT, null, 10_000L);
        Account coinBoxAccount = account(10L, ProductType.COINBOX, 11L, 500L);
        CoinBox coinBox = CoinBox.create(30L, 10L, EXECUTION_DATE.minusDays(1));
        AccountContract contract = AccountContract.create(40L, 10L, 50L, EXECUTION_DATE.minusDays(1));
        CoinSavingCandidate candidate = new CoinSavingCandidate(
                30L, 10L, 11L, 100_850L, EXECUTION_DATE
        );
        prepareExecutionContext(parent, coinBoxAccount, coinBox, contract, candidate);
        when(executionRepository.existsByCoinBoxIdAndExecutionDate(30L, EXECUTION_DATE)).thenReturn(true);

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(candidate);

        // then: 역할과 무관하게 [10, 11] 순서로 잠그고 중복 결과를 반환한다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.DUPLICATE);
        verify(accountRepository).findAllByIdForUpdateOrderByAccountId(List.of(10L, 11L));
    }

    @Test
    @DisplayName("저금통 계좌만 비정상이어도 ACCOUNT_NOT_ACTIVE 이력을 저장한다")
    void skipsInactiveCoinBoxAccount() {
        // given: 근거계좌는 정상이지만 잔액이 없는 저금통 계좌가 해지됐다.
        Account parent = account(10L, ProductType.DEMAND_DEPOSIT, null, 10_000L);
        Account inactiveCoinBox = account(11L, ProductType.COINBOX, 10L, 0L);
        inactiveCoinBox.close();
        PreparedContext context = prepareContext(parent, inactiveCoinBox);
        when(snowflake.nextId()).thenReturn(83L);

        // when: 후보를 처리한다.
        CoinSavingResult result = coinSavingService.execute(context.candidate());

        // then: 금융거래 없이 계좌 비정상 사유를 저장한다.
        assertThat(result.status()).isEqualTo(CoinSavingResultStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(CoinSavingReasonCode.ACCOUNT_NOT_ACTIVE);
        verify(transferService, never()).transferLocked(any(), any(), any(), any());
    }

    private PreparedContext prepareContext(Long parentBalance, Long coinBoxBalance) {
        return prepareContext(
                account(10L, ProductType.DEMAND_DEPOSIT, null, parentBalance),
                account(11L, ProductType.COINBOX, 10L, coinBoxBalance)
        );
    }

    private PreparedContext prepareContext(Account parent, Account coinBoxAccount) {
        CoinBox coinBox = CoinBox.create(30L, 11L, EXECUTION_DATE.minusDays(1));
        AccountContract contract = AccountContract.create(40L, 11L, 50L, EXECUTION_DATE.minusDays(1));
        CoinSavingCandidate candidate = standardCandidate();
        prepareExecutionContext(parent, coinBoxAccount, coinBox, contract, candidate);
        return new PreparedContext(candidate, coinBox, parent, coinBoxAccount, contract);
    }

    private Account account(Long accountId, ProductType productType, Long parentAccountId, Long balance) {
        return account(accountId, 1L, productType, parentAccountId, balance);
    }

    private Account account(
            Long accountId,
            Long customerId,
            ProductType productType,
            Long parentAccountId,
            Long balance
    ) {
        String accountNumber = switch (productType) {
            case COINBOX -> "3310000000011";
            case MEETING_ACCOUNT -> "7979000000010";
            default -> accountId.equals(11L) ? "3333000000011" : "3333000000010";
        };
        return Account.create(
                accountId,
                customerId,
                productType,
                accountNumber,
                parentAccountId,
                balance,
                EXECUTION_DATE.minusDays(2)
        );
    }

    private void assertInvalidRelationship(
            Account parent,
            Account coinBoxAccount,
            CoinBox coinBox,
            CoinSavingCandidate candidate
    ) {
        AccountContract contract = AccountContract.create(
                40L, candidate.coinBoxAccountId(), 50L, EXECUTION_DATE.minusDays(1)
        );
        prepareExecutionContext(parent, coinBoxAccount, coinBox, contract, candidate);
        assertIllegalState(() -> coinSavingService.execute(candidate), "relationship");
    }

    private void prepareExecutionContext(
            Account parent,
            Account coinBoxAccount,
            CoinBox coinBox,
            AccountContract contract,
            CoinSavingCandidate candidate
    ) {
        List<Long> sortedIds = candidate.parentAccountId() < candidate.coinBoxAccountId()
                ? List.of(candidate.parentAccountId(), candidate.coinBoxAccountId())
                : List.of(candidate.coinBoxAccountId(), candidate.parentAccountId());
        List<Account> sortedAccounts = parent.getAccountId() < coinBoxAccount.getAccountId()
                ? List.of(parent, coinBoxAccount)
                : List.of(coinBoxAccount, parent);
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(sortedIds)).thenReturn(sortedAccounts);
        when(coinBoxRepository.findByAccountIdForUpdate(candidate.coinBoxAccountId()))
                .thenReturn(Optional.of(coinBox));
        when(accountContractRepository.findLatestByAccountIdForUpdate(candidate.coinBoxAccountId()))
                .thenReturn(Optional.of(contract));
        when(policyQueryRepository.findByProductVersionId(contract.getProductVersionId()))
                .thenReturn(Optional.of(new CoinBoxPolicySnapshot(50L, 60L, 100_000L)));
    }

    private AccountContract mockContract(
            ContractStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        AccountContract contract = mock(AccountContract.class);
        when(contract.getContractStatus()).thenReturn(status);
        when(contract.getContractStartDate()).thenReturn(startDate);
        when(contract.getContractEndDate()).thenReturn(endDate);
        when(contract.getProductVersionId()).thenReturn(50L);
        return contract;
    }

    private CoinSavingCandidate standardCandidate() {
        return new CoinSavingCandidate(30L, 11L, 10L, 100_850L, EXECUTION_DATE);
    }

    private void assertIllegalState(Runnable operation, String messageFragment) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(messageFragment);
    }

    private record PreparedContext(
            CoinSavingCandidate candidate,
            CoinBox coinBox,
            Account parentAccount,
            Account coinBoxAccount,
            AccountContract contract
    ) {
    }
}
