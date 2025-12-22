package com.shah.ecommerce.customer;

import org.springframework.stereotype.Service;

@Service
public class CustomerMapper {

    public Customer toCustomer(CustomerRequest request) {
        if (request == null){
            return null;
        }
        return Customer.builder().firstName(request.getFirstName())
                .id(request.getId())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .address(request.getAddress())
                .build();
    }

    public CustomerResponse fromCustomer(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getAddress()
        );
    }
}

