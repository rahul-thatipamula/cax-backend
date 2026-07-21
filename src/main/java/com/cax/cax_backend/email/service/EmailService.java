package com.cax.cax_backend.email.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.settings.repository.SettingsRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final SettingsRepository settingsRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private boolean isEmailEnabled(String userId) {
        if (userId == null) return true;
        return settingsRepository.findAllByUserId(userId).stream().findFirst()
                .map(s -> s.isNotificationsEnabled() && s.isEmailNotificationsEnabled())
                .orElse(true);
    }

    /**
     * Send a professional greeting email to the newly signed up user.
     */
    public void sendGreetingEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send greeting email: user or email is null/empty");
            return;
        }
        if (user.isBlocked()) {
            log.info("User {} is blocked. Skipping greeting email.", user.getUserId());
            return;
        }
        if (!isEmailEnabled(user.getUserId())) {
            log.debug("Email notifications disabled for user {}. Skipping greeting email.", user.getUserId());
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String subject = "Welcome to CAX – Your Account is Active";

        String textContent = String.format(
            "Hello %s,\n\n" +
            "Welcome to CAX, your digital campus platform. We are pleased to inform you that your account has been successfully created and is now active.\n\n" +
            "CAX is designed to streamline your campus experience. Here are the core features now available to you:\n" +
            "• Discover Groups: Explore clubs, communities, and societies within your college.\n" +
            "• Stay Updated: Receive notifications for campus events, announcements, and student bulletins.\n\n" +
            "You can access CAX via the web platform or download our Android application:\n" +
            "• Access Web Version: https://caxone.in\n" +
            "• Download Android Version: https://play.google.com/store/apps/details?id=com.axiviontech.cax\n\n" +
            "Best regards,\n" +
            "The CAX Team\n\n" +
            "---\n" +
            "© 2026 CAX. All rights reserved.\n" +
            "This is an automated operational message regarding your account registration.",
            name
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textContent, false);

            mailSender.send(message);
            log.info("Greeting email successfully sent to {} ({})", to, user.getUserId());
        } catch (MessagingException e) {
            log.error("Failed to send greeting email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending greeting email to {}: ", to, e);
        }
    }

    /**
     * Send a professional newsletter subscription confirmation email.
     */
    public void sendNewsletterConfirmationEmail(String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Cannot send newsletter confirmation email: email is null/empty");
            return;
        }
        String normalizedEmail = toEmail != null ? toEmail.toLowerCase().trim() : "";
        Optional<User> uOpt = userRepository.findByEmail(normalizedEmail);
        if (uOpt.isPresent()) {
            User user = uOpt.get();
            if (user.isBlocked()) {
                log.info("User associated with {} is blocked. Skipping newsletter confirmation.", toEmail);
                return;
            }
            if (!isEmailEnabled(user.getUserId())) {
                log.debug("Email notifications disabled for user {}. Skipping newsletter confirmation.", user.getUserId());
                return;
            }
        }

        String subject = "Subscription Confirmed: Cax Bulletins Digest";

        String htmlContent = String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FAF8F5; color: #191816; margin: 0; padding: 40px 20px; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #E6DFD5; border-radius: 4px; padding: 40px; box-shadow: 4px 4px 0px #E6DFD5; }\n" +
            "        h1 { font-family: Georgia, serif; font-size: 28px; font-weight: 300; line-height: 1.2; margin: 0 0 20px 0; color: #191816; }\n" +
            "        h1 span { color: #2C227F; font-style: italic; font-weight: normal; }\n" +
            "        p { font-size: 15px; line-height: 1.6; margin: 0 0 16px 0; color: #70695E; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Cax <span>Bulletins Digest</span></h1>\n" +
            "        <p>Hello,</p>\n" +
            "        <p>Thank you for subscribing to the Cax Bulletins Digest!</p>\n" +
            "        <p>You will now receive periodic notifications regarding campus build releases, feature updates, and student bulletins directly in your inbox.</p>\n" +
            "        <p>If you wish to opt out at any time, you can unsubscribe by clicking the link at the bottom of our updates.</p>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 Cax. All rights reserved.<br>This is an automated confirmation of your email subscription.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>"
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Newsletter subscription confirmation email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send newsletter confirmation email to {}: ", toEmail, e);
        } catch (Exception e) {
            log.error("Unexpected error sending newsletter confirmation email to {}: ", toEmail, e);
        }
    }

    /**
     * Send a professional event registration status update email.
     */
    public void sendEventRegistrationStatusEmail(EventParticipant participant, Event event) {
        if (participant == null || participant.getEmail() == null || participant.getEmail().isBlank()) {
            log.warn("Cannot send registration email: participant or email is null/empty");
            return;
        }
        String normalizedEmail = participant.getEmail() != null ? participant.getEmail().toLowerCase().trim() : "";
        Optional<User> uOpt = userRepository.findByEmail(normalizedEmail);
        if (uOpt.isPresent()) {
            User user = uOpt.get();
            if (user.isBlocked()) {
                log.info("Participant {} is blocked. Skipping registration status email.", participant.getEmail());
                return;
            }
            if (!isEmailEnabled(user.getUserId())) {
                log.debug("Email notifications disabled for user {}. Skipping registration status email.", user.getUserId());
                return;
            }
        }

        String to = participant.getEmail();
        String name = participant.getName() != null ? participant.getName() : "Student";
        String eventName = event != null ? event.getName() : "the event";
        boolean isApproved = "VERIFIED".equals(participant.getStatus());

        String subject = isApproved
                ? "Your Registration is Confirmed: " + eventName
                : "Update: Registration for " + eventName;

        String statusTitle = isApproved ? "Registration Approved" : "Registration Rejected";
        String statusColor = isApproved ? "#10B981" : "#EF4444"; // Green vs Red

        StringBuilder detailMessage = new StringBuilder();
        if (isApproved) {
            detailMessage.append("<p>Your registration request for <strong>").append(eventName).append("</strong> has been successfully approved.</p>");
            if (participant.getTicketCode() != null && !participant.getTicketCode().isEmpty()) {
                detailMessage.append("<div class=\"highlight-box\" style=\"border-left-color: #10B981;\">");
                detailMessage.append("<p style=\"font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: #70695E; margin-bottom: 6px;\">Your Ticket Code</p>");
                detailMessage.append("<p style=\"font-size: 18px; font-weight: 700; font-family: monospace; color: #2C227F;\">").append(participant.getTicketCode()).append("</p>");
                detailMessage.append("<p style=\"font-size: 12px; color: #70695E; margin-top: 6px;\">Please present this code at check-in when entering the event.</p>");
                detailMessage.append("</div>");
            }
        } else {
            detailMessage.append("<p>Your registration request for <strong>").append(eventName).append("</strong> has been declined by the event organizer.</p>");
            detailMessage.append("<p>If you believe this is an error, please verify your details or contact the organizers directly.</p>");
        }

        String htmlContent = String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FAF8F5; color: #191816; margin: 0; padding: 40px 20px; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #E6DFD5; border-radius: 4px; padding: 40px; box-shadow: 4px 4px 0px #E6DFD5; }\n" +
            "        h1 { font-family: Georgia, serif; font-size: 28px; font-weight: 300; line-height: 1.2; margin: 0 0 10px 0; color: #191816; }\n" +
            "        h1 span { color: #2C227F; font-style: italic; font-weight: normal; }\n" +
            "        .status-badge { display: inline-block; padding: 6px 14px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: %s; border: 1px solid %s; background-color: #ffffff; border-radius: 20px; margin-bottom: 24px; }\n" +
            "        p { font-size: 15px; line-height: 1.6; margin: 0 0 16px 0; color: #70695E; }\n" +
            "        .highlight-box { background-color: #FAF8F5; border-left: 3px solid #2C227F; padding: 16px; margin: 24px 0; border-radius: 2px; }\n" +
            "        .highlight-box p { margin: 0; }\n" +
            "        .btn { display: inline-block; background-color: #2C227F; color: #FAF8F5 !important; text-decoration: none; padding: 12px 24px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; border-radius: 4px; box-shadow: 3px 3px 0px #E6DFD5; margin-top: 10px; margin-bottom: 30px; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Event <span>Registration</span></h1>\n" +
            "        <div class=\"status-badge\">%s</div>\n" +
            "        <p>Hello %s,</p>\n" +
            "        %s\n" +
            "        <p>You can access details via our platforms below:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\" style=\"margin-right: 12px;\">Web Version</a>\n" +
            "        <a href=\"https://play.google.com/store/apps/details?id=com.axiviontech.cax\" class=\"btn\">Android Version</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 CAX. All rights reserved.<br>This is an automated operational notification regarding your registration status.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>",
            statusColor,
            statusColor,
            statusTitle,
            name,
            detailMessage.toString()
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Event registration status email successfully sent to {} for event {}", to, eventName);
        } catch (MessagingException e) {
            log.error("Failed to send event registration status email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending event registration status email to {}: ", to, e);
        }
    }

    /**
     * Send a professional club leadership assignment email.
     */
    @Async("taskExecutor")
    public void sendOrganizationLeaderAssignmentEmail(User user, Organization organization, String role) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send club leadership email: user or email is null/empty");
            return;
        }
        if (user.isBlocked()) {
            log.info("User {} is blocked. Skipping club leadership email.", user.getUserId());
            return;
        }
        if (!isEmailEnabled(user.getUserId())) {
            log.debug("Email notifications disabled for user {}. Skipping club leadership email.", user.getUserId());
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String orgTypeLabel = organization != null && organization.getType() != null
                ? organization.getType().getDisplayName().toLowerCase()
                : "organization";
        String orgName = organization != null ? organization.getName() : "the " + orgTypeLabel;
        String subject = String.format("Leadership Assignment: %s of %s", role, orgName);

        String htmlContent = String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FAF8F5; color: #191816; margin: 0; padding: 40px 20px; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #E6DFD5; border-radius: 4px; padding: 40px; box-shadow: 4px 4px 0px #E6DFD5; }\n" +
            "        h1 { font-family: Georgia, serif; font-size: 28px; font-weight: 300; line-height: 1.2; margin: 0 0 20px 0; color: #191816; }\n" +
            "        h1 span { color: #2C227F; font-style: italic; font-weight: normal; }\n" +
            "        p { font-size: 15px; line-height: 1.6; margin: 0 0 16px 0; color: #70695E; }\n" +
            "        .highlight-box { background-color: #FAF8F5; border-left: 3px solid #2C227F; padding: 16px; margin: 24px 0; border-radius: 2px; }\n" +
            "        .highlight-box p { margin: 0; font-size: 14px; font-weight: 500; color: #191816; }\n" +
            "        .btn { display: inline-block; background-color: #2C227F; color: #FAF8F5 !important; text-decoration: none; padding: 12px 24px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; border-radius: 4px; box-shadow: 3px 3px 0px #E6DFD5; margin-top: 10px; margin-bottom: 30px; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Organization <span>Leadership Assignment</span></h1>\n" +
            "        <p>Hello %s,</p>\n" +
            "        <p>We are pleased to inform you that you have been assigned as the <strong>%s</strong> of the official %s <strong>%s</strong>.</p>\n" +
            "        <div class=\"highlight-box\">\n" +
            "            <p>Role: %s</p>\n" +
            "            <p>Organization: %s</p>\n" +
            "            <p>Assigned Privileges: Event Management, Member Management, Organization Settings, Posts, and Memories.</p>\n" +
            "        </div>\n" +
            "        <p>To manage your organization, please access the platform via our Web Console or Android application below:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\" style=\"margin-right: 12px;\">Web Version</a>\n" +
            "        <a href=\"https://play.google.com/store/apps/details?id=com.axiviontech.cax\" class=\"btn\">Android Version</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 CAX. All rights reserved.<br>This is an automated operational notification regarding your leadership assignment.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>",
            name,
            role,
            orgTypeLabel,
            orgName,
            role,
            orgName
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Leadership assignment email successfully sent to {} for organization {}", to, orgName);
        } catch (MessagingException e) {
            log.error("Failed to send leadership assignment email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending leadership assignment email to {}: ", to, e);
        }
    }

    /**
     * Send a beautiful HTML email notifying the student of their promotion to Super Student.
     */
    public void sendSuperStudentPromotionEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send Super Student promotion email: user or email is null/empty");
            return;
        }
        if (user.isBlocked()) {
            log.info("User {} is blocked. Skipping Super Student promotion email.", user.getUserId());
            return;
        }
        if (!isEmailEnabled(user.getUserId())) {
            log.debug("Email notifications disabled for user {}. Skipping Super Student promotion email.", user.getUserId());
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String collegeName = (user.getCollegeDetails() != null && user.getCollegeDetails().getCollegeName() != null)
                ? user.getCollegeDetails().getCollegeName()
                : "your university";
        String subject = "Role Update: Promoted to Super Student";

        String htmlContent = String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FAF8F5; color: #191816; margin: 0; padding: 40px 20px; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #E6DFD5; border-radius: 4px; padding: 40px; box-shadow: 4px 4px 0px #E6DFD5; }\n" +
            "        h1 { font-family: Georgia, serif; font-size: 28px; font-weight: 300; line-height: 1.2; margin: 0 0 10px 0; color: #191816; }\n" +
            "        h1 span { color: #D97706; font-style: italic; font-weight: normal; }\n" +
            "        .status-badge { display: inline-block; padding: 6px 14px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: #D97706; border: 1px solid rgba(217, 119, 6, 0.2); background-color: #FEF3C7; border-radius: 20px; margin-bottom: 24px; }\n" +
            "        p { font-size: 15px; line-height: 1.6; margin: 0 0 16px 0; color: #70695E; }\n" +
            "        .highlight-box { background-color: #FFFDF9; border-left: 3px solid #D97706; padding: 16px; margin: 24px 0; border-radius: 2px; box-shadow: 2px 2px 0px #F5EFEB; }\n" +
            "        .highlight-box p { margin: 0; font-size: 14px; font-weight: 550; color: #7F5F00; }\n" +
            "        .btn { display: inline-block; background-color: #2C227F; color: #FAF8F5 !important; text-decoration: none; padding: 12px 24px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; border-radius: 4px; box-shadow: 3px 3px 0px #E6DFD5; margin-top: 10px; margin-bottom: 30px; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>CAX <span>Super Student</span></h1>\n" +
            "        <div class=\"status-badge\">Role Promoted</div>\n" +
            "        <p>Hello %s,</p>\n" +
            "        <p>We are pleased to inform you that you have been officially promoted to the role of <strong>Super Student</strong> for <strong>%s</strong>.</p>\n" +
            "        <p>As a Super Student, you now hold coordinator privileges to help manage campus activities and services.</p>\n" +
            "        <div class=\"highlight-box\">\n" +
            "            <p>• Profile Indicator: A verification badge is now visible next to your name on feed posts and comments.</p>\n" +
            "            <p>• Campus Management: Access to coordinator features to manage campus activities and services.</p>\n" +
            "        </div>\n" +
            "        <p>To access your coordinator dashboard, please use the platform links below:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\" style=\"margin-right: 12px;\">Web Version</a>\n" +
            "        <a href=\"https://play.google.com/store/apps/details?id=com.axiviontech.cax\" class=\"btn\">Android Version</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 CAX. All rights reserved.<br>This is an automated operational notification regarding your student role upgrade.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>",
            name,
            collegeName
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Super Student promotion email successfully sent to {} ({})", to, user.getUserId());
        } catch (MessagingException e) {
            log.error("Failed to send Super Student promotion email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending Super Student promotion email to {}: ", to, e);
        }
    }

    /**
     * Send a professional HTML email notifying the student of their demotion back to standard Student role.
     */
    public void sendSuperStudentDemotionEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send Super Student demotion email: user or email is null/empty");
            return;
        }
        if (user.isBlocked()) {
            log.info("User {} is blocked. Skipping Super Student demotion email.", user.getUserId());
            return;
        }
        if (!isEmailEnabled(user.getUserId())) {
            log.debug("Email notifications disabled for user {}. Skipping Super Student demotion email.", user.getUserId());
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String subject = "Role Update: Super Student Status Revoked";

        String htmlContent = String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FAF8F5; color: #191816; margin: 0; padding: 40px 20px; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #E6DFD5; border-radius: 4px; padding: 40px; box-shadow: 4px 4px 0px #E6DFD5; }\n" +
            "        h1 { font-family: Georgia, serif; font-size: 28px; font-weight: 300; line-height: 1.2; margin: 0 0 10px 0; color: #191816; }\n" +
            "        h1 span { color: #EF4444; font-style: italic; font-weight: normal; }\n" +
            "        .status-badge { display: inline-block; padding: 6px 14px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: #EF4444; border: 1px solid rgba(239, 68, 68, 0.2); background-color: #FEF2F2; border-radius: 20px; margin-bottom: 24px; }\n" +
            "        p { font-size: 15px; line-height: 1.6; margin: 0 0 16px 0; color: #70695E; }\n" +
            "        .btn { display: inline-block; background-color: #2C227F; color: #FAF8F5 !important; text-decoration: none; padding: 12px 24px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; border-radius: 4px; box-shadow: 3px 3px 0px #E6DFD5; margin-top: 10px; margin-bottom: 30px; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>CAX <span>Role Update</span></h1>\n" +
            "        <div class=\"status-badge\">Role Updated</div>\n" +
            "        <p>Hello %s,</p>\n" +
            "        <p>This email is to notify you that your Super Student coordinator privileges have been rescinded.</p>\n" +
            "        <p>Your user role has been reverted to <strong>Student</strong>. If you believe this is in error, please contact your campus administrator or support.</p>\n" +
            "        <p>To review your account settings and profile details, please access the platform links below:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\" style=\"margin-right: 12px;\">Web Version</a>\n" +
            "        <a href=\"https://play.google.com/store/apps/details?id=com.axiviontech.cax\" class=\"btn\">Android Version</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 CAX. All rights reserved.<br>This is an automated operational notification regarding your student role update.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>",
            name
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Super Student demotion email successfully sent to {} ({})", to, user.getUserId());
        } catch (MessagingException e) {
            log.error("Failed to send Super Student demotion email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending Super Student demotion email to {}: ", to, e);
        }
    }
}
