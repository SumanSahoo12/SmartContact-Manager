package com.smart.controller;

import com.smart.entities.User;
import com.smart.repository.UserRepo;
import com.smart.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private EmailService emailService;

	// Home page
	@GetMapping("/home")
	public String home(Model model) {
		model.addAttribute("title", "Home page");
		model.addAttribute("currentPage", "home");
		return "home"; // This should match your homepage file in templates
	}

	// About page
	@GetMapping("/about")
	public String about(Model model) {
		model.addAttribute("title", "User About");
		model.addAttribute("currentPage", "about");
		return "about"; // This should match your aboutpage file in templates
	}

	// Display the signup form
	@GetMapping("/signup")
	public String showSignupForm(Model model) {
		model.addAttribute("title", "User Register");
		model.addAttribute("currentPage", "signup");
		model.addAttribute("user", new User()); // Bind an empty User object
		return "signup"; // This should match your signuppage file in templates
	}

	@PostMapping("/register")
	public String processSignup(@Valid @ModelAttribute("user") User usr, BindingResult result,
			@RequestParam(value = "agreement", required = false) Boolean agmt,
			@RequestParam("profileImage") MultipartFile file, Model model, RedirectAttributes redirectAttributes,
			HttpServletRequest request) {

		// 1. Form Validation
		if (result.hasErrors()) {
			return "signup";
		}

		if (agmt == null || !agmt) {
			model.addAttribute("error", "You must accept the terms and conditions!");
			return "signup";
		}

		if (userRepo.findByEmail(usr.getEmail()).isPresent()) {
			model.addAttribute("error", "Email is already registered! Please use another email.");
			return "signup";
		}

		try {
			// 2. Handle Image Upload
			if (!file.isEmpty()) {
				String uploadDir = "src/main/resources/static/image/";
				String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

				Files.createDirectories(Paths.get(uploadDir));
				Path path = Paths.get(uploadDir + fileName);
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
				usr.setImageUrl(fileName);
			} else {
				usr.setImageUrl("default.png");
			}

			// 3. Encode Password
			usr.setPassword(passwordEncoder.encode(usr.getPassword()));

			// 4. Set Role
			String selectedRole = usr.getRole().toUpperCase(); // e.g., "User" => "USER"
			usr.setRole("ROLE_" + selectedRole);

			// 5. Save User
			userRepo.save(usr);

			// 6. Success Message
			redirectAttributes.addFlashAttribute("success", "Registration successful! Please log in.");
			return "redirect:/login";

		} catch (IOException e) {
			e.printStackTrace();
			model.addAttribute("error", "Image upload failed.");
			return "signup";
		} catch (Exception e) {
			e.printStackTrace();
			model.addAttribute("error", "Something went wrong during registration.");
			return "signup";
		}
	}

	@GetMapping("/login")
	public String loginPage(Model model, @RequestParam(value = "error", required = false) String err,
			@RequestParam(value = "logout", required = false) String lgt) {

		model.addAttribute("title", "Login - Smart Contact Manager"); 
		model.addAttribute("currentPage", "login");
		
		if (err != null) {
			model.addAttribute("error", "Invalid email or password. Please try again.");
		}

		if (lgt != null) {
			model.addAttribute("logoutSuccess", "You have been logged out successfully.");
		}

		return "login"; // Returns login.html template
	}

	// email id form open handler
	@GetMapping("/forgot")
	public String openEmailForm() {
		return "forgot_email_form";
	}

	// sending otp
	@PostMapping("/send-otp")
	public String sendOTP(@RequestParam("email") String eml, Model model, HttpSession session) {

		// Manual validation logic
		if (eml == null || eml.isBlank()) {
			model.addAttribute("error", "Email is required");
			return "forgot_email_form"; // the name of your HTML Thymeleaf page
		}

		// Check if email matches a basic valid email format using regex
		if (!eml.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
			model.addAttribute("error", "Please enter a valid email address");
			return "forgot_email_form";
		}

		// generating otp of 4 digit
		int otp = new Random().nextInt(9000) + 1000;
		session.setAttribute("myOtp", otp);
		session.setAttribute("email", eml);

		// write code for send otp to email....

		boolean status = emailService.sendOtpEmail(eml, String.valueOf(otp));
		if (!status) {
			model.addAttribute("error", "Failed to send OTP. Try again.");
			return "forgot_email_form";
		}

		model.addAttribute("message", "OTP sent to your email");

		return "verify_otp";
	}

	// verifying otp
	@PostMapping("/verify-otp")
	public String verifyOTP(@RequestParam("otp") int OTP, Model model, HttpSession session) {

		int myOtp = (int) session.getAttribute("myOtp");
		String myEmail = (String) session.getAttribute("email");

		if (OTP == myOtp) {

			// password change form
			User user = userRepo.findUserByEmail(myEmail); // Validates email from DB

			if (user == null) {
				model.addAttribute("error", "User does not exist with this email..!");
				return "forgot_email_form"; // Goes back if email is not found

			}

			// send change password form
			model.addAttribute("message", "OTP verified successfully");
			return "password_change_form"; // page after successful OTP verification
		} else {
			model.addAttribute("error", "Invalid OTP");
			return "verify_otp"; // retry OTP verification
		}
	}

	// change password
	@PostMapping("/change-password")
	public String changePassword(@RequestParam("newPassword") String newPwd, HttpSession session, Model model) {

		String myEmail = (String) session.getAttribute("email");
		User user = userRepo.findUserByEmail(myEmail); // Validates email from DB
		System.out.println(user.getPassword());
		user.setPassword(passwordEncoder.encode(newPwd));
		System.out.println(user.getPassword());
		userRepo.save(user);

		model.addAttribute("success", "Your password changed successfully!");
		return "login"; // returns login.html

	}

}
