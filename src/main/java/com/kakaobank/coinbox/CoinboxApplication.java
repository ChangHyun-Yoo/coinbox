package com.kakaobank.coinbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 저금통 온라인 API와 배치 애플리케이션을 시작한다.
 */
@SpringBootApplication
public class CoinboxApplication {

	/**
	 * Spring Boot 실행 컨텍스트를 생성한다.
	 */
	public static void main(String[] args) {
		SpringApplication.run(CoinboxApplication.class, args);
	}

}
