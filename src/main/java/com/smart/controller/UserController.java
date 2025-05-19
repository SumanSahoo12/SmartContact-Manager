package com.smart.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.entities.Contact;
import com.smart.entities.User;
import com.smart.repository.ContactRepo;
import com.smart.repository.UserRepo;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/user")
public class UserController {

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private ContactRepo contactRepo;

	// Common data method to add user details to the model
	@ModelAttribute
	public void addCommonData(Model model, Principal principal) {

		String userName = principal.getName(); // userName = email here
		// get the user by userName
		User user = userRepo.findUserByEmail(userName);
		model.addAttribute("user", user);
	}

	// User Dashboard
	@GetMapping("/user_dashboard")
	public String userDashboard(Model model) {

		model.addAttribute("title", "User Dashboard");
		return "user/user_dashboard"; // Return user dashboard view
	}

	// Open Add Contact Form (Only for logged-in users)
	@GetMapping("/addContact")
	public String openAddContactForm(Model model) {
		model.addAttribute("title", "Add Contact");
		model.addAttribute("contact", new Contact());
		return "user/addContactForm";
	}

	@Transactional
	@PostMapping("/processContact")
	public String processContact(@Valid @ModelAttribute("contact") Contact contact, BindingResult result,
			@RequestParam("profileImage") MultipartFile file, Principal principal,
			RedirectAttributes redirectAttributes) {

		User user = userRepo.findUserByEmail(principal.getName());

		if (result.hasErrors()) {
			return "user/addContactForm";
		}

		// Check duplicates per user
		if (contactRepo.existsByEmailAndUser(contact.getEmail(), user)) {
			redirectAttributes.addFlashAttribute("error", "Email already exists in your contacts.");
			return "redirect:/user/addContact";
		}

		if (contactRepo.existsByPhoneAndUser(contact.getPhone(), user)) {
			redirectAttributes.addFlashAttribute("error", "Phone number already exists in your contacts.");
			return "redirect:/user/addContact";
		}

		// Optional: Verify name if email already exists elsewhere
		Optional<String> existingName = contactRepo.findNameByEmail(contact.getEmail());
		if (existingName.isPresent() && !existingName.get().equalsIgnoreCase(contact.getName())) {
			redirectAttributes.addFlashAttribute("error",
					"You are not '" + existingName.get() + "'. Please enter the correct name.");
			return "redirect:/user/addContact";
		}

		// Handle image upload
		if (file.isEmpty()) {
			redirectAttributes.addFlashAttribute("errorImage", "Profile image is required!");
			return "redirect:/user/addContact";
		}

		String uploadDir = "src/main/resources/static/image/";
		String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
		try {
			Files.createDirectories(Paths.get(uploadDir));
			Path path = Paths.get(uploadDir + fileName);
			Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
			contact.setImage(fileName);
		} catch (IOException e) {
			redirectAttributes.addFlashAttribute("errorImage", "Image upload failed.");
			return "redirect:/user/addContact";
		}

		// Save contact
		contact.setUser(user);
		contactRepo.save(contact);
		redirectAttributes.addFlashAttribute("success", "Contact added successfully!");
		return "redirect:/user/addContact";
	}

	// Show contacts handler
	// per page =5[n]
	// current page = 0[page]
	@GetMapping("/showContacts/{page}")
	public String showContacts(@PathVariable("page") Integer pg, Model model, RedirectAttributes redirectAttributes,
			Principal principal) {

		model.addAttribute("title", "Show User Contacts");

		String userName = principal.getName(); // userName = email here
		User user = userRepo.findUserByEmail(userName);

		// currentPage - page
		// Contact per page - 4
		Pageable pageable = PageRequest.of(pg, 4);

		// Fetch user's contacts safely
		Page<Contact> contacts = contactRepo.findContactsByUser(user.getUserId(), pageable);
		if (contacts == null || contacts.isEmpty()) {
			model.addAttribute("message", "No contacts found...!");
		} else {
			model.addAttribute("contacts", contacts);
		}

		model.addAttribute("currentPage", pg);
		model.addAttribute("totalPages", contacts.getTotalPages());

		return "user/show_contacts";
	}

