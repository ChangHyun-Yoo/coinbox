package com.kakaobank.coinbox.common.snowflake;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 시간, 노드와 밀리초 내 순번을 조합해 분산 환경에서도 충돌 가능성이 낮은 Long ID를 생성한다.
 */
@Slf4j
@Component
public final class Snowflake {

    // ID는 시간 41비트, 노드 10비트, 시퀀스 12비트로 구성한다.
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L;
    private static final long START_TIME_MILLIS = 1_704_067_200_000L;

    private final long nodeId;

    private long lastTimeMillis = -1L;
    private long sequence;

    /**
     * 설정한 노드 ID가 10비트 범위인지 검증한다.
     */
    public Snowflake(@Value("${coinbox.snowflake.node-id:0}") long nodeId) {
        if (nodeId < 0L || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("Snowflake node ID must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
    }

    /**
     * 같은 인스턴스의 동시 요청을 직렬화해 유일한 다음 ID를 반환한다.
     */
    public synchronized long nextId() {
        long currentTimeMillis = System.currentTimeMillis();

        if (currentTimeMillis < lastTimeMillis) {
            log.error("시스템 시간이 이전 값보다 작아 Snowflake ID를 생성할 수 없습니다. last={}, current={}",
                    lastTimeMillis, currentTimeMillis);
            throw new IllegalStateException("Invalid Time");
        }

        if (currentTimeMillis == lastTimeMillis) {
            sequence = (sequence + 1L) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currentTimeMillis = waitNextMillis(currentTimeMillis);
            }
        } else {
            sequence = 0L;
        }

        lastTimeMillis = currentTimeMillis;

        return ((currentTimeMillis - START_TIME_MILLIS) << TIMESTAMP_SHIFT)
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    /**
     * 한 밀리초의 시퀀스를 모두 사용하면 다음 밀리초까지 대기한다.
     */
    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp <= lastTimeMillis) {
            Thread.onSpinWait();
            currentTimestamp = System.currentTimeMillis();
        }
        return currentTimestamp;
    }
}
