package com.cax.cax_backend.newsletter.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.newsletter.event.NewsletterSubscribeEvent;
import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsletterSubscribeListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NewsletterSubscribeListener newsletterSubscribeListener;

    @Test
    void handleNewsletterSubscribeEvent_CallsEmailService() {
        // Arrange
        NewsletterSubscription subscription = NewsletterSubscription.builder()
                .id("sub123")
                .email("subscriber@cax.edu")
                .build();
        NewsletterSubscribeEvent event = new NewsletterSubscribeEvent(this, subscription);

        // Act
        newsletterSubscribeListener.handleNewsletterSubscribeEvent(event);

        // Assert
        verify(emailService, times(1)).sendNewsletterConfirmationEmail("subscriber@cax.edu");
    }
}
