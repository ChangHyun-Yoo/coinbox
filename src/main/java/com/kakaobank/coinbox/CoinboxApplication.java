package com.kakaobank.coinbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CoinboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoinboxApplication.class, args);
	}

}
