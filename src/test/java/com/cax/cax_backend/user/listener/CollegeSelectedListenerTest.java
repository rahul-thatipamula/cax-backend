package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollegeSelectedListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private CollegeSelectedListener collegeSelectedListener;

    @Test
    void handleCollegeSelectedEvent_CallsEmailService() {
        // Arrange
        User user = User.builder()
                .userId("student123")
                .email("student@cax.edu")
                .name("Alex Student")
                .build();
        CollegeSelectedEvent event = new CollegeSelectedEvent(this, user);

        // Act
        collegeSelectedListener.handleCollegeSelectedEvent(event);

        // Assert
        verify(emailService, times(1)).sendIdVerificationRequestEmail(user);
    }
}
