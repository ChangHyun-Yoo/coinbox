package com.kakaobank.coinbox.account.controller;

import com.kakaobank.coinbox.account.response.ActiveAccountResponse;
import com.kakaobank.coinbox.account.response.ChildAccountResponse;
import com.kakaobank.coinbox.account.service.AccountQueryService;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.exception.GlobalExceptionHandler;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("고객 ACTIVE 계좌 조회 Controller")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @Test
    @DisplayName("ACTIVE 계좌를 childAccount 구조로 반환한다")
    void returnsActiveAccountHierarchy() throws Exception {
        // given: 입출금계좌와 자식 저금통 조회 결과가 있다.
        when(accountQueryService.findActiveAccounts(1L)).thenReturn(List.of(
                new ActiveAccountResponse(
                        "10",
                        ProductType.DEMAND_DEPOSIT,
                        "3333000000010",
                        100_000L,
                        "입출금통장",
                        List.of(new ChildAccountResponse(
                                "11",
                                ProductType.COINBOX,
                                "3310000000011",
                                4_360L,
                                "저금통"
                        ))
                )
        ));

        // when & then: 배열과 중첩 자식 계좌 형식으로 응답한다.
        mockMvc.perform(get("/api/v1/accounts").header("X-Customer-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value("10"))
                .andExpect(jsonPath("$[0].productType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$[0].childAccount[0].accountId").value("11"))
                .andExpect(jsonPath("$[0].childAccount[0].productName").value("저금통"));
    }

    @Test
    @DisplayName("인증 고객 헤더가 없으면 INVALID_REQUEST를 반환한다")
    void rejectsMissingCustomerHeader() throws Exception {
        // when & then: 필수 헤더가 없으면 공통 400 응답으로 변환한다.
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("존재하지 않는 고객은 CUSTOMER_NOT_FOUND를 반환한다")
    void returnsCustomerNotFound() throws Exception {
        // given: Service가 고객 없음 업무 예외를 반환한다.
        when(accountQueryService.findActiveAccounts(1L))
                .thenThrow(new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        // when & then: ErrorCode의 HTTP 상태와 code를 응답한다.
        mockMvc.perform(get("/api/v1/accounts").header("X-Customer-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }
}
