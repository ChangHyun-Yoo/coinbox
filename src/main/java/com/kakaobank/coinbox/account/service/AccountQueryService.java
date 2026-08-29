package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.repository.AccountQueryRepository;
import com.kakaobank.coinbox.account.repository.ActiveAccountRow;
import com.kakaobank.coinbox.account.response.ActiveAccountResponse;
import com.kakaobank.coinbox.account.response.ChildAccountResponse;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고객의 ACTIVE 계좌를 조회하고 parent_account_id에 따라 화면용 계층 구조로 조립한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {

    private final CustomerRepository customerRepository;
    private final AccountQueryRepository accountQueryRepository;

    /**
     * 고객 존재 여부를 확인하고 ACTIVE 계좌 조회 결과를 계층형 응답으로 조립한다.
     */
    public List<ActiveAccountResponse> findActiveAccounts(Long customerId) {
        if (customerId == null || !customerRepository.existsById(customerId)) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }

        List<ActiveAccountRow> rows = accountQueryRepository.findActiveAccounts(customerId);
        validateRows(rows);
        return assembleHierarchy(rows);
    }

    /**
     * 조회 결과에 상품과 부모·자식 관계의 정합성 오류가 없는지 확인한다.
     */
    private void validateRows(List<ActiveAccountRow> rows) {
        Map<Long, ActiveAccountRow> rowsById = new LinkedHashMap<>();
        for (ActiveAccountRow row : rows) {
            if (row.productName() == null || row.productName().isBlank()) {
                throw new IllegalStateException("Product master data is missing for active account");
            }
            rowsById.put(row.accountId(), row);
        }

        for (ActiveAccountRow row : rows) {
            if (row.parentAccountId() == null) {
                continue;
            }
            ActiveAccountRow parent = rowsById.get(row.parentAccountId());
            if (parent == null || parent.parentAccountId() != null) {
                throw new IllegalStateException("Active account hierarchy is invalid");
            }
        }
    }

    /**
     * 평면 조회 결과를 최상위 계좌와 한 단계 자식 목록으로 조립한다.
     */
    private List<ActiveAccountResponse> assembleHierarchy(List<ActiveAccountRow> rows) {
        Map<Long, List<ChildAccountResponse>> childrenByParentId = new LinkedHashMap<>();
        for (ActiveAccountRow row : rows) {
            if (row.parentAccountId() != null) {
                childrenByParentId
                        .computeIfAbsent(row.parentAccountId(), key -> new ArrayList<>())
                        .add(ChildAccountResponse.from(row));
            }
        }

        return rows.stream()
                .filter(row -> row.parentAccountId() == null)
                .map(row -> ActiveAccountResponse.of(
                        row,
                        childrenByParentId.getOrDefault(row.accountId(), List.of())
                ))
                .toList();
    }
}
