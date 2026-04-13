package com.schedy.service;

import com.schedy.service.email.EmailHtmlBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin orchestrator: resolves email subjects, delegates HTML construction
 * to {@link EmailHtmlBuilder}, then routes delivery through Brevo API
 * (preferred) or SMTP (fallback).
 *
 * Public method signatures are intentionally stable — all callers and all
 * Mockito mocks in the test suite target this class directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender    mailSender;
    private final EmailHtmlBuilder  htmlBuilder;

    @Value("${spring.mail.from:no-reply@schedy.work}")
    private String fromAddress;

    @Value("${schedy.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    // ── Auth / account emails ──────────────────────────────────────────────

    @Async
    public void sendInvitationEmail(String recipientEmail, String recipientName, String rawToken) {
        String link    = frontendUrl + "/set-password?token=" + rawToken;
        String subject = "Activation de votre compte utilisateur Schedy / Schedy user account activation";
        String html    = htmlBuilder.buildInvitationHtml(EmailHtmlBuilder.escapeHtml(recipientName), link);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void send2faCodeEmail(String recipientEmail, String recipientName, String code, int expirySeconds) {
        String subject = "Code de v\u00e9rification Schedy / Schedy verification code";
        String minutes = String.valueOf(expirySeconds / 60);
        String name    = EmailHtmlBuilder.escapeHtml(recipientName != null ? recipientName : recipientEmail);
        String html    = htmlBuilder.build2faCodeHtml(name, code, minutes);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendAdminInvitationEmail(String recipientEmail, String orgName, String rawToken) {
        String link    = frontendUrl + "/set-password?token=" + rawToken;
        String subject = "Activation de votre acc\u00e8s administrateur - " + orgName
                       + " / Administrator account activation - " + orgName;
        String html    = htmlBuilder.buildAdminInvitationHtml(EmailHtmlBuilder.escapeHtml(orgName), link);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken) {
        String subject = "R\u00e9initialisation de votre mot de passe Schedy / Schedy password reset";
        String name    = EmailHtmlBuilder.escapeHtml(recipientName != null ? recipientName : recipientEmail);
        String html    = htmlBuilder.buildPasswordResetHtml(name, rawToken);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendPromotionEmail(String recipientEmail, String recipientName) {
        String subject = "Mise \u00e0 jour de vos acc\u00e8s Schedy - Nouveau r\u00f4le : Manager / Schedy account update - Manager role assigned";
        String html    = htmlBuilder.buildPromotionHtml(EmailHtmlBuilder.escapeHtml(recipientName), frontendUrl);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendWaitlistConfirmationEmail(String recipientEmail, String language) {
        boolean isFr   = "fr".equalsIgnoreCase(language);
        String subject = isFr
            ? "Schedy PRO \u2014 Vous \u00eates sur la liste d'attente"
            : "Schedy PRO \u2014 You're on the waitlist";
        String html    = htmlBuilder.buildWaitlistHtml(recipientEmail, isFr);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    // ── Absence emails ─────────────────────────────────────────────────────

    @Async
    public void sendAbsenceSignaleeEmail(String recipientEmail, String recipientName,
                                          com.schedy.entity.AbsenceImprevue absence, String employeNom) {
        String subject  = "Absence impr\u00e9vue signal\u00e9e \u2014 " + EmailHtmlBuilder.escapeHtml(employeNom)
                        + " / Unplanned absence reported";
        String appLink  = frontendUrl + "/planning";
        String html     = htmlBuilder.buildAbsenceSignaleeHtml(recipientName, absence, employeNom, appLink);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendAbsenceValideeEmail(String recipientEmail, String recipientName, String date) {
        String subject = "Votre absence du " + date + " a \u00e9t\u00e9 accept\u00e9e / Absence accepted";
        String html    = htmlBuilder.buildAbsenceValideeHtml(recipientName, date);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendAbsenceRefuseeEmail(String recipientEmail, String recipientName,
                                         String date, String noteRefus) {
        String subject = "Votre absence du " + date + " a \u00e9t\u00e9 refus\u00e9e / Absence declined";
        String html    = htmlBuilder.buildAbsenceRefuseeHtml(recipientName, date, noteRefus);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendAbsenceAnnuleeEmail(String recipientEmail, String recipientName,
                                         String employeNom, String date) {
        String subject = "Absence annul\u00e9e \u2014 " + date + " / Absence cancelled";
        String html    = htmlBuilder.buildAbsenceAnnuleeHtml(recipientName, employeNom, date);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    // ── Registration request notifications ────────────────────────────────

    @Async
    public void sendRegistrationRequestConfirmation(
            String recipientEmail, String contactName, String orgName) {
        String subject  = "Demande d\u2019inscription Schedy re\u00e7ue \u2014 " + orgName
                        + " / Schedy registration request received \u2014 " + orgName;
        String year     = String.valueOf(java.time.Year.now().getValue());
        String nameSafe = EmailHtmlBuilder.escapeHtml(contactName);
        String orgSafe  = EmailHtmlBuilder.escapeHtml(orgName);
        String html     = htmlBuilder.buildRegistrationRequestConfirmationHtml(nameSafe, orgSafe, year);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendRegistrationRequestInternalNotification(
            String contactName, String contactEmail, String orgName,
            String pays, String province, String desiredPlan,
            Integer employeeCount, String billingCycle, String message) {

        String subject   = "\uD83D\uDCE5 Nouvelle demande d\u2019inscription \u2014 " + orgName;
        String year      = String.valueOf(java.time.Year.now().getValue());
        String nameSafe  = EmailHtmlBuilder.escapeHtml(contactName);
        String emailSafe = EmailHtmlBuilder.escapeHtml(contactEmail);
        String orgSafe   = EmailHtmlBuilder.escapeHtml(orgName);
        String paysSafe  = EmailHtmlBuilder.escapeHtml(pays != null ? pays : "\u2014");
        String provSafe  = EmailHtmlBuilder.escapeHtml(province != null && !province.isBlank() ? province : "\u2014");
        String planSafe  = EmailHtmlBuilder.escapeHtml(desiredPlan != null ? desiredPlan : "\u2014");
        String empStr    = employeeCount != null ? String.valueOf(employeeCount) : "\u2014";
        String billSafe  = EmailHtmlBuilder.escapeHtml(billingCycle != null ? billingCycle : "\u2014");
        String msgSafe   = message != null && !message.isBlank() ? EmailHtmlBuilder.escapeHtml(message) : "\u2014";

        String html = htmlBuilder.buildRegistrationRequestInternalNotificationHtml(
            nameSafe, emailSafe, orgSafe, paysSafe, provSafe,
            planSafe, empStr, billSafe, msgSafe, year);

        // Internal notification goes to the contact address, not the submitter
        String contactAddress = htmlBuilder.getContactAddress();
        sendHtmlEmail(contactAddress, subject, html);
    }

    @Async
    public void sendRegistrationRequestRejection(
            String recipientEmail, String contactName, String orgName, String reason) {
        String subject    = "Votre demande d\u2019inscription Schedy \u2014 " + orgName
                          + " / Your Schedy registration request \u2014 " + orgName;
        String year       = String.valueOf(java.time.Year.now().getValue());
        String nameSafe   = EmailHtmlBuilder.escapeHtml(contactName);
        String orgSafe    = EmailHtmlBuilder.escapeHtml(orgName);
        String reasonSafe = EmailHtmlBuilder.escapeHtml(reason);
        String html       = htmlBuilder.buildRegistrationRequestRejectionHtml(nameSafe, orgSafe, reasonSafe, year);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    // ── Delivery ───────────────────────────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String html) {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevoApi(to, subject, html);
        } else {
            sendViaSmtp(to, subject, html);
        }
    }

    private void sendViaBrevoApi(String to, String subject, String html) {
        try {
            log.info("Sending email via Brevo API to {}", to);
            String htmlWithUrl = html.replace("cid:schedy_logo", frontendUrl + "/logo.png");

            var payload = java.util.Map.of(
                "sender", java.util.Map.of("name", "Schedy", "email", fromAddress),
                "to",     java.util.List.of(java.util.Map.of("email", to)),
                "subject",     subject,
                "htmlContent", htmlWithUrl
            );
            String json = OBJECT_MAPPER.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent via Brevo API to {}", to);
            } else {
                log.error("Brevo API error ({}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Brevo API to {}: {}", to, e.getMessage(), e);
        }
    }

    private void sendViaSmtp(String to, String subject, String html) {
        try {
            log.info("Sending email via SMTP to {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Schedy");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("schedy_logo", new ClassPathResource("email/logo.png"), "image/png");
            mailSender.send(message);
            log.info("Email sent via SMTP to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", to, e.getMessage(), e);
        }
    }
}
