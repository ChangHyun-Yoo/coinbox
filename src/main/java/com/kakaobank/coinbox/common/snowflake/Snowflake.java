package com.kakaobank.coinbox.common.snowflake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public Snowflake(@Value("${coinbox.snowflake.node-id:0}") long nodeId) {
        if (nodeId < 0L || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("Snowflake node ID must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long currentTimeMillis = System.currentTimeMillis();

        if (currentTimeMillis < lastTimeMillis) {
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

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp <= lastTimeMillis) {
            Thread.onSpinWait();
            currentTimestamp = System.currentTimeMillis();
        }
        return currentTimestamp;
    }
}
