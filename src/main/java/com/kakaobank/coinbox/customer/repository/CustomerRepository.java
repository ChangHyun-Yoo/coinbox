package com.kakaobank.coinbox.customer.repository;

import com.kakaobank.coinbox.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 고객 저장과 고객 단위 업무 직렬화를 위한 잠금 조회를 담당한다.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * 동일 고객의 저금통 개설·해지 경쟁을 막기 위해 고객 행을 잠근다.
     */
    @Query(value = """
            select c.*
            from customer c
            where c.customer_id = :customerId
            for update
            """, nativeQuery = true)
    Optional<Customer> findByIdForUpdate(@Param("customerId") Long customerId);
}
