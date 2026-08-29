package com.kakaobank.coinbox.coinbox.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.service.AccountNumberGenerator;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyQueryRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicySnapshot;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import com.kakaobank.coinbox.customer.entity.Customer;
import com.kakaobank.coinbox.customer.entity.CustomerStatus;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import com.kakaobank.coinbox.financialtransaction.service.InternalTransferService;
import com.kakaobank.coinbox.financialtransaction.service.TransferLedgerSpec;
import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

@DisplayName("저금통 Service")
class CoinBoxServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    private CustomerRepository customerRepository;
    private AccountRepository accountRepository;
    private AccountContractRepository accountContractRepository;
    private CoinBoxRepository coinBoxRepository;
    private CoinBoxPolicyQueryRepository coinBoxPolicyQueryRepository;
    private AccountNumberGenerator accountNumberGenerator;
    private InternalTransferService internalTransferService;
    private CoinBoxService coinBoxService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        accountRepository = mock(AccountRepository.class);
        accountContractRepository = mock(AccountContractRepository.class);
        coinBoxRepository = mock(CoinBoxRepository.class);
        coinBoxPolicyQueryRepository = mock(CoinBoxPolicyQueryRepository.class);
        accountNumberGenerator = mock(AccountNumberGenerator.class);
        internalTransferService = mock(InternalTransferService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        coinBoxService = new CoinBoxService(
                customerRepository,
                accountRepository,
                accountContractRepository,
                coinBoxRepository,
                coinBoxPolicyQueryRepository,
                accountNumberGenerator,
                internalTransferService,
                new Snowflake(1L),
                clock
        );
    }

    @Test
    @DisplayName("이용 중인 저금통이 없으면 가입 가능한 ACTIVE 입출금계좌를 반환한다")
    void findsEligibleAccounts() {
        // given: 정상 고객에게 하나의 ACTIVE 입출금계좌가 있다.
        Customer customer = Customer.create(1L);
        Account parent = parentAccount(10L, 1L, 100_000L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(accountRepository.countNonClosedCoinBoxes(1L)).thenReturn(0L);
        when(accountRepository.findEligibleDemandDepositAccounts(1L)).thenReturn(List.of(parent));

        // when & then: 가입 가능한 계좌를 반환한다.
        assertThat(coinBoxService.findEligibleAccounts(1L)).containsExactly(parent);
    }

    @Test
    @DisplayName("이미 이용 중인 저금통이 있으면 가입 가능 계좌를 조회하지 않는다")
    void rejectsEligibleAccountQueryWhenCoinBoxAlreadyExists() {
        // given: 고객에게 CLOSED가 아닌 저금통이 있다.
        when(customerRepository.findById(1L)).thenReturn(Optional.of(Customer.create(1L)));
        when(accountRepository.countNonClosedCoinBoxes(1L)).thenReturn(1L);

        // when & then: 중복 가입 오류를 반환한다.
        assertBusinessException(() -> coinBoxService.findEligibleAccounts(1L), ErrorCode.COINBOX_ALREADY_EXISTS);
        verify(accountRepository, never()).findEligibleDemandDepositAccounts(1L);
    }

    @Test
    @DisplayName("가입 가능 조회에서 고객 식별·상태와 빈 계좌 목록을 각각 검증한다")
    void rejectsInvalidEligibleAccountQueries() {
        // given & when & then: 고객 ID가 없거나 존재하지 않으면 고객 없음으로 처리한다.
        assertBusinessException(() -> coinBoxService.findEligibleAccounts(null), ErrorCode.CUSTOMER_NOT_FOUND);
        when(customerRepository.findById(2L)).thenReturn(Optional.empty());
        assertBusinessException(() -> coinBoxService.findEligibleAccounts(2L), ErrorCode.CUSTOMER_NOT_FOUND);

        // and: 고객 상태가 정상이 아니면 가입 조건 오류로 처리한다.
        Customer restrictedCustomer = mock(Customer.class);
        when(restrictedCustomer.getCustomerStatus()).thenReturn(CustomerStatus.RESTRICTED);
        when(customerRepository.findById(3L)).thenReturn(Optional.of(restrictedCustomer));
        assertBusinessException(() -> coinBoxService.findEligibleAccounts(3L), ErrorCode.CUSTOMER_NOT_ELIGIBLE);

        // and: 정상 고객에게 가입 가능한 입출금계좌가 없으면 전용 오류를 반환한다.
        when(customerRepository.findById(4L)).thenReturn(Optional.of(Customer.create(4L)));
        when(accountRepository.countNonClosedCoinBoxes(4L)).thenReturn(0L);
        when(accountRepository.findEligibleDemandDepositAccounts(4L)).thenReturn(List.of());
        assertBusinessException(
                () -> coinBoxService.findEligibleAccounts(4L),
                ErrorCode.ELIGIBLE_ACCOUNT_NOT_FOUND
        );
    }

    @Test
    @DisplayName("잠금 후 가입 조건과 정책을 확인하여 저금통 계좌·계약·설정을 생성한다")
    void opensCoinBox() {
        // given: 잠금 후에도 가입 가능한 정상 고객과 입출금계좌, 유효한 정책이 있다.
        Customer customer = Customer.create(1L);
        Account parent = parentAccount(10L, 1L, 100_000L);
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(customer));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(parent));
        when(accountRepository.countNonClosedCoinBoxes(1L)).thenReturn(0L);
        when(coinBoxPolicyQueryRepository.findEffectivePolicy(TODAY))
                .thenReturn(Optional.of(new CoinBoxPolicySnapshot(100L, 200L, 100_000L)));
        when(accountNumberGenerator.generate(ProductType.COINBOX)).thenReturn("3310000000011");

        // when: 저금통을 개설한다.
        CoinBoxOpenResult result = coinBoxService.openCoinBox(1L, 10L);

        // then: 세 데이터가 동일 계좌 ID와 개설일을 기준으로 생성된다.
        assertThat(result.account())
                .returns(ProductType.COINBOX, Account::getProductType)
                .returns("3310000000011", Account::getAccountNumber)
                .returns(10L, Account::getParentAccountId)
                .returns(TODAY, Account::getAccountOpenDate)
                .returns(AccountStatus.ACTIVE, Account::getAccountStatus);
        assertThat(result.coinBox())
                .returns(result.account().getAccountId(), CoinBox::getAccountId)
                .returns(true, CoinBox::isCoinSavingEnabled)
                .returns(TODAY, CoinBox::getCoinSavingStartDate);
        verify(accountRepository).save(result.account());
        verify(accountContractRepository).save(any(AccountContract.class));
        verify(coinBoxRepository).save(result.coinBox());
    }

    @Test
    @DisplayName("선택 계좌가 인증 고객 소유가 아니면 개설하지 않는다")
    void rejectsOpeningWithAnotherCustomersAccount() {
        // given: 선택한 계좌의 소유 고객이 다르다.
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Customer.create(1L)));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(parentAccount(10L, 2L, 0L)));

        // when & then: 계좌 없음으로 처리해 다른 고객의 계좌 존재를 노출하지 않는다.
        assertBusinessException(() -> coinBoxService.openCoinBox(1L, 10L), ErrorCode.ACCOUNT_NOT_FOUND);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("개설 잠금 후 고객·계좌·적용 정책이 사라진 경우 각각 개설을 중단한다")
    void rejectsOpeningWhenLockedResourcesAreMissing() {
        // given & when & then: 잠금 시점에 고객이 없으면 개설하지 않는다.
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertBusinessException(() -> coinBoxService.openCoinBox(1L, 10L), ErrorCode.CUSTOMER_NOT_FOUND);

        // and: 고객은 있지만 선택 계좌가 없으면 계좌 없음으로 처리한다.
        when(customerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(Customer.create(2L)));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.empty());
        assertBusinessException(() -> coinBoxService.openCoinBox(2L, 20L), ErrorCode.ACCOUNT_NOT_FOUND);

        // and: 고객과 계좌가 정상이어도 개설일에 적용할 정책이 없으면 개설하지 않는다.
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(Customer.create(3L)));
        when(accountRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(parentAccount(30L, 3L, 0L)));
        when(accountRepository.countNonClosedCoinBoxes(3L)).thenReturn(0L);
        when(coinBoxPolicyQueryRepository.findEffectivePolicy(TODAY)).thenReturn(Optional.empty());
        assertBusinessException(() -> coinBoxService.openCoinBox(3L, 30L), ErrorCode.COINBOX_POLICY_NOT_FOUND);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("근거계좌의 상품·상태·계층 조건을 잠금 후 각각 재검증한다")
    void rejectsIneligibleParentAccountConditions() {
        // given: 정상 고객이며 이용 중인 저금통은 없다.
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Customer.create(1L)));
        when(accountRepository.countNonClosedCoinBoxes(1L)).thenReturn(0L);

        // when & then: 입출금통장이 아닌 계좌는 근거계좌로 사용할 수 없다.
        Account meetingAccount = Account.create(
                10L, 1L, ProductType.MEETING_ACCOUNT, "7979000000010", null, 0L, TODAY
        );
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(meetingAccount));
        assertBusinessException(() -> coinBoxService.openCoinBox(1L, 10L), ErrorCode.ACCOUNT_NOT_ELIGIBLE);

        // and: 해지 계좌는 근거계좌로 사용할 수 없다.
        Account closedAccount = parentAccount(10L, 1L, 0L);
        closedAccount.close();
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(closedAccount));
        assertBusinessException(() -> coinBoxService.openCoinBox(1L, 10L), ErrorCode.ACCOUNT_NOT_ELIGIBLE);

        // and: 다른 계좌에 종속된 자식 계좌는 근거계좌로 사용할 수 없다.
        Account childAccount = Account.create(
                10L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000010", 99L, 0L, TODAY
        );
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(childAccount));
        assertBusinessException(() -> coinBoxService.openCoinBox(1L, 10L), ErrorCode.ACCOUNT_NOT_ELIGIBLE);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("저금통 잔액 전액을 연결 입출금계좌로 비운다")
    void emptiesCoinBox() {
        // given: 잔액과 연결 계좌가 있는 정상 저금통이다.
        Account coinBox = coinBoxAccount(11L, 1L, 10L, 4_360L);
        TransferResult transferResult = new TransferResult(100L, 4_360L, 0L, 104_360L);
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(coinBox));
        when(internalTransferService.transferAll(eq(11L), eq(10L), any(TransferLedgerSpec.class)))
                .thenReturn(transferResult);

        // when & then: 공통 당행 이체의 결과를 반환한다.
        assertThat(coinBoxService.emptyCoinBox(1L, "3310000000011")).isEqualTo(transferResult);
    }

    @Test
    @DisplayName("저금통 잔액이 0원이면 비우기를 거부한다")
    void rejectsEmptyCoinBoxWhenBalanceIsZero() {
        // given: 잔액이 없는 정상 저금통이다.
        Account coinBox = coinBoxAccount(11L, 1L, 10L, 0L);
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(coinBox));

        // when & then: 사용자용 비어 있음 오류를 반환한다.
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.COINBOX_BALANCE_EMPTY
        );
        verify(internalTransferService, never()).transferAll(any(), any(), any());
    }

    @Test
    @DisplayName("비우기에서 계좌 상태·연결 관계와 당행 이체 오류를 사용자 오류로 변환한다")
    void rejectsInvalidEmptyCoinBoxStates() {
        // given & when & then: 해지된 저금통은 거래 불가로 처리한다.
        Account closedCoinBox = coinBoxAccount(11L, 1L, 10L, 0L);
        closedCoinBox.close();
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(closedCoinBox));
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );

        // and: 연결 근거계좌가 없는 저금통 데이터는 정합성 오류로 처리한다.
        Account disconnectedCoinBox = coinBoxAccount(11L, 1L, null, 100L);
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(disconnectedCoinBox));
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.COINBOX_INVALID_STATE
        );

        // and: 잠금 후 잔액이 먼저 소진됐으면 비어 있는 저금통 오류로 변환한다.
        Account validCoinBox = coinBoxAccount(11L, 1L, 10L, 100L);
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(validCoinBox));
        when(internalTransferService.transferAll(eq(11L), eq(10L), any(TransferLedgerSpec.class)))
                .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE));
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.COINBOX_BALANCE_EMPTY
        );

        // and: 잔액 부족 이외의 이체 오류는 의미를 바꾸지 않고 전달한다.
        when(internalTransferService.transferAll(eq(11L), eq(10L), any(TransferLedgerSpec.class)))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_TRANSFERABLE));
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );

        // and: 입출금계좌를 저금통 계좌번호로 조회한 비정상 결과는 저금통 없음으로 처리한다.
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(parentAccount(10L, 1L, 100L)));
        assertBusinessException(
                () -> coinBoxService.emptyCoinBox(1L, "3310000000011"),
                ErrorCode.COINBOX_NOT_FOUND
        );
    }

    @Test
    @DisplayName("남은 잔액을 이전하고 저금통 계좌·계약·설정을 함께 종료한다")
    void terminatesCoinBoxWithRemainingBalance() {
        // given: 잔액이 있는 저금통과 연결 계좌, ACTIVE 계약·설정을 잠금 조회한다.
        Account parent = parentAccount(10L, 1L, 100_000L);
        Account coinBoxAccount = coinBoxAccount(11L, 1L, 10L, 4_360L);
        CoinBox coinBox = CoinBox.create(30L, 11L, TODAY);
        AccountContract contract = AccountContract.create(40L, 11L, 100L, TODAY);
        prepareTerminationMocks(parent, coinBoxAccount, coinBox, contract);
        when(internalTransferService.transferAll(eq(11L), eq(10L), any(TransferLedgerSpec.class)))
                .thenAnswer(invocation -> {
                    coinBoxAccount.debit(4_360L);
                    parent.credit(4_360L);
                    return new TransferResult(50L, 4_360L, 0L, 104_360L);
                });

        // when: 저금통을 해지한다.
        CoinBoxTerminationResult result = coinBoxService.terminateCoinBox(1L, "3310000000011");

        // then: 잔액 이전 후 같은 날 계좌·계약·동전모으기가 종료된다.
        assertThat(result.transferredAmount()).isEqualTo(4_360L);
        assertThat(result.account().getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(result.accountContract())
                .returns(ContractStatus.TERMINATED, AccountContract::getContractStatus)
                .returns(TODAY, AccountContract::getContractEndDate);
        assertThat(coinBox.isCoinSavingEnabled()).isFalse();
        assertThat(coinBox.getCoinSavingStartDate()).isNull();
    }

    @Test
    @DisplayName("잔액이 0원인 저금통은 금융거래 없이 해지한다")
    void terminatesEmptyCoinBoxWithoutTransfer() {
        // given: 잔액이 없는 정상 저금통이다.
        Account parent = parentAccount(10L, 1L, 100_000L);
        Account coinBoxAccount = coinBoxAccount(11L, 1L, 10L, 0L);
        CoinBox coinBox = CoinBox.create(30L, 11L, TODAY);
        AccountContract contract = AccountContract.create(40L, 11L, 100L, TODAY);
        prepareTerminationMocks(parent, coinBoxAccount, coinBox, contract);

        // when: 저금통을 해지한다.
        CoinBoxTerminationResult result = coinBoxService.terminateCoinBox(1L, "3310000000011");

        // then: 이체 없이 종료 상태만 반영된다.
        assertThat(result.transferredAmount()).isZero();
        assertThat(result.account().getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        verify(internalTransferService, never()).transferAll(any(), any(), any());
    }

    @Test
    @DisplayName("근거계좌 연결이 없는 저금통은 해지 상태를 변경하지 않는다")
    void rejectsTerminationWithoutParentAccount() {
        // given: 고객 소유 저금통이지만 parentAccountId가 유실된 비정상 데이터다.
        Account disconnectedCoinBox = coinBoxAccount(11L, 1L, null, 0L);
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Customer.create(1L)));
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(disconnectedCoinBox));

        // when & then: 계좌 잠금과 상태 변경 전에 정합성 오류로 종료한다.
        assertBusinessException(
                () -> coinBoxService.terminateCoinBox(1L, "3310000000011"),
                ErrorCode.COINBOX_INVALID_STATE
        );
        verify(accountRepository, never()).findAllByIdForUpdateOrderByAccountId(any());
        verify(internalTransferService, never()).transferAll(any(), any(), any());
    }

    private void prepareTerminationMocks(
            Account parent,
            Account coinBoxAccount,
            CoinBox coinBox,
            AccountContract contract
    ) {
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Customer.create(1L)));
        when(accountRepository.findByCustomerIdAndAccountNumber(1L, "3310000000011"))
                .thenReturn(Optional.of(coinBoxAccount));
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 11L)))
                .thenReturn(List.of(parent, coinBoxAccount));
        when(coinBoxRepository.findByAccountIdForUpdate(11L)).thenReturn(Optional.of(coinBox));
        when(accountContractRepository.findLatestByAccountIdForUpdate(11L)).thenReturn(Optional.of(contract));
    }

    private Account parentAccount(Long accountId, Long customerId, Long balance) {
        return Account.create(
                accountId,
                customerId,
                ProductType.DEMAND_DEPOSIT,
                "3333000000010",
                null,
                balance,
                TODAY
        );
    }

    private Account coinBoxAccount(
            Long accountId,
            Long customerId,
            Long parentAccountId,
            Long balance
    ) {
        return Account.create(
                accountId,
                customerId,
                ProductType.COINBOX,
                "3310000000011",
                parentAccountId,
                balance,
                TODAY
        );
    }

    private void assertBusinessException(Runnable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
