package com.kakaobank.coinbox.customer.repository;

import com.kakaobank.coinbox.customer.entity.Customer;
import com.kakaobank.coinbox.customer.entity.CustomerStatus;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@DisplayName("고객 Repository")
class CustomerRepositoryTest extends MySqlTestContainer {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("고객을 저장하고 ID로 조회한다")
    void savesAndFindsCustomerById() {
        // given: 저장할 정상 고객을 준비한다.
        Long customerId = 1L;
        Customer customer = Customer.create(customerId);
        customerRepository.saveAndFlush(customer);
        entityManager.clear();

        // when: 고객 ID로 고객을 조회한다.
        Optional<Customer> foundCustomer = customerRepository.findById(customerId);

        // then: 저장한 고객이 정상 상태로 조회된다.
        assertThat(foundCustomer)
                .isPresent()
                .get()
                .extracting(Customer::getCustomerStatus)
                .isEqualTo(CustomerStatus.ACTIVE);
    }
}
