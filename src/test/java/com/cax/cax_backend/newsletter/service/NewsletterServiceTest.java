package com.cax.cax_backend.newsletter.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import com.cax.cax_backend.newsletter.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {

    @Mock
    private NewsletterSubscriptionRepository subscriptionRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NewsletterService newsletterService;

    private NewsletterSubscription sampleSubscription;

    @BeforeEach
    void setUp() {
        sampleSubscription = NewsletterSubscription.builder()
                .id("sub123")
                .email("test@cax.edu")
                .build();
    }

    @Test
    void subscribe_Success() {
        // Arrange
        String inputEmail = "  TEST@cax.edu  ";
        String cleanedEmail = "test@cax.edu";
        
        when(subscriptionRepository.findByEmail(cleanedEmail)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NewsletterSubscription.class))).thenAnswer(invocation -> {
            NewsletterSubscription saved = invocation.getArgument(0);
            saved.setId("sub123");
            return saved;
        });

        // Act
        NewsletterSubscription result = newsletterService.subscribe(inputEmail);

        // Assert
        assertNotNull(result);
        assertEquals("sub123", result.getId());
        assertEquals(cleanedEmail, result.getEmail());
        assertNotNull(result.getCreatedAt());
        verify(subscriptionRepository, times(1)).findByEmail(cleanedEmail);
        verify(subscriptionRepository, times(1)).save(any(NewsletterSubscription.class));
        verify(eventPublisher, times(1)).publishEvent(any(com.cax.cax_backend.newsletter.event.NewsletterSubscribeEvent.class));
    }

    @Test
    void subscribe_ThrowsException_WhenEmailIsEmptyOrNull() {
        // Act & Assert null email
        assertThrows(BusinessException.BadRequestException.class, () -> 
                newsletterService.subscribe(null)
        );

        // Act & Assert empty email
        assertThrows(BusinessException.BadRequestException.class, () -> 
                newsletterService.subscribe("   ")
        );

        verify(subscriptionRepository, never()).findByEmail(anyString());
        verify(subscriptionRepository, never()).save(any(NewsletterSubscription.class));
    }

    @Test
    void subscribe_ThrowsException_WhenEmailFormatIsInvalid() {
        // Act & Assert invalid format
        assertThrows(BusinessException.BadRequestException.class, () -> 
                newsletterService.subscribe("invalid-email")
        );

        assertThrows(BusinessException.BadRequestException.class, () -> 
                newsletterService.subscribe("invalid@email")
        );

        verify(subscriptionRepository, never()).findByEmail(anyString());
        verify(subscriptionRepository, never()).save(any(NewsletterSubscription.class));
    }

    @Test
    void subscribe_ThrowsException_WhenEmailAlreadyExists() {
        // Arrange
        String email = "test@cax.edu";
        when(subscriptionRepository.findByEmail(email)).thenReturn(Optional.of(sampleSubscription));

        // Act & Assert
        assertThrows(BusinessException.ResourceAlreadyExistsException.class, () -> 
                newsletterService.subscribe(email)
        );

        verify(subscriptionRepository, times(1)).findByEmail(email);
        verify(subscriptionRepository, never()).save(any(NewsletterSubscription.class));
    }
}
