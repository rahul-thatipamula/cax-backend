package com.cax.cax_backend.email.service;

import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.user.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send a professional greeting email to the newly signed up user.
     */
    public void sendGreetingEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send greeting email: user or email is null/empty");
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String subject = "Welcome to Cax!";

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
            "        ul { margin: 0 0 24px 0; padding-left: 20px; color: #70695E; font-size: 15px; line-height: 1.6; }\n" +
            "        li { margin-bottom: 8px; }\n" +
            "        .btn { display: inline-block; background-color: #2C227F; color: #FAF8F5 !important; text-decoration: none; padding: 12px 24px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; border-radius: 4px; box-shadow: 3px 3px 0px #E6DFD5; margin-top: 10px; margin-bottom: 30px; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Welcome to <span>Cax</span></h1>\n" +
            "        <p>Hello %s,</p>\n" +
            "        <p>We are thrilled to welcome you to Cax, your digital campus companion. Your account is now active and ready.</p>\n" +
            "        <p>With Cax, coordinating campus life is simpler than ever:</p>\n" +
            "        <ul>\n" +
            "            <li><strong>Discover Clubs:</strong> Connect with campus organizations and manage officer privileges.</li>\n" +
            "            <li><strong>Stay Updated:</strong> Never miss out on campus events, announcements, and bulletins.</li>\n" +
            "            <li><strong>Digital Credentials:</strong> Access student ID verifications cleanly in your browser.</li>\n" +
            "        </ul>\n" +
            "        <p>Get started by exploring your dashboard:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\">Launch Dashboard</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 Cax. All rights reserved.<br>This is an automated operational message regarding your registration.</p>\n" +
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
     * Send a professional student ID verification request email.
     */
    public void sendIdVerificationRequestEmail(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send ID verification request email: user or email is null/empty");
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        String collegeName = (user.getCollegeDetails() != null && user.getCollegeDetails().getCollegeName() != null)
                ? user.getCollegeDetails().getCollegeName()
                : "your selected college";
        String subject = "Verify your Student ID on Cax";

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
            "        ul { margin: 0 0 24px 0; padding-left: 20px; color: #70695E; font-size: 15px; line-height: 1.6; }\n" +
            "        li { margin-bottom: 8px; }\n" +
            "        .highlight-box { background-color: #FAF8F5; border-left: 3px solid #2C227F; padding: 16px; margin: 24px 0; border-radius: 2px; }\n" +
            "        .highlight-box p { margin: 0; font-size: 14px; font-weight: 500; color: #191816; }\n" +
            "        .footer { border-top: 1px solid #E6DFD5; padding-top: 24px; margin-top: 32px; font-size: 11px; color: #70695E; font-family: monospace; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Verify your <span>Student ID</span></h1>\n" +
            "        <p>Hello %s,</p>\n" +
            "        <p>You have successfully selected <strong>%s</strong> as your campus. Welcome to your campus portal!</p>\n" +
            "        <p>To ensure a safe and verified environment for all students, we require you to verify your student status. Verifying your student ID card unlocks full access to all features, including:</p>\n" +
            "        <ul>\n" +
            "            <li>Registering for official student clubs</li>\n" +
            "            <li>Securing RSVPs to campus events & workshops</li>\n" +
            "            <li>Trading securely on the Campus Marketplace</li>\n" +
            "            <li>Assigning club leadership & coordinator privileges</li>\n" +
            "        </ul>\n" +
            "        <div class=\"highlight-box\">\n" +
            "            <p>Please upload a clear photo of your student ID card in the Cax Web Console or Cax Mobile App to verify your active enrollment.</p>\n" +
            "        </div>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 Cax. All rights reserved.<br>This is an automated operational notification regarding your student status.</p>\n" +
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
            log.info("Student ID verification request email sent to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send ID verification request email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending ID verification request email to {}: ", to, e);
        }
    }

    /**
     * Send a professional ID card status update email.
     */
    public void sendIdCardStatusEmail(User user, IDCard idCard) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send ID card status email: user or email is null/empty");
            return;
        }

        String to = user.getEmail();
        String name = user.getName() != null ? user.getName() : "Student";
        boolean isApproved = idCard.getStatus() == com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus.APPROVED;
        
        String subject = isApproved 
            ? "Your CAX ID has been Approved! \uD83C\uDF93" 
            : "Action Required: CAX ID Verification Rejected ⚠️";

        String statusTitle = isApproved ? "Verification Approved" : "Verification Rejected";
        String statusColor = isApproved ? "#10B981" : "#EF4444"; // Green vs Red
        String statusEmoji = isApproved ? "✅" : "⚠️";

        StringBuilder detailMessage = new StringBuilder();
        if (isApproved) {
            detailMessage.append("<p>Congratulations! Your Student ID card verification request has been successfully reviewed and approved.</p>");
            detailMessage.append("<p>Your digital CAX ID is now verified. You have unlocked full operational access to official clubs, event registrations, student coordinator privileges, and the marketplace.</p>");
            if (idCard.getVerificationNotes() != null && !idCard.getVerificationNotes().isBlank()) {
                detailMessage.append("<div class=\"highlight-box\" style=\"border-left-color: #10B981;\">");
                detailMessage.append("<p style=\"font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: #70695E; margin-bottom: 6px;\">Verification Notes</p>");
                detailMessage.append("<p style=\"font-size: 14px; font-weight: normal; color: #191816;\">").append(idCard.getVerificationNotes()).append("</p>");
                detailMessage.append("</div>");
            }
        } else {
            detailMessage.append("<p>We were unable to verify your student status based on the ID card document uploaded.</p>");
            detailMessage.append("<p>As a result, your ID verification status has been set to rejected. To restore verified access, please review the reason below and submit a new scanned copy of your ID card in the settings panel.</p>");
            if (idCard.getRejectionReason() != null && !idCard.getRejectionReason().isBlank()) {
                detailMessage.append("<div class=\"highlight-box\" style=\"border-left-color: #EF4444; background-color: #FEF2F2;\">");
                detailMessage.append("<p style=\"font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: #991B1B; margin-bottom: 6px;\">Reason for Rejection</p>");
                detailMessage.append("<p style=\"font-size: 14px; font-weight: 650; color: #991B1B;\">").append(idCard.getRejectionReason()).append("</p>");
                detailMessage.append("</div>");
            }
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
            "        <h1>CAX <span>ID Status</span></h1>\n" +
            "        <div class=\"status-badge\">%s %s</div>\n" +
            "        <p>Hello %s,</p>\n" +
            "        %s\n" +
            "        <p>Check the console for full details:</p>\n" +
            "        <a href=\"https://caxone.in\" class=\"btn\">Open Dashboard</a>\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 Cax. All rights reserved.<br>This is an automated operational notification regarding your credential status.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>",
            statusColor,
            statusColor,
            statusEmoji,
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
            log.info("ID Card status email successfully sent to {} ({})", to, idCard.getUserId());
        } catch (MessagingException e) {
            log.error("Failed to send ID Card status email to {}: ", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending ID Card status email to {}: ", to, e);
        }
    }
}
