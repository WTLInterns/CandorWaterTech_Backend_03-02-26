package com.fieldforcepro.controller;

import com.fieldforcepro.model.User;
import com.fieldforcepro.model.UserRole;
import com.fieldforcepro.repository.UserRepository;
import com.fieldforcepro.service.EmailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/agents")
@Tag(name = "Agents")
public class AgentController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public AgentController(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @GetMapping
    @Operation(summary = "List all agents")
    public List<User> listAgents() {
        return userRepository.findByRole(UserRole.AGENT);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent by id")
    public ResponseEntity<User> getAgent(@PathVariable("id") Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty() || user.get().getRole() != UserRole.AGENT) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(user.get());
    }

    public record AgentRequest(String firstName,
                               String middleName,
                               String lastName,
                               String email,
                               String mobile,
                               String aadhaar,
                               String panCard) { }

    @PostMapping
    @Operation(summary = "Create a new agent")
    public ResponseEntity<User> createAgent(@RequestBody AgentRequest request) {
        String fullName = (request.firstName() != null ? request.firstName() : "")
                + (request.lastName() != null ? " " + request.lastName() : "");

        String rawPassword = request.mobile();

        // Generate next sequential employeeCode for agents (1, 2, 3, ...)
        Integer nextCode = userRepository.findTopByRoleOrderByEmployeeCodeDesc(UserRole.AGENT)
                .map(u -> {
                    Integer current = u.getEmployeeCode();
                    return current == null ? 1 : current + 1;
                })
                .orElse(1);

        User agent = User.builder()
                .email(request.email())
                .passwordHash(rawPassword)
                .name(fullName.trim())
                .mobile(request.mobile())
                .aadhaar(request.aadhaar())
                .panCard(request.panCard())
                .employeeCode(nextCode)
                .role(UserRole.AGENT)
                .build();

        User saved = userRepository.save(agent);
        return ResponseEntity.created(URI.create("/agents/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an agent")
    public ResponseEntity<User> updateAgent(@PathVariable("id") Long id, @RequestBody AgentRequest update) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isEmpty() || existing.get().getRole() != UserRole.AGENT) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        User toSave = existing.get();
        String fullName = (update.firstName() != null ? update.firstName() : "")
                + (update.lastName() != null ? " " + update.lastName() : "");
        toSave.setName(fullName.trim());
        toSave.setEmail(update.email());
        toSave.setMobile(update.mobile());
        toSave.setAadhaar(update.aadhaar());
        toSave.setPanCard(update.panCard());
        toSave.setRole(UserRole.AGENT);

        User saved = userRepository.save(toSave);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/send-credentials")
    @Operation(summary = "Resend login credentials to an agent via email")
    public ResponseEntity<Void> resendCredentials(@PathVariable("id") Long id) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isEmpty() || existing.get().getRole() != UserRole.AGENT) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        User user = existing.get();
        String rawPassword = user.getMobile();
        emailService.sendAgentCredentials(user, rawPassword);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an agent")
    public ResponseEntity<Void> deleteAgent(@PathVariable("id") Long id) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isEmpty() || existing.get().getRole() != UserRole.AGENT) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}