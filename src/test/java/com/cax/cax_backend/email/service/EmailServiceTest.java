package com.cax.cax_backend.email.service;

import com.cax.cax_backend.user.model.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "notification@caxone.in");
    }

    @Test
    void sendGreetingEmail_Success() {
        // Arrange
        User user = User.builder()
                .userId("test-uid")
                .email("student@cax.edu")
                .name("Alex Student")
                .build();

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendGreetingEmail(user);

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendGreetingEmail_DoesNotSend_WhenUserOrEmailIsNull() {
        // Act & Assert for null user
        emailService.sendGreetingEmail(null);
        verify(mailSender, never()).createMimeMessage();

        // Act & Assert for user with null email
        User userNoEmail = User.builder().userId("uid").build();
        emailService.sendGreetingEmail(userNoEmail);
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendNewsletterConfirmationEmail_Success() {
        // Arrange
        String email = "subscriber@cax.edu";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendNewsletterConfirmationEmail(email);

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendNewsletterConfirmationEmail_DoesNotSend_WhenEmailIsNullOrEmpty() {
        // Act & Assert for null email
        emailService.sendNewsletterConfirmationEmail(null);
        verify(mailSender, never()).createMimeMessage();

        // Act & Assert for empty email
        emailService.sendNewsletterConfirmationEmail("");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendIdVerificationRequestEmail_Success() {
        // Arrange
        User user = User.builder()
                .userId("student123")
                .email("student@cax.edu")
                .name("Alex Student")
                .build();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendIdVerificationRequestEmail(user);

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendIdVerificationRequestEmail_DoesNotSend_WhenUserOrEmailIsNullOrEmpty() {
        // Act & Assert for null user
        emailService.sendIdVerificationRequestEmail(null);
        verify(mailSender, never()).createMimeMessage();

        // Act & Assert for null email
        User userNoEmail = User.builder().userId("student123").build();
        emailService.sendIdVerificationRequestEmail(userNoEmail);
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void testRealSmtp() {
        org.springframework.mail.javamail.JavaMailSenderImpl impl = new org.springframework.mail.javamail.JavaMailSenderImpl();
        impl.setHost("smtp.titan.email");
        impl.setPort(587);
        impl.setUsername("notification@caxone.in");
        impl.setPassword("@R1a1h1u1l1@titan");
        
        java.util.Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        
        try {
            System.out.println("TEST: Sending message...");
            jakarta.mail.internet.MimeMessage message = impl.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true);
            helper.setFrom("notification@caxone.in");
            helper.setTo("craftmvp.in@gmail.com");
            helper.setSubject("Real SMTP Test");
            helper.setText("This is a real test email from local JVM", false);
            impl.send(message);
            System.out.println("TEST: Email sent successfully!");
        } catch (Exception e) {
            System.out.println("TEST FAILURE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
