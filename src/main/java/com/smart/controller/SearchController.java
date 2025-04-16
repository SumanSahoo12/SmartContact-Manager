package com.smart.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.entities.Contact;
import com.smart.entities.User;
import com.smart.repository.ContactRepo;

import jakarta.servlet.http.HttpSession;

@RestController
public class SearchController {

	@Autowired
	private ContactRepo contactRepo;

	// search handler
	@GetMapping("/search/{query}")
	public ResponseEntity<?> serach(@PathVariable("query") String query, HttpSession session,
			RedirectAttributes redirectAttributes) {

		// Retrieve logged-in user from session
		User loggedInUser = (User) session.getAttribute("loggedInUser");

		if (loggedInUser == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required! Please sign in to proceed.");
		}

		List<Contact> contacts = contactRepo.findByNameContainingAndUser(query, loggedInUser);

		return ResponseEntity.ok(contacts);
	}


}
