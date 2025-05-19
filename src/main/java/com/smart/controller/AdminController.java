package com.smart.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class AdminController {

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
		User admin = userRepo.findUserByEmail(userName);
		model.addAttribute("admin", admin);
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("title", "Admin Dashboard");
		model.addAttribute("totalUsers", userRepo.count());
		model.addAttribute("totalContacts", contactRepo.count());
		model.addAttribute("adminCount", userRepo.countByRole("ROLE_ADMIN"));
		model.addAttribute("userCount", userRepo.countByRole("ROLE_USER"));
		return "admin/admin_dashboard";
	}

	@GetMapping("/manage-users")
	public String listUsers(Model model, @RequestParam(defaultValue = "0") int page) {
		model.addAttribute("title", "Show Accounts");
		Page<User> users = userRepo.findAll(PageRequest.of(page, 10));
		model.addAttribute("users", users);
		return "admin/manage_users";
	}

	// LIST & SEARCH all contacts ➜ /admin/contacts
	@GetMapping("/manage-contacts")    
	public String listContacts(Model model, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "") String keyword) {

		model.addAttribute("title", "Show Contacts");
		Pageable pageable = PageRequest.of(page, 10, Sort.by("name").ascending());

		Page<Contact> contactPage = (keyword.isBlank()) ? contactRepo.findAll(pageable) // no search
				: contactRepo.searchAll(keyword.toLowerCase(), pageable);// search

		model.addAttribute("contactPage", contactPage);
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", contactPage.getTotalPages());
		model.addAttribute("keyword", keyword);

		return "admin/manage_contacts"; // templates/admin/manage_contacts.html
	}

	// profile handler
	@GetMapping("/profileAdmin")
	public String adminProfile(Model model, Principal principal) {
		model.addAttribute("title", "Admin profile");
		String username = principal.getName();
		User user = userRepo.findUserByEmail(username);
		model.addAttribute("user", user);
		return "admin/profile_admin";
	}

	// open setting handler
	@GetMapping("/settingAdmin")
	public String openSetting(Model model) {
		model.addAttribute("title", " Admin Settings");
		return "admin/setting_admin";
	}

	// change password handler
	@PostMapping("/changePassword")
	public String changePassword(@RequestParam("oldPassword") String oldPass,
			@RequestParam("newPassword") String newPass, HttpSession session, RedirectAttributes redirectAttributes,
			Model model, Principal principal) {

		model.addAttribute("title", "Password change");

		// Validate old password field
		if (oldPass == null || oldPass.isBlank()) {
			redirectAttributes.addFlashAttribute("oldPasswordError", "Old password cannot be empty");
			return "redirect:/admin/settingAdmin";
		}

		// Validate new password field
		if (newPass == null || newPass.isBlank()) {
			redirectAttributes.addFlashAttribute("newPasswordError", "New password cannot be empty");
			return "redirect:/admin/settingAdmin";
		}

		String userName = principal.getName(); // userName = email here
		User currentUser = userRepo.findUserByEmail(userName);

		if (passwordEncoder.matches(oldPass, currentUser.getPassword())) {
			currentUser.setPassword(passwordEncoder.encode(newPass));
			userRepo.save(currentUser);
			redirectAttributes.addFlashAttribute("success", "Password changed successfully");
			return "redirect:/admin/dashboard";
		} else {
			redirectAttributes.addFlashAttribute("error", "Old password is incorrect");
			return "redirect:/admin/settingAdmin";
		}

	}

	// ---------- manage users open edit form ----------
	@GetMapping("/edit/{id}")
	public String editUserForm(@PathVariable("id") Integer uId, Model model, RedirectAttributes redirectAttributes) {

		User user = userRepo.findById(uId).orElse(null);

		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "User not found!");
			return "redirect:/admin/manage-users";
		}

		model.addAttribute("title", "Edit User");
		model.addAttribute("userForm", user); // use a separate attribute name
		return "admin/edit_user_form"; // thymeleaf template
	}

	// manage users after edit then save changes
	@PostMapping("/update")
	public String updateUser(@Valid @ModelAttribute("userForm") User updatedUser, BindingResult result,
			@RequestParam("profileImage") MultipartFile file, RedirectAttributes redirectAttributes,
			HttpServletRequest request, HttpSession session, Principal principal, Model model) {

		model.addAttribute("title", "Update User");

		if (result.hasErrors()) {
			return "admin/edit_user_form";
		}

		User existingUser = userRepo.findById(updatedUser.getUserId()).orElse(null);
		if (existingUser == null) {
			redirectAttributes.addFlashAttribute("error", "User not found!");
			return "redirect:/admin/manage-users";
		}

		// Prevent unauthorized update if needed (optional for admin role)
		// You can include a check here if only self-edit is allowed

		// Update basic fields
		existingUser.setName(updatedUser.getName());
		existingUser.setEmail(updatedUser.getEmail());
		existingUser.setRole(updatedUser.getRole());
		existingUser.setAbout(updatedUser.getAbout());

		// Handle profile image update
		if (!file.isEmpty()) {
			String oldImage = existingUser.getImageUrl();
			if (oldImage != null && !oldImage.equals("default.png")) {
				try {
					Files.deleteIfExists(Paths.get("src/main/resources/static/image/" + oldImage));
				} catch (IOException e) {
					e.printStackTrace();
				}      
			}

			try {
				String newFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
				Path uploadPath = Paths.get("src/main/resources/static/image/");
				Files.createDirectories(uploadPath); // ensure directory exists
				Files.copy(file.getInputStream(), uploadPath.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
				existingUser.setImageUrl(newFileName);
			} catch (IOException e) {
				e.printStackTrace();
				redirectAttributes.addFlashAttribute("error", "Image upload failed.");
				return "redirect:/admin/manage-users";
			}
		}

		userRepo.save(existingUser);

		// Optional: if you are editing a logged-in user and role changes to USER, log
		// them out
		if (principal.getName().equalsIgnoreCase(existingUser.getEmail())
				&& "ROLE_USER".equals(updatedUser.getRole())) {
			session.invalidate(); // destroy session if admin downgraded self
			redirectAttributes.addFlashAttribute("success","User updated and You are no longer an admin. Please login again.");
			return "redirect:/login";
		}

		redirectAttributes.addFlashAttribute("success", "User updated successfully!");
		return "redirect:/admin/manage-users";
	}

	// manage users delete
	@GetMapping("/delete/{id}")
	public String deleteUser(@PathVariable("id") Integer uId, RedirectAttributes redirectAttributes) {

		Optional<User> optionalUser = userRepo.findById(uId);

		if (optionalUser.isPresent()) {
			User user = optionalUser.get();

			// Get image name
			String imageName = user.getImageUrl();

			// Check if image exists and is not shared with other users
			if (imageName != null) {
				long count = userRepo.countByImageUrl(imageName);
				if (count == 1) {
					try {
						Path imagePath = Paths.get("src/main/resources/static/image/" + imageName);
						Files.deleteIfExists(imagePath);
					} catch (IOException e) {
						e.printStackTrace(); // Optional: Log this
					}
				}
			}

			// Finally, delete user
			userRepo.deleteById(uId);

			redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
		} else {
			redirectAttributes.addFlashAttribute("error", "User not found!");
		}

		return "redirect:/admin/manage-users";
	}

	// ---------- manage contacts open edit form ----------
	@GetMapping("/manage-contacts/edit/{id}")
	public String showEditForm(@PathVariable("id") Integer cId, Model model, RedirectAttributes redirectAttributes) {

		Contact contact = contactRepo.findById(cId).orElse(null);

		if (contact == null) {
			redirectAttributes.addFlashAttribute("error", "Contact not found!");
			return "redirect:/admin/manage-contacts";
		}

		model.addAttribute("contact", contact);
		model.addAttribute("title", "Edit Contact");
		return "admin/edit_contact";
	}

	// manage contacts after edit then save changes
	@PostMapping("/manage-contacts/update")
	public String updateContact(@ModelAttribute Contact contact, RedirectAttributes redirectAttributes, Model model) {

		// Fetch the existing record
		Contact old = contactRepo.findById(contact.getContactId()).orElse(null);
		if (old == null) {
			redirectAttributes.addFlashAttribute("error", "Contact not found!");
			return "redirect:/admin/manage-contacts";
		}

		model.addAttribute("title", "Update contact");

		// Preserve fields that aren't edited in the form
		contact.setImage(old.getImage()); // keep old image filename
		contact.setUser(old.getUser()); // keep the owning user

		// Save updated contact
		contactRepo.save(contact);
		redirectAttributes.addFlashAttribute("success", "Contact updated successfully!");
		return "redirect:/admin/manage-contacts";
	}

	// manage contacts for delete
	@GetMapping("/manage-contacts/delete/{id}")
	public String deleteContact(@PathVariable("id") Integer cId, RedirectAttributes redirectAttributes, Model model) {

		model.addAttribute("title", "Delete contact");

		// 1. Fetch the contact
		Optional<Contact> contactOptional = contactRepo.findById(cId);
		if (contactOptional.isEmpty()) {
			redirectAttributes.addFlashAttribute("error", "Contact not found.");
			return "redirect:/admin/manage-contacts";
		}

		Contact contact = contactOptional.get();
		String imageName = contact.getImage();

		// 2. Delete the contact from DB
		contact.setUser(null); // avoid foreign key constraint
		contactRepo.delete(contact);

		// 3. Delete image only if not used by other contacts
		if (imageName != null && !imageName.trim().isEmpty()) {
			long count = contactRepo.countByImage(imageName);

			if (count == 0) {
				Path imagePath = Paths.get("src/main/resources/static/image").resolve(imageName).normalize();
				try {
					Files.deleteIfExists(imagePath);
					redirectAttributes.addFlashAttribute("success", "Contact and image deleted successfully.");
				} catch (IOException e) {
					e.printStackTrace();
					redirectAttributes.addFlashAttribute("error", "Contact deleted, but failed to delete image.");
				}
			} else {
				redirectAttributes.addFlashAttribute("success", "Contact deleted. Image retained (used by others).");
			}
		} else {
			redirectAttributes.addFlashAttribute("success", "Contact deleted.");
		}

		return "redirect:/admin/manage-contacts";
	}

	// ----------open profile edit form ----------
	@GetMapping("/Editprofile")
	public String showEditProfileForm(Model model, RedirectAttributes redirectAttributes, Principal principal) {
		String username = principal.getName();
		User user = userRepo.findUserByEmail(username);
		System.out.println("this is user data" + user);

		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "Contact User not found!");
			return "redirect:/admin/profileAdmin";
		}

		model.addAttribute("user", user);
		model.addAttribute("title", "Edit Profile");
		return "admin/edit_profile_admin"; // Create this Thymeleaf file
	}

	// ----------update profile edit form ----------
	@PostMapping("/profile/update")
	public String updateAdminProfile(@Valid @ModelAttribute("user") User updatedUser, BindingResult result,
			@RequestParam("profileImage") MultipartFile file, RedirectAttributes redirectAttributes,
			Principal principal, HttpServletRequest request, HttpSession session, Model model) {

		model.addAttribute("title", "Update Profile");

		if (result.hasErrors()) {
			return "admin/edit_profile_admin";
		}

		String email = principal.getName();
		User existingUser = userRepo.findUserByEmail(email);

		if (existingUser == null) {
			redirectAttributes.addFlashAttribute("error", "User not found.");
			return "redirect:/admin/profileAdmin";
		}

		// Prevent manipulation of other accounts
		if (existingUser.getUserId() != updatedUser.getUserId()) {
			redirectAttributes.addFlashAttribute("error", "Unauthorized update attempt.");
			return "redirect:/admin/profileAdmin";
		}

		// Update fields
		existingUser.setName(updatedUser.getName());
		existingUser.setAbout(updatedUser.getAbout());
		existingUser.setEmail(updatedUser.getEmail());
		existingUser.setRole(updatedUser.getRole());

		if (!file.isEmpty()) {
			String oldImage = existingUser.getImageUrl();
			if (oldImage != null && !oldImage.equals("default.png")) {
				try {
					Files.deleteIfExists(Paths.get("src/main/resources/static/image/" + oldImage));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			try {
				String newFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
				Path uploadPath = Paths.get("src/main/resources/static/image/");
				Files.createDirectories(uploadPath);
				Files.copy(file.getInputStream(), uploadPath.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
				existingUser.setImageUrl(newFileName);
			} catch (IOException e) {
				e.printStackTrace();
				redirectAttributes.addFlashAttribute("error", "Image upload failed.");
				return "redirect:/admin/profileAdmin";
			}
		}

		userRepo.save(existingUser);

		// If role changed to USER, logout
		if ("ROLE_USER".equals(updatedUser.getRole())) {
			session.invalidate(); // Invalidate current session
			redirectAttributes.addFlashAttribute("success",
					"Profile updated successfully. You are no longer an admin. Please login again.");
			return "redirect:/login";
		}

		redirectAttributes.addFlashAttribute("success", "Profile updated successfully.");
		return "redirect:/admin/profileAdmin";
	}

	// ----------profile delete handler ----------
	@GetMapping("/profile/delete")
	public String deleteAdminProfile(Principal principal, HttpSession session, RedirectAttributes redirectAttributes,
			Model model) {

		model.addAttribute("title", "Delete Profile");

		String email = principal.getName();
		User user = userRepo.findUserByEmail(email);

		if (user == null) {
			redirectAttributes.addFlashAttribute("error", "User not found.");
			return "redirect:/admin/profileAdmin";
		}

		// Delete profile image if not shared
		String imageName = user.getImageUrl();
		if (imageName != null) {
			long count = userRepo.countByImageUrl(imageName);
			if (count == 1) {
				try {
					Path imagePath = Paths.get("src/main/resources/static/image/" + imageName);
					Files.deleteIfExists(imagePath);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// Delete all related contacts
		List<Contact> contacts = contactRepo.findByUser(user);
		for (Contact contact : contacts) {
			contact.setUser(null);
			contactRepo.delete(contact);
		}

		// Delete user
		userRepo.delete(user);
		// Invalidate session and logout
		session.invalidate();
		return "redirect:/login?logout";
	}

}
