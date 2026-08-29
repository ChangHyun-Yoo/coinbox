package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.repository.AccountQueryRepository;
import com.kakaobank.coinbox.account.repository.ActiveAccountRow;
import com.kakaobank.coinbox.account.response.ActiveAccountResponse;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("고객 ACTIVE 계좌 조회 Service")
class AccountQueryServiceTest {

    private CustomerRepository customerRepository;
    private AccountQueryRepository accountQueryRepository;
    private AccountQueryService accountQueryService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        accountQueryRepository = mock(AccountQueryRepository.class);
        accountQueryService = new AccountQueryService(customerRepository, accountQueryRepository);
    }

    @Test
    @DisplayName("평면 계좌 결과를 최상위 계좌와 childAccount 구조로 조립한다")
    void assemblesActiveAccountHierarchy() {
        // given: 하나의 입출금계좌와 연결 저금통, 별도 모임통장이 있다.
        Long customerId = 1L;
        when(customerRepository.existsById(customerId)).thenReturn(true);
        when(accountQueryRepository.findActiveAccounts(customerId)).thenReturn(List.of(
                row(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", 100_000L, "입출금통장", null),
                row(11L, ProductType.COINBOX, "3310000000011", 4_360L, "저금통", 10L),
                row(20L, ProductType.MEETING_ACCOUNT, "7979000000020", 50_000L, "모임통장", null)
        ));

        // when: ACTIVE 계좌를 조회한다.
        List<ActiveAccountResponse> result = accountQueryService.findActiveAccounts(customerId);

        // then: 최상위 두 계좌와 첫 계좌의 자식 저금통이 응답된다.
        assertThat(result).hasSize(2);
        assertThat(result.getFirst())
                .returns("10", ActiveAccountResponse::accountId)
                .returns("입출금통장", ActiveAccountResponse::productName);
        assertThat(result.getFirst().childAccount())
                .singleElement()
                .returns("11", child -> child.accountId())
                .returns(ProductType.COINBOX, child -> child.productType());
        assertThat(result.get(1).childAccount()).isEmpty();
    }

    @Test
    @DisplayName("고객은 존재하지만 ACTIVE 계좌가 없으면 빈 배열을 반환한다")
    void returnsEmptyListWhenCustomerHasNoActiveAccount() {
        // given: 고객은 존재하지만 조회 결과가 없다.
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(accountQueryRepository.findActiveAccounts(1L)).thenReturn(List.of());

        // when & then: 오류가 아닌 빈 목록을 반환한다.
        assertThat(accountQueryService.findActiveAccounts(1L)).isEmpty();
    }

    @Test
    @DisplayName("고객이 존재하지 않으면 CUSTOMER_NOT_FOUND를 반환한다")
    void rejectsUnknownCustomer() {
        // given: 고객이 존재하지 않는다.
        when(customerRepository.existsById(1L)).thenReturn(false);

        // when & then: 고객 없음 업무 예외가 발생한다.
        assertBusinessException(() -> accountQueryService.findActiveAccounts(1L), ErrorCode.CUSTOMER_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 부모가 없는 자식 계좌는 정합성 오류로 처리한다")
    void rejectsOrphanActiveChildAccount() {
        // given: 부모가 조회 결과에 없는 ACTIVE 저금통이 있다.
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(accountQueryRepository.findActiveAccounts(1L)).thenReturn(List.of(
                row(11L, ProductType.COINBOX, "3310000000011", 4_360L, "저금통", 10L)
        ));

        // when & then: 자식을 임의로 최상위로 승격하지 않는다.
        assertThatThrownBy(() -> accountQueryService.findActiveAccounts(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hierarchy");
    }

    private ActiveAccountRow row(
            Long accountId,
            ProductType productType,
            String accountNumber,
            Long balance,
            String productName,
            Long parentAccountId
    ) {
        return new ActiveAccountRow(
                accountId,
                productType,
                accountNumber,
                balance,
                productName,
                parentAccountId
        );
    }

    private void assertBusinessException(Runnable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
