package com.kakaobank.coinbox.common.batch.controller;

import com.kakaobank.coinbox.common.batch.service.BatchExecutionResult;
import com.kakaobank.coinbox.common.batch.service.BatchExecutionService;
import com.kakaobank.coinbox.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManualBatchController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("배치 수동 실행 Controller")
class ManualBatchControllerTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 8, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BatchExecutionService batchExecutionService;

    @Test
    @DisplayName("지정 실행일로 일별 최종 잔액 배치를 수동 실행한다")
    void executesDailyBalanceBatch() throws Exception {
        // given: 일별 최종 잔액 Job이 정상 완료됩니다.
        when(batchExecutionService.executeDailyBalance(EXECUTION_DATE)).thenReturn(
                new BatchExecutionResult("101", "dailyBalanceJob", BatchStatus.COMPLETED, EXECUTION_DATE)
        );

        // when & then: 실행 식별자, Job 이름, 상태와 업무 실행일을 반환합니다.
        mockMvc.perform(post("/internal/v1/batches/daily-balance")
                        .queryParam("executionDate", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value("101"))
                .andExpect(jsonPath("$.jobName").value("dailyBalanceJob"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionDate").value("2026-08-30"));
        verify(batchExecutionService).executeDailyBalance(EXECUTION_DATE);
    }

    @Test
    @DisplayName("지정 실행일로 동전모으기 배치를 수동 실행한다")
    void executesCoinSavingBatch() throws Exception {
        // given: 동전모으기 Job이 정상 완료됩니다.
        when(batchExecutionService.executeCoinSaving(EXECUTION_DATE)).thenReturn(
                new BatchExecutionResult("102", "coinSavingJob", BatchStatus.COMPLETED, EXECUTION_DATE)
        );

        // when & then: 동전모으기 Job 실행 결과를 반환합니다.
        mockMvc.perform(post("/internal/v1/batches/coin-saving")
                        .queryParam("executionDate", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value("102"))
                .andExpect(jsonPath("$.jobName").value("coinSavingJob"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionDate").value("2026-08-30"));
        verify(batchExecutionService).executeCoinSaving(EXECUTION_DATE);
    }

    @Test
    @DisplayName("실행일이 없으면 INVALID_REQUEST를 반환한다")
    void rejectsMissingExecutionDate() throws Exception {
        // when & then: 필수 executionDate가 없으면 공통 400 응답으로 변환합니다.
        mockMvc.perform(post("/internal/v1/batches/daily-balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("실행일 형식이 올바르지 않으면 INVALID_REQUEST를 반환한다")
    void rejectsInvalidExecutionDate() throws Exception {
        // when & then: ISO 날짜로 변환할 수 없는 값은 공통 400 응답으로 변환합니다.
        mockMvc.perform(post("/internal/v1/batches/coin-saving")
                        .queryParam("executionDate", "2026/08/30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
