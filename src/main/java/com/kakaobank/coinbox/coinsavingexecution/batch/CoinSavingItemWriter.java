package com.kakaobank.coinbox.coinsavingexecution.batch;

import com.kakaobank.coinbox.coinsavingexecution.service.CoinSavingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Reader가 전달한 후보를 업무 서비스에 위임한다. 조회와 계산 규칙은 Writer에 중복하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinSavingItemWriter implements ItemWriter<CoinSavingCandidate> {

    private final CoinSavingService coinSavingService;

    /**
     * 청크로 전달된 각 후보를 서비스의 독립 업무 처리에 순서대로 위임한다.
     */
    @Override
    public void write(Chunk<? extends CoinSavingCandidate> candidates) {
        for (CoinSavingCandidate candidate : candidates) {
            coinSavingService.execute(candidate);
        }
        log.debug("동전모으기 후보 청크 처리를 완료했습니다. size={}", candidates.size());
    }
}
