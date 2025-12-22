package com.shah.ecommerce.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CustomerRequest {
    private String id;
    @NotNull(message = "Customer Firstname is required")
    private String firstName;
    @NotNull(message = "Customer lastName is required")
    private String lastName;
    @NotNull(message = "Customer email is required")
    @Email(message = "Please enter a valid email")
    private String email;
    private Address address;
}
