package com.kakaobank.coinbox.coinbox.controller;

import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 초기 데이터와 Swagger 기본 요청 예시가 실제 성공 응답으로 연결되는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
@TestPropertySource(properties = "spring.sql.init.mode=always")
@DisplayName("Swagger 성공 예시 통합 테스트")
class SwaggerExampleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("data.sql 기반 Swagger 예시를 순서대로 실행하면 모두 성공한다")
    void executesAllSwaggerExamplesSuccessfully() throws Exception {
        // given & when & then: 고객 계좌와 가입 가능 계좌 조회 예시는 200을 반환합니다.
        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-Customer-Id", "700000000000000001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/coinboxes/eligible-accounts")
                        .header("X-Customer-Id", "700000000000000002"))
                .andExpect(status().isOk());

        // and: 개설 전용 고객은 다른 조회 예시를 소모하지 않고 REST 생성 성공 상태를 반환합니다.
        mockMvc.perform(post("/api/v1/coinboxes")
                        .header("X-Customer-Id", "700000000000000003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentAccountId\":\"710000000000000005\"}"))
                .andExpect(status().isCreated());

        // and: 비우기와 해지는 서로 다른 저금통을 사용하므로 실행 순서와 무관하게 200을 반환합니다.
        mockMvc.perform(post("/api/v1/coinboxes/{accountNumber}/empty", "3310000000001")
                        .header("X-Customer-Id", "700000000000000001"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/coinboxes/{accountNumber}", "3310000000002")
                        .header("X-Customer-Id", "700000000000000004"))
                .andExpect(status().isOk());

        // and: 두 수동 배치 예시는 지정 실행일로 정상 완료됩니다.
        mockMvc.perform(post("/internal/v1/batches/daily-balance")
                        .queryParam("executionDate", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/internal/v1/batches/coin-saving")
                        .queryParam("executionDate", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // and: 테스트 전용 잔고 충전은 입출금계좌의 balance만 증가시킵니다.
        mockMvc.perform(post("/internal/v1/test-account-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3333000000003",
                                  "amount": 10000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(135_670));
    }
}
