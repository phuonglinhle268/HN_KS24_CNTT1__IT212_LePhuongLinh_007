package com.banking.models.services;

import com.banking.exceptions.BusinessException;
import com.banking.models.constant.CustomerStatus;
import com.banking.models.dto.AuthRequest;
import com.banking.models.dto.RegisterRequest;
import com.banking.models.entities.Customer;
import com.banking.models.repositories.CustomerRepository;
import com.banking.security.jwt.JwtUtil;
import com.banking.security.principal.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, String> login(AuthRequest authRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword()));
        } catch (Exception e) {
            throw new BusinessException(401, "Incorrect username or password");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getEmail());
        final String jwt = jwtUtil.generateToken(userDetails);

        Map<String, String> data = new HashMap<>();
        data.put("token", jwt);
        return data;
    }

    public void register(RegisterRequest registerRequest) {
        Optional<Customer> existingUser = customerRepository.findByEmail(registerRequest.getEmail());
        if (existingUser.isPresent()) {
            throw new BusinessException(400, "Email already in use");
        }

        Customer customer = new Customer();
        customer.setFullName(registerRequest.getFullName());
        customer.setEmail(registerRequest.getEmail());
        customer.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        customer.setRole("CUSTOMER"); // Default role
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setAddress(registerRequest.getAddress());
        customer.setPhoneNumber(registerRequest.getPhoneNumber());
        customer.setIdentityNumber(registerRequest.getIdentityNumber());
        customer.setDateOfBirth(registerRequest.getDateOfBirth());

        customerRepository.save(customer);
    }
}
