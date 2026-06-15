package com.cax.cax_backend.email.service;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
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

    @Mock
    private UserRepository userRepository;

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

}