	// showing particular contact details
	@GetMapping("/{contactId}/contact")
	public String showContactDetail(@PathVariable("contactId") Integer contId, Model model, Principal principal) {

		// Find the contact and handle the absence gracefully
		Optional<Contact> contactOptional = contactRepo.findById(contId);

		// First check if contact exists
		if (contactOptional.isPresent()) {
			Contact contact = contactOptional.get();

			String userName = principal.getName(); // userName = email here
			User user = userRepo.findUserByEmail(userName);

			// Check if the contact belongs to the logged-in user
			if (user.getUserId() == contact.getUser().getUserId()) {
				model.addAttribute("contact", contact);
				model.addAttribute("title", contact.getName());
				return "user/contact_details";
			} else {
				model.addAttribute("error", "You don't have permission to view this contact.");
				return "user/contact_details"; // Show error on the same page
			}
		} else {
			// Handle invalid contact ID
			model.addAttribute("error", "No contact found with the provided ID..!");
			return "user/contact_details"; // Show error on the same page
		}

	}

	// delete contact handler
	@GetMapping("/delete/{contactId}")
	public String deleteContact(@PathVariable("contactId") Integer contId, Principal principal,
			RedirectAttributes redirectAttributes) {

		// Find the contact and handle the absence gracefully
		Optional<Contact> contactOptional = contactRepo.findById(contId);

		if (contactOptional.isPresent()) {
			Contact contact = contactOptional.get();

			String userName = principal.getName(); // userName = email here
			User user = userRepo.findUserByEmail(userName);

			// Check if the contact belongs to the logged-in user
			if (user.getUserId() == contact.getUser().getUserId()) {

				String imageName = contact.getImage();

				// Delete the contact first
				contact.setUser(null);
				contactRepo.delete(contact);

				// If the image exists, check how many contacts are using the same image
				if (imageName != null) {
					long count = contactRepo.countByImage(imageName); // COUNT how many contacts are using the same
																		// image

					if (count == 0) { // If no other contact is using this image, delete it
						String uploadDir = "src/main/resources/static/image/";
						Path imagePath = Paths.get(uploadDir + imageName);

						try {
							Files.deleteIfExists(imagePath);
							redirectAttributes.addFlashAttribute("success", "Contact and image deleted successfully.");
						} catch (IOException e) {
							redirectAttributes.addFlashAttribute("error", "Failed to delete contact image.");
						}
					} else {
						redirectAttributes.addFlashAttribute("success",
								"Contact deleted. Image retained as it's used by other contacts.");
					}
				} else {
					redirectAttributes.addFlashAttribute("success", "Contact deleted successfully.");
				}
			} else {
				redirectAttributes.addFlashAttribute("error", "You don't have permission to delete this contact.");
			}
		} else {
			redirectAttributes.addFlashAttribute("error", "No contact found with the provided ID.");
		}

		return "redirect:/user/showContacts/0"; // Redirect to contact list
	}

	// open update form handler
	@GetMapping("/update-contact/{contactId}")
	public String upadteForm(@PathVariable("contactId") Integer contId, Model model,
			RedirectAttributes redirectAttributes, Principal principal) {

		model.addAttribute("title", "Update Contact");

		// Get logged-in user
		String userName = principal.getName();
		User user = userRepo.findUserByEmail(userName);

		// Safely fetch contact
		Optional<Contact> optionalContact = contactRepo.findById(contId);

		if (optionalContact.isPresent()) {
			Contact contact = optionalContact.get();

			// Check ownership of the contact
			if (user.getUserId() == contact.getUser().getUserId()) {
				model.addAttribute("contact", contact);
				return "user/update_form";
			} else {
				redirectAttributes.addFlashAttribute("error", "You don’t have permission to update this contact.");
			}

		} else {
			redirectAttributes.addFlashAttribute("error", "Contact not found.");
		}
		return "user/update_form";
	}

