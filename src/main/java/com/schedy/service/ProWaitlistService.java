package com.schedy.service;

import com.schedy.dto.request.WaitlistJoinRequest;
import com.schedy.entity.ProWaitlist;
import com.schedy.repository.ProWaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the PRO plan waitlist.
 *
 * Join is idempotent: if the email is already registered the method returns
 * {@code false} (caller maps this to HTTP 200). A new entry returns {@code true}
 * (caller maps this to HTTP 201).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProWaitlistService {

    private final ProWaitlistRepository proWaitlistRepository;
    private final EmailService emailService;

    /**
     * Registers an email address on the PRO waitlist.
     *
     * @param dto the inbound request
     * @return {@code true} if a new entry was created, {@code false} if the email
     *         was already present (idempotent duplicate)
     */
    @Transactional
    public boolean join(WaitlistJoinRequest dto) {
        if (proWaitlistRepository.existsByEmail(dto.email())) {
            log.debug("PRO waitlist: duplicate sign-up ignored for email={}",
                    maskEmail(dto.email()));
            return false;
        }

        ProWaitlist entry = ProWaitlist.builder()
                .email(dto.email())
                .language(dto.language())
                .source(dto.source())
                .build();

        proWaitlistRepository.save(entry);

        log.info("PRO waitlist: new sign-up — source={}, lang={}",
                dto.source(), dto.language());

        emailService.sendWaitlistConfirmationEmail(dto.email(), dto.language());

        return true;
    }

    /**
     * Masks the local part of an email for safe logging.
     * E.g. "user@example.com" becomes "us**@example.com".
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "**" + email.substring(atIndex);
        return email.substring(0, 2) + "**" + email.substring(atIndex);
    }
}
