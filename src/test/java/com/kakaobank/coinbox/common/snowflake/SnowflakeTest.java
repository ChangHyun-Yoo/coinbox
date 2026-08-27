package com.kakaobank.coinbox.common.snowflake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Snowflake ID 생성기")
class SnowflakeTest {

    @Test
    @DisplayName("ID를 중복 없이 증가하는 순서로 생성한다")
    void generatesUniqueAndIncreasingIds() {
        // given: 하나의 노드에서 사용하는 Snowflake 생성기를 준비한다.
        Snowflake snowflake = new Snowflake(1L);

        // when: 여러 개의 ID를 연속해서 생성한다.
        List<Long> ids = Stream.generate(snowflake::nextId)
                .limit(10_000)
                .toList();

        // then: 모든 ID가 중복 없이 생성 순서대로 증가한다.
        assertThat(ids)
                .doesNotHaveDuplicates()
                .isSorted();
    }

    @Test
    @DisplayName("노드 ID를 정해진 10비트 위치에 기록한다")
    void includesNodeIdInGeneratedId() {
        // given: 식별 가능한 노드 ID로 Snowflake 생성기를 준비한다.
        long nodeId = 37L;
        Snowflake snowflake = new Snowflake(nodeId);

        // when: ID를 생성하고 노드 영역을 추출한다.
        long id = snowflake.nextId();
        long extractedNodeId = (id >> 12) & 1_023L;

        // then: 추출한 노드 ID가 생성기에 설정한 값과 같다.
        assertThat(extractedNodeId).isEqualTo(nodeId);
    }

    @Test
    @DisplayName("여러 스레드에서 동시에 생성해도 ID가 중복되지 않는다")
    void generatesUniqueIdsConcurrently() throws InterruptedException {
        // given: 여러 스레드가 공유할 하나의 Snowflake 생성기를 준비한다.
        int threadCount = 8;
        int idCountPerThread = 2_000;
        Snowflake snowflake = new Snowflake(1L);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<List<Long>>> tasks = Stream.generate(() -> (Callable<List<Long>>) () ->
                        Stream.generate(snowflake::nextId)
                                .limit(idCountPerThread)
                                .toList())
                .limit(threadCount)
                .toList();

        // when: 여러 스레드에서 동시에 ID를 생성한다.
        List<Long> ids;
        try {
            ids = executorService.invokeAll(tasks).stream()
                    .flatMap(future -> {
                        try {
                            return future.get().stream();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
        } finally {
            executorService.shutdownNow();
        }

        // then: 요청한 개수만큼 중복 없는 ID가 생성된다.
        assertThat(ids)
                .hasSize(threadCount * idCountPerThread)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("10비트 범위를 벗어난 노드 ID를 거부한다")
    void rejectsNodeIdOutsideTenBitRange() {
        // given: 10비트로 표현할 수 없는 노드 ID를 준비한다.
        long invalidNodeId = 1_024L;

        // when: 잘못된 노드 ID로 생성기를 만든다.
        Throwable throwable = catchThrowable(() -> new Snowflake(invalidNodeId));

        // then: 유효한 범위를 안내하는 예외가 발생한다.
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 1023");
    }
}
