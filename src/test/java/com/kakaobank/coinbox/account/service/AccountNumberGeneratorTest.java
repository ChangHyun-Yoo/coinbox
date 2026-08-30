package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("계좌번호 생성 Service")
class AccountNumberGeneratorTest {

    private AccountRepository accountRepository;
    private AccountNumberGenerator accountNumberGenerator;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        accountNumberGenerator = new AccountNumberGenerator(accountRepository);
    }

    @ParameterizedTest(name = "{0} 계좌번호는 {1}으로 시작하는 13자리다")
    @MethodSource("accountNumberPrefixes")
    @DisplayName("상품별 prefix를 적용해 13자리 계좌번호를 생성한다")
    void generatesThirteenDigitAccountNumber(ProductType productType, String prefix) {
        // given: 생성한 첫 계좌번호 후보는 아직 사용되지 않았다.
        when(accountRepository.countByAccountNumber(anyString())).thenReturn(0L);

        // when: 상품 유형에 맞는 계좌번호를 생성한다.
        String accountNumber = accountNumberGenerator.generate(productType);

        // then: 지정된 prefix와 9자리 숫자 suffix로 구성된다.
        assertThat(accountNumber)
                .hasSize(13)
                .startsWith(prefix)
                .matches("[0-9]{13}");
    }

    @Test
    @DisplayName("이미 사용 중인 계좌번호가 생성되면 새 번호를 채번한다")
    void retriesWhenGeneratedAccountNumberAlreadyExists() {
        // given: 첫 후보는 중복이고 두 번째 후보는 사용 가능하다.
        when(accountRepository.countByAccountNumber(anyString())).thenReturn(1L, 0L);

        // when: 입출금계좌 번호를 생성한다.
        String accountNumber = accountNumberGenerator.generate(ProductType.DEMAND_DEPOSIT);

        // then: 중복 조회를 거쳐 사용 가능한 13자리 번호를 반환한다.
        assertThat(accountNumber).matches("3333[0-9]{9}");
        verify(accountRepository, times(2)).countByAccountNumber(anyString());
    }

    @Test
    @DisplayName("정해진 횟수 동안 사용 가능한 번호가 없으면 업무 예외를 반환한다")
    void failsWhenEveryGeneratedAccountNumberAlreadyExists() {
        // given: 생성되는 모든 후보가 이미 사용 중이다.
        when(accountRepository.countByAccountNumber(anyString())).thenReturn(1L);

        // when & then: 무한 반복하지 않고 계좌번호 생성 실패를 알린다.
        assertThatThrownBy(() -> accountNumberGenerator.generate(ProductType.COINBOX))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NUMBER_GENERATION_FAILED);
        verify(accountRepository, times(AccountNumberGenerator.MAX_GENERATION_ATTEMPTS))
                .countByAccountNumber(anyString());
    }

    private static Stream<Arguments> accountNumberPrefixes() {
        return Stream.of(
                Arguments.of(ProductType.DEMAND_DEPOSIT, "3333"),
                Arguments.of(ProductType.BUSINESS_DEMAND_DEPOSIT, "3333"),
                Arguments.of(ProductType.COINBOX, "3310"),
                Arguments.of(ProductType.MEETING_ACCOUNT, "7979")
        );
    }
}
