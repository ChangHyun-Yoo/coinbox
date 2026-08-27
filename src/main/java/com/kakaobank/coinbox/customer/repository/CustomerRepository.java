package com.kakaobank.coinbox.customer.repository;

import com.kakaobank.coinbox.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