	// update contact handler
	@Transactional
	@PostMapping("/processUpdate/{contactId}")
	public String updateContact(@PathVariable("contactId") int contId, @ModelAttribute Contact contact,
			@RequestParam("profileImage") MultipartFile file, RedirectAttributes redirectAttributes,
			Principal principal) {

		try {

			// Find existing contact
			Optional<Contact> oldContactDetails = contactRepo.findById(contId);
			if (oldContactDetails.isEmpty()) {
				redirectAttributes.addFlashAttribute("error", "Contact not found!");
				return "redirect:/user/showContacts/0";
			}

			Contact existingContact = oldContactDetails.get();

			String userName = principal.getName(); // userName = email here
			User user = userRepo.findUserByEmail(userName);

			// Check if the user is authorized to update the contact
			if (existingContact.getUser().getUserId() != user.getUserId()) {
				redirectAttributes.addFlashAttribute("error", "You are not authorized to update this contact!");
				return "redirect:/user/showContacts/0";
			}

			// Update basic details
			existingContact.setName(contact.getName());
			existingContact.setSecondName(contact.getSecondName());
			existingContact.setPhone(contact.getPhone());
			existingContact.setEmail(contact.getEmail());
			existingContact.setWork(contact.getWork());
			existingContact.setDescription(contact.getDescription());

			// Handle profile image update
			if (!file.isEmpty()) {
				String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
				String uploadDir = "src/main/resources/static/image/";

				File directory = new File(uploadDir);
				if (!directory.exists()) {
					directory.mkdirs(); // Create directory if not exists
				}

				try {
					// DELETE Old Image If Exists
					if (existingContact.getImage() != null) { // Use existingContact.getImage()
						Path oldImagePath = Paths.get(uploadDir + existingContact.getImage());
						File oldFile = oldImagePath.toFile();
						if (oldFile.exists()) {
							boolean deleted = oldFile.delete();
							if (deleted) {
								System.out.println("Old image deleted: " + existingContact.getImage());
							} else {
								System.out.println("Failed to delete old image: " + existingContact.getImage());
							}
						}
					}

					// SAVE New Image
					Path targetPath = Paths.get(uploadDir + fileName);
					Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

					existingContact.setImage(fileName); // Save new image name in DB
					redirectAttributes.addFlashAttribute("success", "Contact updated successfully!");

				} catch (IOException e) {
					redirectAttributes.addFlashAttribute("errorImage",
							"Failed to upload profile image. Please try again.");
					return "redirect:/user/showContacts/0";
				}
			}

			// Save contact to the database
			contactRepo.save(existingContact);

		} catch (Exception e) {
			e.printStackTrace(); // ✅ Print the exception for debugging
			redirectAttributes.addFlashAttribute("error", "An error occurred while updating the contact.");
		}

		return "redirect:/user/showContacts/0";
	}

	// profile handler
	@GetMapping("/profile")
	public String yourProfile(Model model) {

		model.addAttribute("title", "Profile Page");
		return "user/user_profile";
	}

	// delete User handler
	@GetMapping("/deleteUser/{userId}")
	public String deleteUser(@PathVariable("userId") Integer uId, RedirectAttributes redirectAttributes,
			Principal principal) {

		String userName = principal.getName(); // userName = email here
		User user = userRepo.findUserByEmail(userName);

		// Authorization check (only admin or the user themselves can delete)
		if (user.getRole().equals("ADMIN") || user.getUserId() == uId) {
			userRepo.deleteById(uId);
			redirectAttributes.addFlashAttribute("success", "User account has deleted successfully!");
			return "redirect:/signup"; // Redirect to contact list
		} else {
			redirectAttributes.addFlashAttribute("error", "You are not authorized to delete this user!");
			return "redirect:/user/user_profile";
		}

	}

	// open update form handler for user
	@GetMapping("/update-User/{userId}")
	public String updateFormUser(@PathVariable("userId") Integer uId, Model model,
			RedirectAttributes redirectAttributes, Principal principal) {

		model.addAttribute("title", "Update User");

		String userName = principal.getName(); // userName = email here
		User user = userRepo.findUserByEmail(userName);

		// Allow update only if loggedInUser is updating their own data
		if (user.getUserId() != uId) {
			redirectAttributes.addFlashAttribute("error", "You are not authorized to update this user!");
			return "redirect:/user/profile";
		}

		// No need to check existence in DB since user data is from session
		model.addAttribute("user", user);
		return "user/updateUserForm"; // ✅ Create `update_form.html`
	}

