package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.response.TestBalanceWithdrawalResponse;
import com.kakaobank.coinbox.account.service.TestBalanceWithdrawalService;
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

@WebMvcTest(TestBalanceWithdrawalController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("테스트 잔고 출금 Controller")
class TestBalanceWithdrawalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestBalanceWithdrawalService testBalanceWithdrawalService;

    @Test
    @DisplayName("입출금계좌의 잔고를 수동으로 감소시킨다")
    void withdrawsBalanceForTesting() throws Exception {
        // given: 단순 잔고 감소가 정상 처리됩니다.
        when(testBalanceWithdrawalService.withdraw("3333000000004", 5_000L))
                .thenReturn(new TestBalanceWithdrawalResponse("3333000000004", 5_000L, 181_420L));

        // when & then: 대상 계좌, 감소 금액과 변경 후 잔고를 반환합니다.
        mockMvc.perform(post("/internal/v1/test-account-withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3333000000004",
                                  "amount": 5000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("3333000000004"))
                .andExpect(jsonPath("$.withdrawnAmount").value(5_000))
                .andExpect(jsonPath("$.balanceAfter").value(181_420));
        verify(testBalanceWithdrawalService).withdraw("3333000000004", 5_000L);
    }

    @Test
    @DisplayName("입출금계좌가 아니면 전용 오류를 반환한다")
    void rejectsNonDemandDepositAccount() throws Exception {
        // given: 서비스가 저금통 계좌에 대한 잔고 출금을 거부합니다.
        when(testBalanceWithdrawalService.withdraw("3310000000001", 5_000L))
                .thenThrow(new BusinessException(ErrorCode.TEST_BALANCE_WITHDRAWAL_NOT_ALLOWED));

        // when & then: 테스트 전용 업무 오류를 409로 반환합니다.
        mockMvc.perform(post("/internal/v1/test-account-withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3310000000001",
                                  "amount": 5000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_BALANCE_WITHDRAWAL_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("0원 이하 금액이면 INVALID_REQUEST를 반환한다")
    void rejectsNonPositiveAmount() throws Exception {
        // when & then: 양수가 아닌 금액은 Service 호출 전에 400으로 거부합니다.
        mockMvc.perform(post("/internal/v1/test-account-withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "3333000000004",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
