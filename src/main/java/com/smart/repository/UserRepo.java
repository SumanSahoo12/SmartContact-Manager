package com.smart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import com.smart.entities.User;

public interface UserRepo extends JpaRepository<User, Integer> {
    
    Optional<User> findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findUserByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.contacts WHERE u.id = :userId")
    Optional<User> findUserWithContacts(@Param("userId") int userId);

}
