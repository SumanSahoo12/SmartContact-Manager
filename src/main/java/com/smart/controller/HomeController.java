package com.smart.controller;

import com.smart.entities.User;
import com.smart.repository.UserRepo;
import com.smart.service.EmailService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private EmailService emailService;

	// Home page
	@GetMapping("/home")
	public String home() {
		return "home"; // This should match your homepage file in templates
	}

	// About page
	@GetMapping("/about")
	public String about() {
		return "about"; // This should match your aboutpage file in templates
	}

	// Display the signup form
	@GetMapping("/signup")
	public String showSignupForm(Model model) {
		model.addAttribute("user", new User()); // Bind an empty User object
		return "signup"; // This should match your signuppage file in templates
	}

	@PostMapping("/signup")
	public String processSignup(@Valid @ModelAttribute("user") User user, BindingResult result,
			@RequestParam(value = "agreement", required = false) Boolean agreement, Model model,
			RedirectAttributes redirectAttributes) {

		// If validation errors exist, return to the form
		if (result.hasErrors()) {
			return "signup"; // Stay on the signup page with validation errors
		}

		// Manually validate agreement checkbox
		if (agreement == null || !agreement) {
			model.addAttribute("error", "You must accept the terms and conditions!");
			return "signup"; // Stay on the signup page
		}

		// Check for duplicate email
		if (userRepo.findByEmail(user.getEmail()).isPresent()) {
			model.addAttribute("error", "Email is already registered! Please use another email.");
			return "signup"; // Stay on the signup page with an error message
		}

		// Save user to database
		user.setImageUrl("default.png");
		userRepo.save(user);

		// Show success message on the login page
		redirectAttributes.addFlashAttribute("success", "Registration successful! Please log in.");
		return "redirect:/login"; // Redirects to login page
	}

	// Show login page
	@GetMapping("/login")
	public String login() {
		return "login"; // This should match your loginpage file in templates
	}

	// Handle login form submission
	@PostMapping("/login")
	public String processLogin(@RequestParam("email") String email, @RequestParam("password") String password,
			Model model, HttpSession session) {

		// Fetch user by email
		User user = userRepo.findUserByEmail(email);

		// Check if user exists or not
		if (user == null) {
			model.addAttribute("error", "Invalid email");
			return "login"; // Stay on login page with an error message
		}
		// Check if user exists and then password matches or not
		else if (!user.getPassword().equals(password)) {
			model.addAttribute("error", "Invalid password!");
			return "login"; // Stay on login page with an error message
		}
		// Check if user exists and password matches
		else {
			session.setAttribute("loggedInUser", user);
			return "redirect:/user_dashboard"; // Redirect to dashboard
		}
	}

	// email id form open handler
	@GetMapping("/forgot")
	public String openEmailForm() {
		return "forgot_email_form";
	}

	// sending otp
	@PostMapping("/send-otp")
	public String sendOTP(@RequestParam("email") String email, Model model, HttpSession session) {

		// Manual validation logic
		if (email == null || email.isBlank()) {
			model.addAttribute("error", "Email is required");
			return "forgot_email_form"; // the name of your HTML Thymeleaf page
		}

		// Check if email matches a basic valid email format using regex
		if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
			model.addAttribute("error", "Please enter a valid email address");
			return "forgot_email_form";
		}

		// generating otp of 4 digit
		int otp = new Random().nextInt(9000) + 1000;
		session.setAttribute("myOtp", otp);
		session.setAttribute("email", email);

		// write code for send otp to email....

		boolean status = emailService.sendOtpEmail(email, String.valueOf(otp));
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
			User user = userRepo.findUserByEmail(myEmail);  // Validates email from DB

			if (user == null) {
				model.addAttribute("error", "User does not exist with this email..!");
				return "forgot_email_form";  // Goes back if email is not found

			}

			// send change password form
			model.addAttribute("message", "OTP verified successfully");
			return "password_change_form"; // page after successful OTP verification
		} 
		else {
			model.addAttribute("error", "Invalid OTP");
			return "verify_otp"; // retry OTP verification
		}
	}
	
	//change password 
	@PostMapping("/change-password")
	public String changePassword(@RequestParam("newPassword") String newPwd, HttpSession session, Model model) {
		
		String myEmail = (String) session.getAttribute("email");
		User user = userRepo.findUserByEmail(myEmail);  // Validates email from DB
		System.out.println(user.getPassword());
		user.setPassword(newPwd);
		System.out.println(user.getPassword());
		userRepo.save(user);
		
		model.addAttribute("success", "Your password changed successfully!");
		return "login"; // returns login.html

	}

}
