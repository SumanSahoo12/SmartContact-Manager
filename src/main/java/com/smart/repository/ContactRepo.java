package com.smart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smart.entities.Contact;
import com.smart.entities.User;

public interface ContactRepo extends JpaRepository<Contact, Integer>{
	
	@Query("SELECT c FROM Contact c WHERE c.user.id = :userId")
	// currentPage - page
	// Contact per page - 5
	public Page<Contact> findContactsByUser(@Param("userId") int userId,Pageable pageable);
	
	@Query("SELECT COUNT(c) FROM Contact c WHERE c.user = :user")
	long countByUser(@Param("user") User user);

	// Find name by email (if needed)
    @Query("SELECT c.name FROM Contact c WHERE c.email = :email")    
    Optional<String> findNameByEmail(@Param("email") String email);

    // Find contacts by user (fixed method name)
    @Query("SELECT c FROM Contact c WHERE c.user.id = :userId")
    Page<Contact> findByUser(@Param("userId") int userId, Pageable pageable);
    
    // Count by image (field name fixed)
    @Query("SELECT COUNT(c) FROM Contact c WHERE c.image = :imageName")
    long countByImage(@Param("imageName") String imageName);
    
    //search    
    public List<Contact> findByNameContainingAndUser(String name,User user);


}
