package com.smart.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.smart.entities.User;
import com.smart.repository.UserRepo;

@Component
public class UserDetailsServiceImpl implements UserDetailsService{

	@Autowired
	private UserRepo userRepo;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		//fetching user from databas
		User user = userRepo.findUserByEmail(username);
		
		if(user==null) {
			throw new UsernameNotFoundException("User not found...!");
		}
		
		CustomUserDetails customUserDetails = new CustomUserDetails(user);
		System.out.println("User Role (from DB): " + user.getRole()); // ✅ Check here
		return customUserDetails;
	}

}
