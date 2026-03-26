package com.schedy.repository;

import com.schedy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByRefreshToken(String refreshToken);
    Optional<User> findByEmployeId(String employeId);
    List<User> findAllByOrganisationId(String organisationId);
    Optional<User> findByInvitationToken(String invitationToken);
}
