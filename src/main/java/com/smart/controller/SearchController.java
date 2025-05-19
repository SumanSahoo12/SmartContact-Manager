package com.smart.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.smart.entities.Contact;
import com.smart.entities.User;
import com.smart.repository.ContactRepo;
import com.smart.repository.UserRepo;

@RestController
public class SearchController {

	@Autowired
	private ContactRepo contactRepo;

	@Autowired
	private UserRepo userRepo;
	
	// search handler
	@GetMapping("/search/{query}")
	public ResponseEntity<?> serach(@PathVariable("query") String qry, Principal principal) {

		String userName = principal.getName(); // userName = email here 
		User user = userRepo.findUserByEmail(userName);
		
		List<Contact> contacts = contactRepo.findByNameContainingAndUser(qry, user);

		return ResponseEntity.ok(contacts);
	}


}
