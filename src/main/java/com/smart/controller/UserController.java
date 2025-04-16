package com.smart.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
public class UserController {

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private ContactRepo contactRepo;

	// Common data method to add user details to the model
	@ModelAttribute
	public void addCommonData(HttpSession session, Model model) {
		User user = (User) session.getAttribute("loggedInUser");
		if (user != null) {
			model.addAttribute("username", user);
		}
	}

	// User Dashboard (redirects to login if not authenticated)
	@GetMapping("/user_dashboard")
	public String userDashboard(HttpSession session, RedirectAttributes redirectAttributes, Model model) {
		User user = (User) session.getAttribute("loggedInUser");
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login"; // Ensure only logged-in users access the dashboard
		}
		model.addAttribute("title", "User Dashboard");
		return "normal/user_dashboard"; // Return user dashboard view
	}      

	// Logout Handler
	@GetMapping("/logout")
	public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
		session.invalidate(); // Destroy user session
		redirectAttributes.addFlashAttribute("logoutSuccess", "You have logged out successfully.");
		return "redirect:/login"; // Redirect to login page
	}

	// Open Add Contact Form (Only for logged-in users)
	@GetMapping("/addContact")
	public String openAddContactForm(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
		User user = (User) session.getAttribute("loggedInUser");
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login"; // Redirect if not logged in
		}
		model.addAttribute("title", "Add Contact");
		model.addAttribute("contact", new Contact());
		return "normal/addContactForm";
	}

	// Process Contact Form Submission
	@Transactional // Ensures Hibernate session is active
	@PostMapping("/processContact")
	public String processContact(HttpSession session, @Valid @ModelAttribute("contact") Contact contact,
			BindingResult result, @RequestParam("profileImage") MultipartFile file, // Image is now mandatory
			Model model, RedirectAttributes redirectAttributes) {

		User userSession = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (userSession == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// Fetch user with contacts to avoid LazyInitializationException
		User user = userRepo.findUserWithContacts(userSession.getUserId()).orElse(null);

		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "User not found!");
			return "redirect:/login";
		}

		// Check for validation errors
		if (result.hasErrors()) {
			model.addAttribute("title", "Add Contact");
			return "normal/addContactForm";
		}

		// **Check if email exists and get username**
		Optional<String> existingUserName = contactRepo.findNameByEmail(contact.getEmail());

		if (existingUserName.isPresent()) {
			String existingName = existingUserName.get();
			if (!existingName.equalsIgnoreCase(contact.getName())) {
				redirectAttributes.addFlashAttribute("error",
						"You are not '" + existingName + "'. Please use the correct email associated with your name.");
				return "redirect:/addContact";
			}
		}

		// Ensure Image is Uploaded (Mandatory)
		if (file == null || file.isEmpty()) {
			redirectAttributes.addFlashAttribute("errorImage", "Profile image is required!");
			return "redirect:/addContact"; // Redirect back to form
		}

		// Save Image to Folder
		String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
		String uploadDir = "src/main/resources/static/image/";

		File directory = new File(uploadDir);
		if (!directory.exists()) {
			directory.mkdirs(); // Create directory if not exists
		}

		try {
			Path targetPath = Paths.get(uploadDir + fileName);
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
			contact.setImage(fileName); // Save image name in DB
		} catch (IOException e) {
			redirectAttributes.addFlashAttribute("errorImage", "Error uploading image. Please try again.");
			return "redirect:/addContact";
		}

		// link contact with user and save
		contact.setUser(user);
		user.getContacts().add(contact);
		userRepo.save(user);

		redirectAttributes.addFlashAttribute("success", "Contact added successfully!");
		return "redirect:/addContact";
	}

	// Show contacts handler
	// per page =5[n]
	// current page = 0[page]
	@GetMapping("/showContacts/{page}")
	public String showContacts(@PathVariable("page") Integer page, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {

		// Retrieve logged-in user from session
		User user = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// currentPage - page
		// Contact per page - 4
		Pageable pageable = PageRequest.of(page, 4);

		// Fetch user's contacts safely
		Page<Contact> contacts = contactRepo.findContactsByUser(user.getUserId(), pageable);
		if (contacts == null || contacts.isEmpty()) {
			model.addAttribute("message", "No contacts found...!");
		} else {
			model.addAttribute("contacts", contacts);
		}

		model.addAttribute("title", "Show User Contacts");
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", contacts.getTotalPages());

		return "normal/show_contacts";
	}

	// showing particular contact details
	@GetMapping("/{contactId}/contact")
	public String showContactDetail(@PathVariable("contactId") Integer contactId, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {

		// Retrieve logged-in user from session
		User user = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}
		// Find the contact and handle the absence gracefully
		Optional<Contact> contactOptional = contactRepo.findById(contactId);

		// First check if contact exists
		if (contactOptional.isPresent()) {
			Contact contact = contactOptional.get();

			// Check if the contact belongs to the logged-in user
			if (user.getUserId() == contact.getUser().getUserId()) {
				model.addAttribute("contact", contact);
				return "normal/contact_details";
			} else {
				model.addAttribute("error", "You don't have permission to view this contact.");
				return "normal/contact_details"; // Show error on the same page
			}
		} else {
			// Handle invalid contact ID
			model.addAttribute("error", "No contact found with the provided ID..!");
			return "normal/contact_details"; // Show error on the same page
		}

	}

	// delete contact handler
	@GetMapping("/delete/{contactId}")
	public String deleteContact(@PathVariable("contactId") Integer contactId, HttpSession session,
			RedirectAttributes redirectAttributes) {

		// Retrieve logged-in user from session
		User user = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// Find the contact and handle the absence gracefully
		Optional<Contact> contactOptional = contactRepo.findById(contactId);

		if (contactOptional.isPresent()) {
			Contact contact = contactOptional.get();

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

		return "redirect:/showContacts/0"; // Redirect to contact list
	}

	// open update form handler
	@GetMapping("/update-contact/{contactId}")
	public String upadteForm(@PathVariable("contactId") Integer contactId, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {

		model.addAttribute("title", "Update Contact");

		// Retrieve logged-in user from session
		User user = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// Find the contact and handle the absence gracefully
		Optional<Contact> contactOptional = contactRepo.findById(contactId);

		// First check if contact exists
		if (contactOptional.isPresent()) {
			Contact contact = contactOptional.get();

			// Check if the contact belongs to the logged-in user
			if (user.getUserId() == contact.getUser().getUserId()) {

				model.addAttribute("contact", contact);
			} else {
				// Use redirectAttributes for consistent error handling
				redirectAttributes.addFlashAttribute("error", "You don't have permission to update this contact..!");
			}
		} else {
			// Handle invalid contact ID using redirectAttributes
			redirectAttributes.addFlashAttribute("error", "No contact found with the provided ID.");
		}

		return "normal/update_form";
	}

	// update contact handler
	@Transactional
	@PostMapping("/processUpdate/{contactId}")
	public String updateContact(@PathVariable("contactId") int contactId, @ModelAttribute Contact contact,
			@RequestParam("profileImage") MultipartFile file, HttpSession session,
			RedirectAttributes redirectAttributes) {

		try {
			// Check if user is logged in
			User user = (User) session.getAttribute("loggedInUser");
			if (user == null) {
				redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
				return "redirect:/login";
			}

			// Find existing contact
			Optional<Contact> contactOptional = contactRepo.findById(contactId);
			if (contactOptional.isEmpty()) {
				redirectAttributes.addFlashAttribute("error", "Contact not found!");
				return "redirect:/showContacts/0";
			}

			Contact existingContact = contactOptional.get();

			// Check if the user is authorized to update the contact
			if (existingContact.getUser().getUserId() != user.getUserId()) {
				redirectAttributes.addFlashAttribute("error", "You are not authorized to update this contact!");
				return "redirect:/showContacts/0";
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
					return "redirect:/showContacts/0";
				}
			}

			// Save contact to the database
			contactRepo.save(existingContact);

		} catch (Exception e) {
			e.printStackTrace(); // ✅ Print the exception for debugging
			redirectAttributes.addFlashAttribute("error", "An error occurred while updating the contact.");
		}

		return "redirect:/showContacts/0";
	}

	// profile handler
	@GetMapping("/profile")
	public String yourProfile(HttpSession session, Model model, RedirectAttributes redirectAttributes) {

		User user = (User) session.getAttribute("loggedInUser");
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		model.addAttribute("title", "Profile Page");
		model.addAttribute("user", user);
		return "normal/user_profile";
	}

	// delete User handler
	@GetMapping("/deleteUser/{userId}")
	public String deleteUser(@PathVariable("userId") Integer userId, HttpSession session,
			RedirectAttributes redirectAttributes) {

		// Retrieve logged-in user from session
		User user = (User) session.getAttribute("loggedInUser");

		// Check if the user is logged in
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		} else {
			// ✅ Authorization check (only admin or the user themselves can delete)
			if (user.getRole().equals("ADMIN") || user.getUserId() == userId) {
				userRepo.deleteById(userId);
				redirectAttributes.addFlashAttribute("success", "User account has deleted successfully!");
				return "redirect:/signup"; // Redirect to contact list
			} else {
				redirectAttributes.addFlashAttribute("error", "You are not authorized to delete this user!");
				return "redirect:/user_profile";
			}
		}

	}

	// open update form handler for user
	@GetMapping("/update-User/{userId}")
	public String updateFormUser(@PathVariable("userId") Integer userId, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {

		model.addAttribute("title", "Update User");

		// Retrieve logged-in user from session
		User loggedInUser = (User) session.getAttribute("loggedInUser");

		if (loggedInUser == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// ✅ Allow update only if loggedInUser is updating their own data
		if (loggedInUser.getUserId() != userId) {
			redirectAttributes.addFlashAttribute("error", "You are not authorized to update this user!");
			return "redirect:/profile";
		}

		// No need to check existence in DB since user data is from session
		model.addAttribute("user", loggedInUser);
		return "normal/updateUserForm"; // ✅ Create `update_form.html`
	}

	// Handle Profile Update Request
	@Transactional
	@PostMapping("/update-User")
	public String processUpdateForm(@Valid @ModelAttribute("user") User updatedUser, BindingResult result,
			HttpSession session, RedirectAttributes redirectAttributes, Model model) {

		model.addAttribute("title", "Update User");

		// Retrieve logged-in user from session
		User loggedInUser = (User) session.getAttribute("loggedInUser");

		if (loggedInUser == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login";
		}

		// Make sure userId is coming from form
		System.out.println("Form submitted with userId: " + updatedUser.getUserId());

		// Allow update only if loggedInUser is updating their own data
		if (loggedInUser.getUserId() != updatedUser.getUserId()) {
			redirectAttributes.addFlashAttribute("error", "You are not authorized to update this user!");
			return "redirect:/profile";
		}

		// Handle validation errors
		if (result.hasErrors()) {
			System.out.println("Validation errors: " + result);
			return "normal/updateUserForm"; // ✅ Return back to the form if validation fails
		}

		// Fetch existing user from the database
		Optional<User> userOptional = userRepo.findById(loggedInUser.getUserId());
		if (userOptional.isEmpty()) {
			redirectAttributes.addFlashAttribute("error", "User not found!");
			return "redirect:/profile";
		}

		User existingUser = userOptional.get();

		// Update fields
		existingUser.setName(updatedUser.getName());
		existingUser.setEmail(updatedUser.getEmail());
//		existingUser.setPassword(updatedUser.getPassword());
		existingUser.setRole(updatedUser.getRole());
		existingUser.setAbout(updatedUser.getAbout());

		System.out.println(updatedUser);

		// ✅ Save updated user
		userRepo.save(existingUser);

		// ✅ Fix: Update session with latest user data
		session.setAttribute("loggedInUser", existingUser);

		model.addAttribute("success", "Profile updated successfully!");
		return "redirect:/profile"; // ✅ Redirect to profile page after successful update
	}

	// open setting handler
	@GetMapping("/setting")
	public String openSetting(HttpSession session, RedirectAttributes redirectAttributes, Model model) {

		User user = (User) session.getAttribute("loggedInUser");
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login"; // Ensure only logged-in users access the dashboard
		}
		model.addAttribute("title", "Settings");

		return "normal/setting";
	}

	// change password handler
	@PostMapping("/changePassword")
	public String changePassword(@RequestParam("oldPassword") String oldPass,
			@RequestParam("newPassword") String newPass, HttpSession session, RedirectAttributes redirectAttributes,
			Model model) {

		User user = (User) session.getAttribute("loggedInUser");
		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Login required! Please sign in to proceed.");
			return "redirect:/login"; // Ensure only logged-in users access the dashboard
		}

		// ✅ Validate old password field
		if (oldPass == null || oldPass.isBlank()) {
			redirectAttributes.addFlashAttribute("oldPasswordError", "Old password cannot be empty");
			return "redirect:/setting";
		}

		// ✅ Validate new password field
		if (newPass == null || newPass.isBlank()) {
			redirectAttributes.addFlashAttribute("newPasswordError", "New password cannot be empty");
			return "redirect:/setting";
		}

		// ✅ Check if old password matches (example logic)
		if (!user.getPassword().equals(oldPass)) {
			redirectAttributes.addFlashAttribute("error", "Old password is incorrect");
			return "redirect:/setting";
		}

		// ✅ Condition 1: Check password length (between 6 and 20 characters)
		if (newPass.length() < 6 || newPass.length() > 20) {
			redirectAttributes.addFlashAttribute("newPasswordError", "Password must be between 6 and 20 characters!");
			return "redirect:/setting";
		}

		// ✅ Condition 2: Check for uppercase, lowercase, digit, and special character
		if (!newPass.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$")) {
			redirectAttributes.addFlashAttribute("newPasswordError",
					"Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character!");
			return "redirect:/setting";
		}

		// ✅ Update password (example logic)
		user.setPassword(newPass);
		userRepo.save(user);

		redirectAttributes.addFlashAttribute("success", "Password changed successfully");

		return "redirect:/user_dashboard";
	}

}
