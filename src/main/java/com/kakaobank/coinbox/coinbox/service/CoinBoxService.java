package com.kakaobank.coinbox.coinbox.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.service.AccountNumberGenerator;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 저금통의 가입 조건, 연결 계좌 관계, 비우기와 해지 상태 전환을 조정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoinBoxService {

    private static final TransferLedgerSpec EMPTY_LEDGER_SPEC = new TransferLedgerSpec(
            EntryCode.COINBOX_EMPTY,
            EntryCode.COINBOX_EMPTY.getDescription(),
            EntryCode.COINBOX,
            EntryCode.COINBOX.getDescription()
    );
    private static final TransferLedgerSpec TERMINATION_LEDGER_SPEC = new TransferLedgerSpec(
            EntryCode.COINBOX_TERMINATION,
            EntryCode.COINBOX_TERMINATION.getDescription(),
            EntryCode.COINBOX,
            EntryCode.COINBOX.getDescription()
    );

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AccountContractRepository accountContractRepository;
    private final CoinBoxRepository coinBoxRepository;
    private final CoinBoxPolicyQueryRepository coinBoxPolicyQueryRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final InternalTransferService internalTransferService;
    private final Snowflake snowflake;
    private final Clock clock;

    /**
     * 신규 가입 가능 여부를 확인하고 선택 가능한 입출금계좌를 반환한다.
     */
    public List<Account> findEligibleAccounts(Long customerId) {
        Customer customer = findCustomer(customerId);
        validateEligibleCustomer(customer);
        validateNoActiveCoinBox(customerId);

        List<Account> accounts = accountRepository.findEligibleDemandDepositAccounts(customerId);
        if (accounts.isEmpty()) {
            throw new BusinessException(ErrorCode.ELIGIBLE_ACCOUNT_NOT_FOUND);
        }
        return accounts;
    }

    /**
     * 고객과 선택 계좌를 잠근 뒤 계좌·계약·저금통 설정을 원자적으로 생성한다.
     */
    @Transactional
    public CoinBoxOpenResult openCoinBox(Long customerId, Long selectedAccountId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        validateEligibleCustomer(customer);

        Account selectedAccount = accountRepository.findByIdForUpdate(selectedAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateNoActiveCoinBox(customerId);
        validateEligibleParentAccount(customerId, selectedAccount);

        LocalDate openDate = LocalDate.now(clock);
        CoinBoxPolicySnapshot policy = coinBoxPolicyQueryRepository.findEffectivePolicy(openDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.COINBOX_POLICY_NOT_FOUND));

        Long coinBoxAccountId = snowflake.nextId();
        Account coinBoxAccount = Account.create(
                coinBoxAccountId,
                customerId,
                ProductType.COINBOX,
                accountNumberGenerator.generate(ProductType.COINBOX),
                selectedAccountId,
                0L,
                openDate
        );
        AccountContract accountContract = AccountContract.create(
                snowflake.nextId(),
                coinBoxAccountId,
                policy.productVersionId(),
                openDate
        );
        CoinBox coinBox = CoinBox.create(snowflake.nextId(), coinBoxAccountId, openDate);

        accountRepository.save(coinBoxAccount);
        accountContractRepository.save(accountContract);
        coinBoxRepository.save(coinBox);

        log.debug("저금통 개설 데이터를 생성했습니다. customerId={}, accountId={}", customerId, coinBoxAccountId);
        return new CoinBoxOpenResult(coinBoxAccount, coinBox);
    }

    /**
     * 인증 고객의 저금통 잔액 전액을 공통 당행 이체로 근거계좌에 이전한다.
     */
    @Transactional
    public TransferResult emptyCoinBox(Long customerId, String accountNumber) {
        Account coinBoxAccount = findOwnedCoinBox(customerId, accountNumber);
        if (coinBoxAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_TRANSFERABLE);
        }
        if (coinBoxAccount.getBalance() == 0L) {
            throw new BusinessException(ErrorCode.COINBOX_BALANCE_EMPTY);
        }
        if (coinBoxAccount.getParentAccountId() == null) {
            throw new BusinessException(ErrorCode.COINBOX_INVALID_STATE);
        }

        try {
            return internalTransferService.transferAll(
                    coinBoxAccount.getAccountId(),
                    coinBoxAccount.getParentAccountId(),
                    EMPTY_LEDGER_SPEC
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE) {
                throw new BusinessException(ErrorCode.COINBOX_BALANCE_EMPTY);
            }
            throw exception;
        }
    }

    /**
     * 잔액을 정리한 뒤 저금통 계좌·계약·동전모으기 설정을 함께 종료한다.
     */
    @Transactional
    public CoinBoxTerminationResult terminateCoinBox(Long customerId, String accountNumber) {
        customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        Account foundCoinBoxAccount = findOwnedCoinBox(customerId, accountNumber);
        if (foundCoinBoxAccount.getAccountStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.COINBOX_ALREADY_TERMINATED);
        }
        Long parentAccountId = foundCoinBoxAccount.getParentAccountId();
        if (parentAccountId == null) {
            throw new BusinessException(ErrorCode.COINBOX_INVALID_STATE);
        }

        List<Long> sortedIds = foundCoinBoxAccount.getAccountId() < parentAccountId
                ? List.of(foundCoinBoxAccount.getAccountId(), parentAccountId)
                : List.of(parentAccountId, foundCoinBoxAccount.getAccountId());
        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdateOrderByAccountId(sortedIds);
        if (lockedAccounts.size() != 2) {
            throw new BusinessException(ErrorCode.COINBOX_INVALID_STATE);
        }

        Account lockedCoinBoxAccount = findAccount(lockedAccounts, foundCoinBoxAccount.getAccountId());
        Account lockedParentAccount = findAccount(lockedAccounts, parentAccountId);
        CoinBox coinBox = coinBoxRepository.findByAccountIdForUpdate(lockedCoinBoxAccount.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COINBOX_INVALID_STATE));
        AccountContract accountContract = accountContractRepository
                .findLatestByAccountIdForUpdate(lockedCoinBoxAccount.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COINBOX_INVALID_STATE));

        validateTerminationState(customerId, lockedCoinBoxAccount, lockedParentAccount, accountContract);

        Long transferredAmount = 0L;
        if (lockedCoinBoxAccount.getBalance() > 0L) {
            transferredAmount = internalTransferService.transferAll(
                    lockedCoinBoxAccount.getAccountId(),
                    lockedParentAccount.getAccountId(),
                    TERMINATION_LEDGER_SPEC
            ).amount();
        }

        LocalDate terminationDate = LocalDate.now(clock);
        coinBox.disableCoinSaving();
        accountContract.terminate(terminationDate);
        lockedCoinBoxAccount.close();

        log.debug("저금통 해지 상태를 반영했습니다. customerId={}, accountId={}",
                customerId, lockedCoinBoxAccount.getAccountId());
        return new CoinBoxTerminationResult(
                lockedCoinBoxAccount,
                accountContract,
                transferredAmount,
                terminationDate
        );
    }

    /**
     * 고객 ID를 검증하고 조회된 고객을 반환한다.
     */
    private Customer findCustomer(Long customerId) {
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    /**
     * 정상 고객만 저금통 가입 절차를 진행하도록 제한한다.
     */
    private void validateEligibleCustomer(Customer customer) {
        if (customer.getCustomerStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_ELIGIBLE);
        }
    }

    /**
     * 한 고객이 동시에 둘 이상의 이용 중 저금통을 갖지 못하게 한다.
     */
    private void validateNoActiveCoinBox(Long customerId) {
        if (accountRepository.countNonClosedCoinBoxes(customerId) > 0L) {
            throw new BusinessException(ErrorCode.COINBOX_ALREADY_EXISTS);
        }
    }

    /**
     * 선택 계좌의 소유자, 상품, 상태와 최상위 계좌 여부를 확인한다.
     */
    private void validateEligibleParentAccount(Long customerId, Account account) {
        if (!account.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (account.getProductType() != ProductType.DEMAND_DEPOSIT
                || account.getAccountStatus() != AccountStatus.ACTIVE
                || account.getParentAccountId() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ELIGIBLE);
        }
    }

    /**
     * 계좌번호로 인증 고객 소유의 저금통 계좌만 조회한다.
     */
    private Account findOwnedCoinBox(Long customerId, String accountNumber) {
        Account account = accountRepository.findByCustomerIdAndAccountNumber(customerId, accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.COINBOX_NOT_FOUND));
        if (account.getProductType() != ProductType.COINBOX) {
            throw new BusinessException(ErrorCode.COINBOX_NOT_FOUND);
        }
        return account;
    }

    /**
     * 잠긴 두 계좌 중 요청한 ID의 계좌를 찾는다.
     */
    private Account findAccount(List<Account> accounts, Long accountId) {
        return accounts.stream()
                .filter(account -> account.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.COINBOX_INVALID_STATE));
    }

    /**
     * 해지 직전 계좌 관계, 상품 유형, 거래 상태와 계약 효력을 재검증한다.
     */
    private void validateTerminationState(
            Long customerId,
            Account coinBoxAccount,
            Account parentAccount,
            AccountContract accountContract
    ) {
        if (coinBoxAccount.getAccountStatus() == AccountStatus.CLOSED
                || accountContract.getContractStatus() == ContractStatus.TERMINATED) {
            throw new BusinessException(ErrorCode.COINBOX_ALREADY_TERMINATED);
        }
        if (!coinBoxAccount.getCustomerId().equals(customerId)
                || !parentAccount.getCustomerId().equals(customerId)
                || !parentAccount.getAccountId().equals(coinBoxAccount.getParentAccountId())
                || coinBoxAccount.getProductType() != ProductType.COINBOX
                || parentAccount.getProductType() != ProductType.DEMAND_DEPOSIT) {
            throw new BusinessException(ErrorCode.COINBOX_INVALID_STATE);
        }
        if (coinBoxAccount.getAccountStatus() != AccountStatus.ACTIVE
                || parentAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_TRANSFERABLE);
        }
        if (accountContract.getContractStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COINBOX_INVALID_STATE);
        }
    }
}
