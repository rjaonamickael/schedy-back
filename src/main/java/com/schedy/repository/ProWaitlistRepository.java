package com.schedy.repository;

import com.schedy.entity.ProWaitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface ProWaitlistRepository extends JpaRepository<ProWaitlist, String> {
    boolean existsByEmail(String email);
}
