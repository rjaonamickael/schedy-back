package com.schedy.repository;

import com.schedy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByRefreshToken(String refreshToken);
    Optional<User> findByEmployeId(String employeId);
    List<User> findAllByOrganisationId(String organisationId);
    long countByOrganisationId(String organisationId);

    @Query("SELECT u.organisationId, COUNT(u) FROM User u WHERE u.organisationId IN :orgIds GROUP BY u.organisationId")
    List<Object[]> countGroupedByOrganisationId(@Param("orgIds") Collection<String> orgIds);

    List<User> findByOrganisationIdAndRoleIn(String organisationId, List<User.UserRole> roles);
    Optional<User> findFirstByOrganisationIdAndRole(String organisationId, User.UserRole role);
    Optional<User> findByInvitationToken(String invitationToken);
    Optional<User> findByPasswordResetToken(String passwordResetToken);
    void deleteByOrganisationId(String organisationId);
}
