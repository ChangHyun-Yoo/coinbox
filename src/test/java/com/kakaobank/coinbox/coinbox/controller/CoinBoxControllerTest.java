package com.kakaobank.coinbox.coinbox.controller;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.service.CoinBoxOpenResult;
import com.kakaobank.coinbox.coinbox.service.CoinBoxService;
import com.kakaobank.coinbox.coinbox.service.CoinBoxTerminationResult;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.exception.GlobalExceptionHandler;
import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoinBoxController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("저금통 Controller")
class CoinBoxControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoinBoxService coinBoxService;

    @Test
    @DisplayName("가입 가능한 입출금계좌를 accounts 배열로 반환한다")
    void returnsEligibleAccounts() throws Exception {
        // given: 가입 가능한 입출금계좌가 있다.
        when(coinBoxService.findEligibleAccounts(1L)).thenReturn(List.of(parentAccount()));

        // when & then: 문서의 accounts 응답 형식을 반환한다.
        mockMvc.perform(get("/api/v1/coinboxes/eligible-accounts").header("X-Customer-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].accountId").value("10"))
                .andExpect(jsonPath("$.accounts[0].productType").value("DEMAND_DEPOSIT"));
    }

    @Test
    @DisplayName("저금통 개설 성공 시 201과 개설 정보를 반환한다")
    void opensCoinBox() throws Exception {
        // given: 저금통 계좌와 동전모으기 설정이 생성된다.
        Account account = coinBoxAccount(0L);
        CoinBox coinBox = CoinBox.create(30L, 11L, TODAY);
        when(coinBoxService.openCoinBox(1L, 10L)).thenReturn(new CoinBoxOpenResult(account, coinBox));

        // when & then: 201과 ACTIVE 저금통 정보를 반환한다.
        mockMvc.perform(post("/api/v1/coinboxes")
                        .header("X-Customer-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentAccountId\":\"10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("11"))
                .andExpect(jsonPath("$.accountNumber").value("3310000000011"))
                .andExpect(jsonPath("$.coinSavingEnabled").value(true));
    }

    @Test
    @DisplayName("개설 요청에 parentAccountId가 없으면 INVALID_REQUEST를 반환한다")
    void rejectsInvalidOpenRequest() throws Exception {
        // when & then: Bean Validation 오류를 공통 응답으로 반환한다.
        mockMvc.perform(post("/api/v1/coinboxes")
                        .header("X-Customer-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("저금통 비우기 결과를 반환한다")
    void emptiesCoinBox() throws Exception {
        // given: 전액 4,360원이 연결 계좌로 이체된다.
        when(coinBoxService.emptyCoinBox(1L, "3310000000011"))
                .thenReturn(new TransferResult(50L, 4_360L, 0L, 104_360L));

        // when & then: 거래 ID와 두 계좌의 변경 후 잔액을 반환한다.
        mockMvc.perform(post("/api/v1/coinboxes/3310000000011/empty")
                        .header("X-Customer-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("50"))
                .andExpect(jsonPath("$.amount").value(4360))
                .andExpect(jsonPath("$.coinBoxBalanceAfter").value(0));
    }

    @Test
    @DisplayName("13자리가 아닌 계좌번호는 INVALID_REQUEST를 반환한다")
    void rejectsInvalidAccountNumber() throws Exception {
        // when & then: Service 진입 전에 경로 계좌번호 형식을 검증한다.
        mockMvc.perform(post("/api/v1/coinboxes/3310/empty").header("X-Customer-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("저금통 해지 결과를 반환한다")
    void terminatesCoinBox() throws Exception {
        // given: 잔액 이전 후 계좌와 계약이 종료된다.
        Account account = coinBoxAccount(0L);
        account.close();
        AccountContract contract = AccountContract.create(40L, 11L, 100L, TODAY);
        contract.terminate(TODAY);
        when(coinBoxService.terminateCoinBox(1L, "3310000000011"))
                .thenReturn(new CoinBoxTerminationResult(account, contract, 4_360L, TODAY));

        // when & then: CLOSED·TERMINATED 상태와 이전 금액을 반환한다.
        mockMvc.perform(delete("/api/v1/coinboxes/3310000000011")
                        .header("X-Customer-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
                .andExpect(jsonPath("$.contractStatus").value("TERMINATED"))
                .andExpect(jsonPath("$.transferredAmount").value(4360));
    }

    @Test
    @DisplayName("업무 예외는 API 오류 코드로 반환한다")
    void returnsBusinessError() throws Exception {
        // given: 저금통 잔액이 없다.
        when(coinBoxService.emptyCoinBox(1L, "3310000000011"))
                .thenThrow(new BusinessException(ErrorCode.COINBOX_BALANCE_EMPTY));

        // when & then: ErrorCode의 상태와 메시지를 반환한다.
        mockMvc.perform(post("/api/v1/coinboxes/3310000000011/empty")
                        .header("X-Customer-Id", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COINBOX_BALANCE_EMPTY"));
    }

    private Account parentAccount() {
        return Account.create(
                10L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000010", null, 100_000L, TODAY
        );
    }

    private Account coinBoxAccount(Long balance) {
        return Account.create(
                11L, 1L, ProductType.COINBOX, "3310000000011", 10L, balance, TODAY
        );
    }
}
