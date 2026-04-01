package com.schedy.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@schedy.work}")
    private String fromAddress;

    @Value("${schedy.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${schedy.invitation.expiry-hours:24}")
    private int expiryHours;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    private static final String CID_LOGO_IMG =
        "<img src=\"cid:schedy_logo\" alt=\"Schedy\" width=\"160\" "
        + "style=\"display:block;border:0;height:auto;max-width:160px;\"/>";

    @Async
    public void sendInvitationEmail(String recipientEmail, String recipientName, String rawToken, boolean isFrench) {
        String link = frontendUrl + "/set-password?token=" + rawToken;
        String subject = "Activation de votre compte utilisateur Schedy / Schedy user account activation";
        String html = buildInvitationHtml(escapeHtml(recipientName), link);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void send2faCodeEmail(String recipientEmail, String recipientName, String code, int expirySeconds) {
        String subject = "Code de v\u00e9rification Schedy / Schedy verification code";
        String minutes = String.valueOf(expirySeconds / 60);
        String html = build2faCodeHtml(escapeHtml(recipientName != null ? recipientName : recipientEmail), code, minutes);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendAdminInvitationEmail(String recipientEmail, String orgName, String rawToken, boolean isFrench) {
        String link = frontendUrl + "/set-password?token=" + rawToken;
        String subject = "Activation de votre acc\u00e8s administrateur - " + orgName + " / Administrator account activation - " + orgName;
        String html = buildAdminInvitationHtml(escapeHtml(orgName), link);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken, boolean isFrench) {
        String subject = "R\u00e9initialisation de votre mot de passe Schedy / Schedy password reset";
        String html = buildPasswordResetHtml(escapeHtml(recipientName != null ? recipientName : recipientEmail), rawToken);
        sendHtmlEmail(recipientEmail, subject, html);
    }

    @Async
    public void sendPromotionEmail(String recipientEmail, String recipientName, boolean isFrench) {
        String link = frontendUrl;
        String subject = "Mise \u00e0 jour de vos acc\u00e8s Schedy - Nouveau r\u00f4le : Manager / Schedy account update - Manager role assigned";
        String html = buildPromotionHtml(escapeHtml(recipientName), link);
        sendHtmlEmail(recipientEmail, subject, html);
    }

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
                "to", java.util.List.of(java.util.Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlWithUrl
            );
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildPromotionHtml(String name, String link) {
        String year = String.valueOf(java.time.Year.now().getValue());

        return "<!DOCTYPE html>\n"
            + "<html lang=\"fr\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\"/>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>\n"
            + "<meta name=\"color-scheme\" content=\"light\"/>\n"
            + "<meta name=\"format-detection\" content=\"telephone=no,date=no,address=no\"/>\n"
            + "<title>Schedy</title>\n"
            + "</head>\n"
            + "<body style=\"margin:0;padding:0;background-color:#FFFFFF;"
            + "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;"
            + "-webkit-text-size-adjust:100%;color:#1F2937;\">\n"

            + "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;color:#FFFFFF;\">"
            + name + ", mise \u00e0 jour de vos acc\u00e8s Schedy. / Schedy account update."
            + "&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;</div>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);"
            + "font-size:0;line-height:0;\">&nbsp;</td></tr>\n"
            + "<tr><td style=\"padding:32px 32px 24px;\">\n" + CID_LOGO_IMG + "\n</td></tr>\n"

            + buildSection(name, link, "",
                "Mise \u00e0 jour de vos acc\u00e8s Schedy",
                "Bonjour",
                "Votre profil utilisateur sur Schedy a \u00e9t\u00e9 mis \u00e0 jour. Vous disposez d\u00e9sormais des acc\u00e8s relatifs au r\u00f4le de <strong style=\"color:#047857;\">Manager</strong>.",
                "Ce changement vous permet d\u2019acc\u00e9der aux fonctionnalit\u00e9s de gestion suivantes\u00a0:",
                new String[]{
                    "\u00c9dition et supervision des plannings d\u2019\u00e9quipe.",
                    "Validation des pointages et suivi des pr\u00e9sences.",
                    "Traitement des demandes de cong\u00e9s et d\u2019absences."
                },
                "Ces outils sont d\u00e8s \u00e0 pr\u00e9sent disponibles sur votre interface habituelle.",
                "Acc\u00e9der \u00e0 mon espace",
                "", "")

            + "<tr><td style=\"padding:0 32px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"border-top:1px solid #E5E7EB;\"></td></tr></table></td></tr>\n"

            + buildSection(name, link, "",
                "Schedy account update \u2014 Manager role assigned",
                "Dear",
                "Your Schedy user profile has been updated. You have been assigned the <strong style=\"color:#047857;\">Manager</strong> role.",
                "This update grants you access to the following management tools:",
                new String[]{
                    "Team schedule oversight and editing.",
                    "Attendance tracking and time-clock validation.",
                    "Leave and absence request processing."
                },
                "These features are now available within your standard workspace.",
                "Access my workspace",
                "", "")

            // FOOTER
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background-color:#F9FAFB;\">\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr>\n"
            + "<td style=\"vertical-align:middle;\">\n"
            + "<p style=\"margin:0 0 2px;font-size:13px;font-weight:700;color:#1F2937;\">Schedy</p>\n"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;line-height:1.4;\">"
            + "Planning, pointage et cong\u00e9s / Scheduling, time clock &amp; leave</p>\n"
            + "</td>\n"
            + "<td align=\"right\" style=\"vertical-align:middle;font-size:11px;color:#D1D5DB;\">"
            + "\u00a9 " + year + " Schedy</td>\n"
            + "</tr></table>\n"
            + "</td></tr>\n"

            + "</table>\n"
            + "</body></html>";
    }

    private String buildInvitationHtml(String name, String link) {
        String linkFr = link + "&lang=fr";
        String linkEn = link + "&lang=en";
        String year = String.valueOf(java.time.Year.now().getValue());
        String h = String.valueOf(expiryHours);

        return "<!DOCTYPE html>\n"
            + "<html lang=\"fr\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\"/>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>\n"
            + "<meta name=\"color-scheme\" content=\"light\"/>\n"
            + "<meta name=\"format-detection\" content=\"telephone=no,date=no,address=no\"/>\n"
            + "<title>Schedy</title>\n"
            + "</head>\n"
            + "<body style=\"margin:0;padding:0;background-color:#FFFFFF;"
            + "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;"
            + "-webkit-text-size-adjust:100%;color:#1F2937;\">\n"

            // Hidden preheader
            + "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;color:#FFFFFF;\">"
            + name + ", activation de votre compte Schedy. / Schedy account activation."
            + "&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;</div>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);"
            + "font-size:0;line-height:0;\">&nbsp;</td></tr>\n"
            + "<tr><td style=\"padding:32px 32px 24px;\">\n" + CID_LOGO_IMG + "\n</td></tr>\n"

            + buildSection(name, linkFr, h,
                "Activation de votre compte utilisateur",
                "Bonjour",
                "Un compte utilisateur a \u00e9t\u00e9 cr\u00e9\u00e9 pour vous sur <strong style=\"color:#047857;\">Schedy</strong> pour la gestion de vos plannings et de votre pointage.",
                "",
                new String[]{},
                "Pour acc\u00e9der \u00e0 votre espace personnel et consulter vos horaires, vous devez pr\u00e9alablement d\u00e9finir votre mot de passe\u00a0:",
                "D\u00e9finir mon mot de passe",
                "Ce lien est valide pendant <strong>" + h + " heures</strong>. En cas d\u2019expiration, merci de vous rapprocher de votre responsable.",
                "")

            + "<tr><td style=\"padding:0 32px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"border-top:1px solid #E5E7EB;\"></td></tr></table></td></tr>\n"

            + buildSection(name, linkEn, h,
                "Schedy user account activation",
                "Dear",
                "A user account has been created for you on <strong style=\"color:#047857;\">Schedy</strong> for schedule management and time tracking.",
                "",
                new String[]{},
                "To access your personal dashboard and view your shifts, please set your password using the button below:",
                "Set my password",
                "This link is valid for <strong>" + h + " hours</strong>. Should it expire, please contact your manager.",
                "")

            // ══════════════ FOOTER ══════════════
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background-color:#F9FAFB;\">\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr>\n"
            + "<td style=\"vertical-align:middle;\">\n"
            + "<p style=\"margin:0 0 2px;font-size:13px;font-weight:700;color:#1F2937;\">Schedy</p>\n"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;line-height:1.4;\">"
            + "Planning, pointage et cong\u00e9s / Scheduling, time clock &amp; leave</p>\n"
            + "</td>\n"
            + "<td align=\"right\" style=\"vertical-align:middle;font-size:11px;color:#D1D5DB;\">"
            + "\u00a9 " + year + " Schedy</td>\n"
            + "</tr></table>\n"
            + "<p style=\"margin:12px 0 0;font-size:10px;color:#D1D5DB;\">"
            + "FR: <a href=\"" + linkFr + "\" style=\"color:#9CA3AF;text-decoration:underline;\">lien direct</a>"
            + " &nbsp;|&nbsp; EN: <a href=\"" + linkEn + "\" style=\"color:#9CA3AF;text-decoration:underline;\">direct link</a></p>\n"
            + "</td></tr>\n"

            + "</table>\n"
            + "</body></html>";
    }

    private String buildSection(String name, String ctaLink, String hours,
                                String title, String greeting,
                                String intro, String featureIntro,
                                String[] features, String closingText,
                                String btnText,
                                String expiryText, String ignoreText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr><td style=\"padding:28px 32px 0;\">\n");

        // Title
        sb.append("<p style=\"margin:0 0 16px;font-size:18px;font-weight:700;color:#047857;line-height:1.3;\">")
          .append(title).append("</p>\n");

        // Greeting
        sb.append("<p style=\"margin:0 0 12px;font-size:15px;color:#1F2937;line-height:1.65;\">")
          .append(greeting).append(" ").append(name).append(",</p>\n");

        // Intro
        sb.append("<p style=\"margin:0 0 8px;font-size:15px;color:#4B5563;line-height:1.65;\">")
          .append(intro).append("</p>\n");

        // Feature intro — only rendered when non-empty
        if (featureIntro != null && !featureIntro.isBlank()) {
            sb.append("<p style=\"margin:0 0 12px;font-size:15px;color:#4B5563;line-height:1.65;\">")
              .append(featureIntro).append("</p>\n");
        }

        // Features
        for (String feat : features) {
            sb.append(buildFeatureRow("#10B981", feat));
        }

        // Closing text before CTA
        if (closingText != null && !closingText.isBlank()) {
            sb.append("<p style=\"margin:20px 0 0;font-size:15px;color:#4B5563;line-height:1.65;\">")
              .append(closingText.replace("\n\n", "<br/><br/>")).append("</p>\n");
        }

        // CTA
        sb.append("<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" ")
          .append("style=\"margin:24px 0;\">\n<tr><td>\n")
          .append("<a href=\"").append(ctaLink).append("\" target=\"_blank\" ")
          .append("style=\"display:inline-block;background-color:#047857;color:#FFFFFF;")
          .append("font-size:15px;font-weight:600;text-decoration:none;padding:13px 36px;")
          .append("border-radius:6px;font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;\">")
          .append(escapeHtml(btnText)).append("</a>\n")
          .append("</td></tr></table>\n");

        // Expiry + security — only rendered when the text is non-empty
        if (expiryText != null && !expiryText.isBlank()) {
            sb.append("<p style=\"margin:0 0 4px;font-size:13px;color:#6B7280;line-height:1.5;\">")
              .append(expiryText).append("</p>\n");
        }
        if (ignoreText != null && !ignoreText.isBlank()) {
            sb.append("<p style=\"margin:0;font-size:13px;color:#9CA3AF;line-height:1.5;\">")
              .append(ignoreText).append("</p>\n");
        }

        sb.append("</td></tr>\n");
        return sb.toString();
    }

    private String buildAdminInvitationHtml(String orgName, String link) {
        String linkFr = link + "&lang=fr";
        String linkEn = link + "&lang=en";
        String year = String.valueOf(java.time.Year.now().getValue());
        String h = String.valueOf(expiryHours);

        return "<!DOCTYPE html>\n"
            + "<html lang=\"fr\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\"/>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>\n"
            + "<meta name=\"color-scheme\" content=\"light\"/>\n"
            + "<meta name=\"format-detection\" content=\"telephone=no,date=no,address=no\"/>\n"
            + "<title>Schedy</title>\n"
            + "</head>\n"
            + "<body style=\"margin:0;padding:0;background-color:#FFFFFF;"
            + "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;"
            + "-webkit-text-size-adjust:100%;color:#1F2937;\">\n"
            + "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;color:#FFFFFF;\">"
            + "Activation de votre acc\u00e8s administrateur - " + orgName + " / Administrator account activation - " + orgName
            + "&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;</div>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);"
            + "font-size:0;line-height:0;\">&nbsp;</td></tr>\n"
            + "<tr><td style=\"padding:32px 32px 24px;\">\n" + CID_LOGO_IMG + "\n</td></tr>\n"

            + buildSection(orgName, linkFr, h,
                "Activation de votre acc\u00e8s administrateur",
                "Bonjour",
                "L\u2019espace d\u2019administration pour l\u2019organisation <strong style=\"color:#047857;\">" + orgName + "</strong> a \u00e9t\u00e9 configur\u00e9 sur Schedy.",
                "",
                new String[]{},
                "Votre profil administrateur vous permet de g\u00e9rer les param\u00e8tres de l\u2019organisation, de configurer les sites et de superviser l\u2019ensemble des plannings et du personnel.\n\nVeuillez finaliser la configuration de votre compte en d\u00e9finissant votre mot de passe via le bouton ci-dessous\u00a0:",
                "D\u00e9finir mon mot de passe",
                "Ce lien de s\u00e9curit\u00e9 expirera dans <strong>" + h + " heures</strong>. Pass\u00e9 ce d\u00e9lai, veuillez contacter le support pour un nouvel envoi.",
                "")

            + "<tr><td style=\"padding:0 32px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"border-top:1px solid #E5E7EB;\"></td></tr></table></td></tr>\n"

            + buildSection(orgName, linkEn, h,
                "Administrator account activation",
                "Dear Administrator",
                "The administration workspace for <strong style=\"color:#047857;\">" + orgName + "</strong> has been successfully set up on Schedy.",
                "",
                new String[]{},
                "Your administrator profile grants you access to organization settings, site configuration, and full oversight of schedules and personnel.\n\nPlease finalize your account setup by creating your password via the link below:",
                "Set my password",
                "This secure link will expire in <strong>" + h + " hours</strong>. If the link expires, please contact support to request a new one.",
                "")

            // Footer
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background-color:#F9FAFB;\">\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr>\n"
            + "<td style=\"vertical-align:middle;\">\n"
            + "<p style=\"margin:0 0 2px;font-size:13px;font-weight:700;color:#1F2937;\">Schedy</p>\n"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;line-height:1.4;\">"
            + "Planning, pointage et cong\u00e9s / Scheduling, time clock &amp; leave</p>\n"
            + "</td>\n"
            + "<td align=\"right\" style=\"vertical-align:middle;font-size:11px;color:#D1D5DB;\">"
            + "\u00a9 " + year + " Schedy</td>\n"
            + "</tr></table>\n"
            + "<p style=\"margin:12px 0 0;font-size:10px;color:#D1D5DB;\">"
            + "FR: <a href=\"" + linkFr + "\" style=\"color:#9CA3AF;text-decoration:underline;\">lien direct</a>"
            + " &nbsp;|&nbsp; EN: <a href=\"" + linkEn + "\" style=\"color:#9CA3AF;text-decoration:underline;\">direct link</a></p>\n"
            + "</td></tr>\n"
            + "</table>\n"
            + "</body></html>";
    }

    private String buildPasswordResetHtml(String name, String rawToken) {
        String linkFr = frontendUrl + "/set-password?resetToken=" + rawToken + "&lang=fr";
        String linkEn = frontendUrl + "/set-password?resetToken=" + rawToken + "&lang=en";
        String year = String.valueOf(java.time.Year.now().getValue());

        return "<!DOCTYPE html>\n"
            + "<html lang=\"fr\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\"/>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>\n"
            + "<meta name=\"color-scheme\" content=\"light\"/>\n"
            + "<meta name=\"format-detection\" content=\"telephone=no,date=no,address=no\"/>\n"
            + "<title>Schedy</title>\n"
            + "</head>\n"
            + "<body style=\"margin:0;padding:0;background-color:#FFFFFF;"
            + "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;"
            + "-webkit-text-size-adjust:100%;color:#1F2937;\">\n"

            // Hidden preheader
            + "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;color:#FFFFFF;\">"
            + name + ", r\u00e9initialisez votre mot de passe Schedy. / Reset your Schedy password."
            + "&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;</div>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);"
            + "font-size:0;line-height:0;\">&nbsp;</td></tr>\n"
            + "<tr><td style=\"padding:32px 32px 24px;\">\n" + CID_LOGO_IMG + "\n</td></tr>\n"

            // FR section
            + buildSection(name, linkFr, "1",
                "R\u00e9initialisation de votre mot de passe",
                "Bonjour",
                "Nous avons re\u00e7u une demande de r\u00e9initialisation du mot de passe associ\u00e9 \u00e0 votre compte <strong style=\"color:#047857;\">Schedy</strong>.",
                "",
                new String[]{},
                "Cliquez sur le bouton ci-dessous pour d\u00e9finir un nouveau mot de passe. Ce lien est \u00e0 usage unique.",
                "R\u00e9initialiser mon mot de passe",
                "Ce lien expire dans <strong>1 heure</strong>. Pass\u00e9 ce d\u00e9lai, vous devrez refaire une demande.",
                "Si vous n\u2019\u00eates pas \u00e0 l\u2019origine de cette demande, ignorez cet email. Votre mot de passe ne sera pas modifi\u00e9.")

            + "<tr><td style=\"padding:0 32px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"border-top:1px solid #E5E7EB;\"></td></tr></table></td></tr>\n"

            // EN section
            + buildSection(name, linkEn, "1",
                "Password reset",
                "Dear",
                "We received a request to reset the password for your <strong style=\"color:#047857;\">Schedy</strong> account.",
                "",
                new String[]{},
                "Click the button below to set a new password. This link is single-use.",
                "Reset my password",
                "This link expires in <strong>1 hour</strong>. After that, you will need to request a new one.",
                "If you did not request a password reset, please ignore this email. Your password will not be changed.")

            // Footer
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background-color:#F9FAFB;\">\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr>\n"
            + "<td style=\"vertical-align:middle;\">\n"
            + "<p style=\"margin:0 0 2px;font-size:13px;font-weight:700;color:#1F2937;\">Schedy</p>\n"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;line-height:1.4;\">"
            + "Planning, pointage et cong\u00e9s / Scheduling, time clock &amp; leave</p>\n"
            + "</td>\n"
            + "<td align=\"right\" style=\"vertical-align:middle;font-size:11px;color:#D1D5DB;\">"
            + "\u00a9 " + year + " Schedy</td>\n"
            + "</tr></table>\n"
            + "<p style=\"margin:12px 0 0;font-size:10px;color:#D1D5DB;\">"
            + "FR: <a href=\"" + linkFr + "\" style=\"color:#9CA3AF;text-decoration:underline;\">lien direct</a>"
            + " &nbsp;|&nbsp; EN: <a href=\"" + linkEn + "\" style=\"color:#9CA3AF;text-decoration:underline;\">direct link</a></p>\n"
            + "</td></tr>\n"
            + "</table>\n"
            + "</body></html>";
    }

    private String build2faCodeHtml(String name, String code, String minutes) {
        String year = String.valueOf(java.time.Year.now().getValue());
        // Format code with spaces: "123456" → "1 2 3 4 5 6"
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) spaced.append(" \u00a0 ");
            spaced.append(code.charAt(i));
        }

        return "<!DOCTYPE html>\n"
            + "<html lang=\"fr\">\n<head>\n<meta charset=\"UTF-8\"/>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>\n"
            + "<title>Schedy</title>\n</head>\n"
            + "<body style=\"margin:0;padding:0;background-color:#FFFFFF;"
            + "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;"
            + "color:#1F2937;\">\n"
            + "<div style=\"display:none;max-height:0;overflow:hidden;font-size:1px;color:#FFFFFF;\">"
            + "Votre code : " + code + " / Your code: " + code + "&zwnj;&nbsp;&zwnj;</div>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);"
            + "font-size:0;line-height:0;\">&nbsp;</td></tr>\n"
            + "<tr><td style=\"padding:32px 32px 24px;\">\n" + CID_LOGO_IMG + "\n</td></tr>\n"

            // FR
            + "<tr><td style=\"padding:0 32px 24px;\">\n"
            + "<p style=\"margin:0 0 8px;font-size:20px;font-weight:700;color:#1F2937;\">V\u00e9rification de connexion</p>\n"
            + "<p style=\"margin:0 0 6px;font-size:15px;color:#4B5563;line-height:1.65;\">Bonjour " + name + ",</p>\n"
            + "<p style=\"margin:0 0 20px;font-size:15px;color:#4B5563;line-height:1.65;\">"
            + "Une tentative de connexion a \u00e9t\u00e9 d\u00e9tect\u00e9e sur votre compte Schedy. "
            + "Veuillez saisir le code ci-dessous pour confirmer votre identit\u00e9\u00a0:</p>\n"
            // Code display
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td align=\"center\" style=\"padding:20px 0;\">\n"
            + "<div style=\"display:inline-block;padding:16px 32px;background-color:#F3F4F6;"
            + "border:2px solid #E5E7EB;border-radius:8px;\">\n"
            + "<span style=\"font-size:32px;font-weight:800;color:#047857;letter-spacing:0.3em;"
            + "font-family:'Courier New',monospace;\">" + spaced + "</span>\n"
            + "</div>\n"
            + "</td></tr></table>\n"
            + "<p style=\"margin:0;font-size:13px;color:#6B7280;line-height:1.5;\">"
            + "Ce code est valable <strong>" + minutes + " minutes</strong>. "
            + "Si vous n\u2019\u00eates pas \u00e0 l\u2019origine de cette connexion, ignorez cet email.</p>\n"
            + "</td></tr>\n"

            // Separator
            + "<tr><td style=\"padding:0 32px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"border-top:1px solid #E5E7EB;\"></td></tr></table></td></tr>\n"

            // EN
            + "<tr><td style=\"padding:24px 32px;\">\n"
            + "<p style=\"margin:0 0 8px;font-size:20px;font-weight:700;color:#1F2937;\">Login verification</p>\n"
            + "<p style=\"margin:0 0 6px;font-size:15px;color:#4B5563;line-height:1.65;\">Dear " + name + ",</p>\n"
            + "<p style=\"margin:0 0 20px;font-size:15px;color:#4B5563;line-height:1.65;\">"
            + "A login attempt has been detected on your Schedy account. "
            + "Please enter the code below to verify your identity:</p>\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td align=\"center\" style=\"padding:20px 0;\">\n"
            + "<div style=\"display:inline-block;padding:16px 32px;background-color:#F3F4F6;"
            + "border:2px solid #E5E7EB;border-radius:8px;\">\n"
            + "<span style=\"font-size:32px;font-weight:800;color:#047857;letter-spacing:0.3em;"
            + "font-family:'Courier New',monospace;\">" + spaced + "</span>\n"
            + "</div>\n"
            + "</td></tr></table>\n"
            + "<p style=\"margin:0;font-size:13px;color:#6B7280;line-height:1.5;\">"
            + "This code is valid for <strong>" + minutes + " minutes</strong>. "
            + "If you did not initiate this login, please ignore this email.</p>\n"
            + "</td></tr>\n"

            // Footer
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background-color:#F9FAFB;\">\n"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr><td style=\"vertical-align:middle;\">\n"
            + "<p style=\"margin:0 0 2px;font-size:13px;font-weight:700;color:#1F2937;\">Schedy</p>\n"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;\">Planning, pointage et cong\u00e9s / Scheduling, time clock &amp; leave</p>\n"
            + "</td><td align=\"right\" style=\"font-size:11px;color:#D1D5DB;\">\u00a9 " + year + " Schedy</td>\n"
            + "</tr></table>\n</td></tr>\n"
            + "</table>\n</body></html>";
    }

    private String buildFeatureRow(String dotColor, String text) {
        return "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n"
            + "<tr>\n"
            + "<td style=\"width:20px;vertical-align:top;padding:4px 0;\">"
            + "<div style=\"width:6px;height:6px;border-radius:50%;background-color:" + dotColor + ";"
            + "margin-top:7px;\"></div></td>\n"
            + "<td style=\"padding:4px 0;font-size:14px;color:#374151;line-height:1.55;\">"
            + escapeHtml(text) + "</td>\n"
            + "</tr></table>\n";
    }

    // ── Plan B : Absences imprévues ─────────────────────────────────

    public void sendAbsenceSignaleeEmail(String recipientEmail, String recipientName,
                                          com.schedy.entity.AbsenceImprevue absence, String employeNom) {
        String subject = "Absence impr\u00e9vue signal\u00e9e \u2014 " + escapeHtml(employeNom)
                + " / Unplanned absence reported";
        String appLink = frontendUrl + "/planning";
        String html = "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"/></head>"
            + "<body style=\"margin:0;padding:0;background:#fff;font-family:Arial,sans-serif;color:#1F2937;\">"
            + "<table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);\"></td></tr>"
            + "<tr><td style=\"padding:32px;\">"
            + "<p style=\"font-size:18px;font-weight:700;color:#047857;margin:0 0 16px;\">Absence impr\u00e9vue signal\u00e9e</p>"
            + "<p style=\"font-size:15px;margin:0 0 12px;\">Bonjour " + escapeHtml(recipientName) + ",</p>"
            + "<p style=\"font-size:15px;color:#4B5563;line-height:1.6;margin:0 0 16px;\">"
            + "<strong>" + escapeHtml(employeNom) + "</strong> a signal\u00e9 une absence impr\u00e9vue pour le "
            + "<strong style=\"color:#047857;\">" + absence.getDateAbsence() + "</strong>.</p>"
            + buildFeatureRow("#10B981", "Motif : " + absence.getMotif())
            + (absence.getMessageEmploye() != null && !absence.getMessageEmploye().isBlank()
                ? buildFeatureRow("#6B7280", "Message : " + absence.getMessageEmploye()) : "")
            + buildFeatureRow("#F59E0B", absence.getCreneauIds().size() + " cr\u00e9neau(x) impact\u00e9(s)")
            + "<p style=\"font-size:15px;color:#4B5563;margin:16px 0 0;\">Veuillez valider ou refuser cette absence :</p>"
            + "<table role=\"presentation\" style=\"margin:24px 0;\"><tr><td>"
            + "<a href=\"" + appLink + "\" style=\"display:inline-block;background:#047857;color:#fff;"
            + "font-size:15px;font-weight:600;text-decoration:none;padding:13px 36px;border-radius:6px;\">"
            + "Voir dans l'application</a></td></tr></table>"
            + "</td></tr>"
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background:#F9FAFB;\">"
            + "<p style=\"margin:0;font-size:13px;font-weight:700;\">Schedy</p>"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;\">Planning, pointage et cong\u00e9s</p>"
            + "</td></tr></table></body></html>";
        sendHtmlEmail(recipientEmail, subject, html);
    }

    public void sendAbsenceValideeEmail(String recipientEmail, String recipientName, String date) {
        String subject = "Votre absence du " + date + " a \u00e9t\u00e9 accept\u00e9e / Absence accepted";
        String html = "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"/></head>"
            + "<body style=\"margin:0;padding:0;background:#fff;font-family:Arial,sans-serif;color:#1F2937;\">"
            + "<table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);\"></td></tr>"
            + "<tr><td style=\"padding:32px;\">"
            + "<p style=\"font-size:18px;font-weight:700;color:#047857;margin:0 0 16px;\">Absence valid\u00e9e</p>"
            + "<p style=\"font-size:15px;margin:0 0 12px;\">Bonjour " + escapeHtml(recipientName) + ",</p>"
            + "<p style=\"font-size:15px;color:#4B5563;line-height:1.6;margin:0 0 16px;\">"
            + "Votre absence du <strong>" + date + "</strong> a \u00e9t\u00e9 valid\u00e9e par votre manager. "
            + "Un rempla\u00e7ant sera assign\u00e9 si n\u00e9cessaire.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background:#F9FAFB;\">"
            + "<p style=\"margin:0;font-size:13px;font-weight:700;\">Schedy</p>"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;\">Planning, pointage et cong\u00e9s</p>"
            + "</td></tr></table></body></html>";
        sendHtmlEmail(recipientEmail, subject, html);
    }

    public void sendAbsenceRefuseeEmail(String recipientEmail, String recipientName,
                                         String date, String noteRefus) {
        String subject = "Votre absence du " + date + " a \u00e9t\u00e9 refus\u00e9e / Absence declined";
        String html = "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"/></head>"
            + "<body style=\"margin:0;padding:0;background:#fff;font-family:Arial,sans-serif;color:#1F2937;\">"
            + "<table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);\"></td></tr>"
            + "<tr><td style=\"padding:32px;\">"
            + "<p style=\"font-size:18px;font-weight:700;color:#DC2626;margin:0 0 16px;\">Absence refus\u00e9e</p>"
            + "<p style=\"font-size:15px;margin:0 0 12px;\">Bonjour " + escapeHtml(recipientName) + ",</p>"
            + "<p style=\"font-size:15px;color:#4B5563;line-height:1.6;margin:0 0 8px;\">"
            + "Votre absence du <strong>" + date + "</strong> a \u00e9t\u00e9 refus\u00e9e. "
            + "Vous \u00eates attendu(e) \u00e0 votre poste.</p>"
            + (noteRefus != null && !noteRefus.isBlank()
                ? "<p style=\"font-size:14px;color:#6B7280;margin:12px 0 0;\"><strong>Motif :</strong> "
                  + escapeHtml(noteRefus) + "</p>" : "")
            + "</td></tr>"
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background:#F9FAFB;\">"
            + "<p style=\"margin:0;font-size:13px;font-weight:700;\">Schedy</p>"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;\">Planning, pointage et cong\u00e9s</p>"
            + "</td></tr></table></body></html>";
        sendHtmlEmail(recipientEmail, subject, html);
    }

    public void sendAbsenceAnnuleeEmail(String recipientEmail, String recipientName,
                                         String employeNom, String date) {
        String subject = "Absence annul\u00e9e \u2014 " + date + " / Absence cancelled";
        String html = "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"/></head>"
            + "<body style=\"margin:0;padding:0;background:#fff;font-family:Arial,sans-serif;color:#1F2937;\">"
            + "<table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td style=\"height:4px;background:linear-gradient(90deg,#6EE7B7,#10B981,#047857);\"></td></tr>"
            + "<tr><td style=\"padding:32px;\">"
            + "<p style=\"font-size:18px;font-weight:700;color:#047857;margin:0 0 16px;\">Absence annul\u00e9e</p>"
            + "<p style=\"font-size:15px;margin:0 0 12px;\">Bonjour " + escapeHtml(recipientName) + ",</p>"
            + "<p style=\"font-size:15px;color:#4B5563;line-height:1.6;\">"
            + "<strong>" + escapeHtml(employeNom) + "</strong> a annul\u00e9 son alerte d'absence pour le "
            + "<strong style=\"color:#047857;\">" + date + "</strong>. Aucune action n'est requise.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:24px 32px;border-top:1px solid #E5E7EB;background:#F9FAFB;\">"
            + "<p style=\"margin:0;font-size:13px;font-weight:700;\">Schedy</p>"
            + "<p style=\"margin:0;font-size:11px;color:#9CA3AF;\">Planning, pointage et cong\u00e9s</p>"
            + "</td></tr></table></body></html>";
        sendHtmlEmail(recipientEmail, subject, html);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
