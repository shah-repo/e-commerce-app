package com.shah.ecommerce.user;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Document(collection = "users")
public class AppUser {
    @Id
    private String id;
    @Indexed(unique = true)
    private String username;
    private String email;
    private String password;
    private Role role;
}
