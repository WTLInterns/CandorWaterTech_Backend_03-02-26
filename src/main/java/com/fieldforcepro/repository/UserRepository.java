package com.fieldforcepro.repository;

import com.fieldforcepro.model.User;
import com.fieldforcepro.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    // Used for generating next sequential employeeCode for agents
    Optional<User> findTopByRoleOrderByEmployeeCodeDesc(UserRole role);
}
