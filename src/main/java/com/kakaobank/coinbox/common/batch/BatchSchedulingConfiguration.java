package com.kakaobank.coinbox.common.batch;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 운영 스케줄러를 활성화하며 테스트에서는 스케줄 메서드를 직접 호출할 수 있게 분리한다.
 */
@Configuration
@EnableScheduling
public class BatchSchedulingConfiguration {
}
