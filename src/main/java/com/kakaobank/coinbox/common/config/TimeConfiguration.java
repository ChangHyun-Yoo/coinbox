package com.kakaobank.coinbox.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 업무 날짜 계산을 테스트에서 고정할 수 있도록 시스템 시계를 의존성으로 제공한다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