	// Handle Profile Update Request	
	@Transactional
	@PostMapping("/update-User")
	public String processUpdateForm(@Valid @ModelAttribute("user") User updatedUser,
	                                BindingResult result,
	                                HttpSession session,
	                                RedirectAttributes redirectAttributes,
	                                Model model,
	                                Principal principal) {

	    String userName = principal.getName(); // Logged-in user's email
	    User loggedInUser = userRepo.findUserByEmail(userName);

	    model.addAttribute("title", "Update User");

	    // Only allow if user is updating their own profile
	    if (loggedInUser.getUserId() != updatedUser.getUserId()) {
	        redirectAttributes.addFlashAttribute("error", "You are not authorized to update this user!");
	        return "redirect:/user/profile";
	    }

	    // Handle validation errors
	    if (result.hasErrors()) {
	        return "user/updateUserForm";
	    }

	    // Fetch existing user by ID
	    Optional<User> optionalUser = userRepo.findById(updatedUser.getUserId());
	    if (optionalUser.isEmpty()) {
	        redirectAttributes.addFlashAttribute("error", "User not found!");
	        return "redirect:/user/profile";
	    }

	    User existingUser = optionalUser.get();

	    // Save current role
	    String oldRole = existingUser.getRole().trim().toUpperCase();
	    String newRole = updatedUser.getRole().trim().toUpperCase();
	    System.out.println("String oldRole is " + " " + oldRole);
	    System.out.println("String newRole is " + " " + newRole);

	    // Update user fields
	    existingUser.setName(updatedUser.getName());
	    existingUser.setEmail(updatedUser.getEmail());
	    existingUser.setAbout(updatedUser.getAbout());
	    existingUser.setRole(newRole);  // ✅ Very important

	    userRepo.save(existingUser); // ✅ Save updated data

	    // DEBUG LOGS
	    System.out.println("Old Role: " + oldRole);
	    System.out.println("New Role: " + newRole);
	    System.out.println("Saved Role in DB: " + userRepo.findById(existingUser.getUserId()).get().getRole());

	    if ("ROLE_ADMIN".equals(updatedUser.getRole())) {
			session.invalidate(); // Invalidate current session
			redirectAttributes.addFlashAttribute("success",
					"Profile updated successfully. You are no longer an admin. Please login again.");
			return "redirect:/login";           
		}

	    // Stay logged in if role hasn't changed
	    redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
	    return "redirect:/user/profile";
	}   
    

	// open setting handler
	@GetMapping("/setting")
	public String openSetting(Model model) {

		model.addAttribute("title", "Settings");
		return "user/setting";
	}

	// change password handler
	@PostMapping("/changePassword")
	public String changePassword(@RequestParam("oldPassword") String oldPass,
			@RequestParam("newPassword") String newPass, HttpSession session, RedirectAttributes redirectAttributes,
			Model model, Principal principal) {

		// Validate old password field
		if (oldPass == null || oldPass.isBlank()) {
			redirectAttributes.addFlashAttribute("oldPasswordError", "Old password cannot be empty");
			return "redirect:/user/setting";
		}   

		// Validate new password field
		if (newPass == null || newPass.isBlank()) {
			redirectAttributes.addFlashAttribute("newPasswordError", "New password cannot be empty");
			return "redirect:/user/setting";
		}

		String userName = principal.getName(); // userName = email here
		User currentUser = userRepo.findUserByEmail(userName);

		if (passwordEncoder.matches(oldPass, currentUser.getPassword())) {
			currentUser.setPassword(passwordEncoder.encode(newPass));
			userRepo.save(currentUser);
			redirectAttributes.addFlashAttribute("success", "Password changed successfully");
			return "redirect:/user/user_dashboard";
		} else {
			redirectAttributes.addFlashAttribute("error", "Old password is incorrect");
			return "redirect:/user/setting";
		}

	}

}
