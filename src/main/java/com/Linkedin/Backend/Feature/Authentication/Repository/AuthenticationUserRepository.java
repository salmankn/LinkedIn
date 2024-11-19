package com.Linkedin.Backend.Feature.Authentication.Repository;

import com.Linkedin.Backend.Feature.Authentication.Model.AuthenticationUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthenticationUserRepository extends JpaRepository<AuthenticationUser, Long> {

    Optional<AuthenticationUser> findByEmail(String email);
}
