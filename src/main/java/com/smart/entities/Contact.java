package com.smart.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CONTACT")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int contactId;

    @NotBlank(message = "Name is mandatory and should not be blank")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    @Column(nullable = false)
    private String name;

    @Size(max = 50, message = "Second name must not exceed 50 characters")
    private String secondName;

    @NotBlank(message = "Work field is required")
    @Size(min = 2, max = 100, message = "Work must be between 2 and 100 characters")
    @Column(nullable = false)
    private String work;

    @NotBlank(message = "Email is required and cannot be empty") // Ensures it's not blank
    @Email(message = "Please enter a valid email address") // Checks valid format
    @Size(max = 255, message = "Email must not exceed 255 characters") // Database-safe limit
    @Column(nullable = false, length = 255) // DB constraints
    private String email;

    @NotBlank(message = "Phone number is mandatory")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    @Column(nullable = false)
    private String phone;

    @Column(unique = true, nullable = false)
    private String image;

    @NotBlank(message = "About section is mandatory")
    @Size(max = 500, message = "Description must not exceed 5000 characters")
    @Column(length = 500, nullable = false) 
    private String description;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // Ignore user to prevent recursion
    private User user;
}
