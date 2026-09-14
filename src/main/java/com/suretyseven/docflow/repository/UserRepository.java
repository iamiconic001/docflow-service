package com.suretyseven.docflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suretyseven.docflow.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
