package com.schedy.repository;

import com.schedy.entity.RegistrationRequest;
import com.schedy.entity.RegistrationRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, String> {
    List<RegistrationRequest> findAllByStatusOrderByCreatedAtDesc(RequestStatus status);
    List<RegistrationRequest> findAllByOrderByCreatedAtDesc();
    boolean existsByContactEmailAndStatus(String contactEmail, RequestStatus status);
}
