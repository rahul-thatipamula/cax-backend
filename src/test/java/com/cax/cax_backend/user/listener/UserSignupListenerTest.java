package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.UserSignupEvent;
import com.cax.cax_backend.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSignupListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserSignupListener userSignupListener;

    @Test
    void handleUserSignupEvent_CallsEmailService() {
        // Arrange
        User user = User.builder()
                .userId("test-uid")
                .email("test@cax.edu")
                .name("Alex Student")
                .build();
        UserSignupEvent event = new UserSignupEvent(this, user);

        // Act
        userSignupListener.handleUserSignupEvent(event);

        // Assert
        verify(emailService, times(1)).sendGreetingEmail(user);
    }
}
