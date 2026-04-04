package com.schedy.controller;

import com.schedy.dto.request.RegistrationRequestDto;
import com.schedy.dto.response.RegistrationRequestResponse;
import com.schedy.service.RegistrationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Unauthenticated public endpoint for organisation registration requests.
 * Mapped under /api/v1/public/** which is permitted in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicRegistrationController {

    private final RegistrationRequestService registrationRequestService;

    /**
     * POST /api/v1/public/registration-requests
     * Submit a new organisation registration request.
     * Returns 201 with a confirmation message on success.
     * Returns 409 if a PENDING request already exists for the same email.
     */
    @PostMapping("/registration-requests")
    public ResponseEntity<Map<String, Object>> submitRegistrationRequest(
            @Valid @RequestBody RegistrationRequestDto dto) {

        RegistrationRequestResponse created = registrationRequestService.submitRequest(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message",
            "Votre demande a bien été reçue. "
            + "Nous l'examinerons dans les meilleurs délais et vous contacterons par email. / "
            + "Your request has been received. "
            + "We will review it and contact you by email shortly.",
            "requestId", created.id()
        ));
    }
}
