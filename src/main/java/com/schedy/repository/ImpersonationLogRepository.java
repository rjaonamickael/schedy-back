package com.schedy.repository;

import com.schedy.entity.ImpersonationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImpersonationLogRepository extends JpaRepository<ImpersonationLog, String> {
    Page<ImpersonationLog> findAllByOrderByStartedAtDesc(Pageable pageable);
}
