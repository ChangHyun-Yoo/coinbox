package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyQueryRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("동전모으기 정책 Resolver")
class CoinSavingPolicyResolverTest {

    private CoinBoxPolicyQueryRepository policyQueryRepository;
    private CoinSavingPolicyResolver policyResolver;

    @BeforeEach
    void setUp() {
        policyQueryRepository = mock(CoinBoxPolicyQueryRepository.class);
        policyResolver = new CoinSavingPolicyResolver(policyQueryRepository);
    }

    @Test
    @DisplayName("같은 Step에서 동일 상품 버전의 정책은 최초 한 번만 조회한다")
    void cachesPolicyByProductVersionWithinStep() {
        // given: 동일 상품 버전에 연결된 불변 정책이 있다.
        CoinBoxPolicySnapshot policy = new CoinBoxPolicySnapshot(50L, 60L, 100_000L);
        when(policyQueryRepository.findByProductVersionId(50L)).thenReturn(Optional.of(policy));

        // when: 같은 상품 버전의 정책을 두 번 요청한다.
        CoinBoxPolicySnapshot first = policyResolver.resolve(50L);
        CoinBoxPolicySnapshot second = policyResolver.resolve(50L);

        // then: DB 조회는 한 번만 수행하고 같은 스냅샷을 재사용한다.
        assertThat(first).isSameAs(policy);
        assertThat(second).isSameAs(policy);
        verify(policyQueryRepository, times(1)).findByProductVersionId(50L);
    }

    @Test
    @DisplayName("서로 다른 상품 버전은 각각 조회해 캐시한다")
    void cachesEachProductVersionSeparately() {
        // given: 서로 다른 두 상품 버전의 정책이 있다.
        CoinBoxPolicySnapshot firstPolicy = new CoinBoxPolicySnapshot(50L, 60L, 100_000L);
        CoinBoxPolicySnapshot secondPolicy = new CoinBoxPolicySnapshot(51L, 61L, 200_000L);
        when(policyQueryRepository.findByProductVersionId(50L)).thenReturn(Optional.of(firstPolicy));
        when(policyQueryRepository.findByProductVersionId(51L)).thenReturn(Optional.of(secondPolicy));

        // when & then: 각 버전에 맞는 정책을 반환한다.
        assertThat(policyResolver.resolve(50L)).isSameAs(firstPolicy);
        assertThat(policyResolver.resolve(51L)).isSameAs(secondPolicy);
        verify(policyQueryRepository).findByProductVersionId(50L);
        verify(policyQueryRepository).findByProductVersionId(51L);
    }

    @Test
    @DisplayName("상품 버전이 없거나 연결 정책이 없으면 처리를 중단한다")
    void rejectsMissingPolicy() {
        // given: 조회할 수 없는 상품 버전이다.
        when(policyQueryRepository.findByProductVersionId(50L)).thenReturn(Optional.empty());

        // when & then: 식별자 누락과 정책 누락을 모두 거부한다.
        assertThatThrownBy(() -> policyResolver.resolve(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy");
        assertThatThrownBy(() -> policyResolver.resolve(50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy");
    }
}
