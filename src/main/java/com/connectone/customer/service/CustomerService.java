package com.connectone.customer.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.exception.ApiException;
import com.connectone.customer.dto.CustomerDtos.ActivityResponse;
import com.connectone.customer.dto.CustomerDtos.CustomerSummary;
import com.connectone.customer.dto.CustomerDtos.ProfileResponse;
import com.connectone.customer.repository.CustomerRepository;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public ProfileResponse getMyProfile(long userId) {
        return customerRepository.findProfileByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found"));
    }

    public Page<CustomerSummary> search(String query, String accountStatus, Pageable pageable) {
        return customerRepository.search(query, accountStatus, pageable);
    }

    public List<ActivityResponse> getMyActivity(long userId, int limit) {
        return customerRepository.findActivity(userId, limit);
    }
}
