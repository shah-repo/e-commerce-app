package com.shah.ecommerce.customer;

import com.shah.ecommerce.exception.CustomerNotFoundException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CustomerService {
    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public String createCustomer(CustomerRequest request) {
       Customer customer = repository.save(mapper.toCustomer(request));
        return customer.getId();
    }

    public void updateCustomer(CustomerRequest request) {
        var customer = repository.findById(request.getId()).orElseThrow(()-> new CustomerNotFoundException(
                String.format("Can't update customer:: No customer found with the given id:: %s", request.getId())
        ));
        mergeCustomer(customer,request);
        repository.save(customer);
    }

    private void mergeCustomer(Customer customer, CustomerRequest request) {
        if (StringUtils.isNotBlank(request.getFirstName())){
            customer.setFirstName(request.getFirstName());
        }
        if (StringUtils.isNotBlank(request.getLastName())){
            customer.setLastName(request.getLastName());
        }
        if (StringUtils.isNotBlank(request.getEmail())){
            customer.setEmail(request.getEmail());
        }
        if (request.getAddress() != null){
            customer.setAddress(request.getAddress());
        }
    }

    public List<CustomerResponse> findAllCustomer() {
       return repository.findAll().stream().map(mapper::fromCustomer).collect(Collectors.toList());
    }

    public Boolean existsById(String customerId) {
        return repository.findById(customerId).isPresent();
    }

    public CustomerResponse findById(String customerId) {
        return repository.findById(customerId)
                .map(mapper::fromCustomer)
                .orElseThrow(()-> new CustomerNotFoundException(
                String.format("Can't update customer:: No customer found with the given id:: %s", customerId)
        ));
    }

    public void removeById(String customerId) {
        repository.deleteById(customerId);
    }
}
