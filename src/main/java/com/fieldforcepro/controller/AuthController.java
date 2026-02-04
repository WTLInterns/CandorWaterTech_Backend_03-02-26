package com.fieldforcepro.controller;

import com.fieldforcepro.dto.auth.LoginRequest;
import com.fieldforcepro.dto.auth.UserDto;
import com.fieldforcepro.model.User;
import com.fieldforcepro.repository.UserRepository;
import com.fieldforcepro.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    @Operation(summary = "Login and return user details without security context")
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.username()).orElseThrow();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.toDto(user));
    }

    public record AgentLoginResponse(Long agentId, String name, String email, String mobile, Integer employeeCode) {}

    @PostMapping("/agent-login")
    @Operation(summary = "Agent login with email and password (raw password as stored)")
    public ResponseEntity<AgentLoginResponse> agentLogin(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.username()).orElse(null);
        if (user == null || user.getRole() != com.fieldforcepro.model.UserRole.AGENT) {
            return ResponseEntity.status(401).build();
        }

        // For agents we currently store raw password (e.g. mobile) in passwordHash field
        if (!request.password().equals(user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new AgentLoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getEmployeeCode()
        ));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserDto> register(@RequestBody User user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        User saved = userRepository.save(user);
        return ResponseEntity.created(URI.create("/auth/" + saved.getId())).body(authService.toDto(saved));
    }
}
