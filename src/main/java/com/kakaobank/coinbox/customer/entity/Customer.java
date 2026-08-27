package com.kakaobank.coinbox.customer.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "CUSTOMER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus customerStatus;

    /**
     * 개인 고객의 기본 상태를 정상으로 생성한다.
     */
    public static Customer create(Long customerId) {
        Customer customer = new Customer();
        customer.customerId = customerId;
        customer.customerStatus = CustomerStatus.ACTIVE;
        return customer;
    }
}
