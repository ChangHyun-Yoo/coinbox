package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.response.TestBalanceDepositResponse;
import com.kakaobank.coinbox.account.service.TestBalanceDepositService;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestBalanceDepositController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("테스트 잔고 충전 Controller")
class TestBalanceDepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestBalanceDepositService testBalanceDepositService;

    @Test
    @DisplayName("입출금계좌의 잔고를 수동으로 증가시킨다")
    void depositsBalanceForTesting() throws Exception {
        // given: 단순 잔고 증가가 정상 처리됩니다.
        when(testBalanceDepositService.deposit("3333000000003", 10_000L))
                .thenReturn(new TestBalanceDepositResponse("3333000000003", 10_000L, 135_670L));

        // when & then: 대상 계좌, 증가 금액과 변경 후 잔고를 반환합니다.
        mockMvc.perform(post("/internal/v1/test-account-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3333000000003",
                                  "amount": 10000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("3333000000003"))
                .andExpect(jsonPath("$.depositedAmount").value(10_000))
                .andExpect(jsonPath("$.balanceAfter").value(135_670));
        verify(testBalanceDepositService).deposit("3333000000003", 10_000L);
    }

    @Test
    @DisplayName("입출금계좌가 아니면 전용 오류를 반환한다")
    void rejectsNonDemandDepositAccount() throws Exception {
        // given: 서비스가 저금통 계좌에 대한 잔고 충전을 거부합니다.
        when(testBalanceDepositService.deposit("3310000000001", 10_000L))
                .thenThrow(new BusinessException(ErrorCode.TEST_BALANCE_DEPOSIT_NOT_ALLOWED));

        // when & then: 테스트 전용 업무 오류를 409로 반환합니다.
        mockMvc.perform(post("/internal/v1/test-account-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3310000000001",
                                  "amount": 10000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_BALANCE_DEPOSIT_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("0원 이하 금액이면 INVALID_REQUEST를 반환한다")
    void rejectsNonPositiveAmount() throws Exception {
        // when & then: 양수가 아닌 금액은 Service 호출 전에 400으로 거부합니다.
        mockMvc.perform(post("/internal/v1/test-account-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3333000000003",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
