package com.smart.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor         
@NoArgsConstructor                   
public class User {
 
 @Id
 @GeneratedValue(strategy = GenerationType.AUTO)  // Auto-increment ID
 private int userId;
 
 @NotBlank(message = "Name is mandatory and should not be blank")
 @Size(min = 3, max = 20, message = "Name must be between 3 and 20 characters")
 @Column(nullable = false)
 private String name;
 
 @NotBlank(message = "Email is required and cannot be empty") // Ensures it's not blank
 @Email(message = "Please enter a valid email address") // Checks valid format
 @Size(max = 255, message = "Email must not exceed 255 characters") // Database-safe limit
 @Column(unique = true, nullable = false, length = 255) // DB constraints
 private String email;
 
 @NotBlank(message = "Password cannot be blank")
 @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters!")
 @Pattern(
     regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
     message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character!"
 )
 @Column(nullable = false)
 private String password;   
 
 @NotBlank(message = "Role is mandatory and should not be blank")
 @Column(nullable = false)
 private String role;
    
 private String imageUrl;
 
 @NotBlank(message = "About section is mandatory")
 @Size(min = 10, max = 500, message = "About section must be between 10 and 500 characters")
 @Column(length = 500, nullable = false)   
 private String about;
    
 @OneToMany(cascade = CascadeType.ALL,fetch = FetchType.LAZY,mappedBy = "user")
 private List<Contact> contacts = new ArrayList<Contact>(); 
 
}

