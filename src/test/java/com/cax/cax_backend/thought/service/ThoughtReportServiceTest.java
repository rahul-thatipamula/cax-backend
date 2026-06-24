package com.cax.cax_backend.thought.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.thought.dto.ReportedThoughtDetailDto;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtReport;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import com.cax.cax_backend.thought.repository.ThoughtReportRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ThoughtReportServiceTest {

    @Mock private ThoughtReportRepository thoughtReportRepository;
    @Mock private ThoughtRepository thoughtRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ThoughtReportService thoughtReportService;

    private Thought thought;
    private User reporter;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(thoughtReportService, "reportThreshold", 3);

        thought = Thought.builder()
                .id("thought-1")
                .userId("creator-1")
                .heading("Test Heading")
                .content("Test Content")
                .disabled(false)
                .build();

        reporter = User.builder()
                .userId("reporter-1")
                .name("John Doe")
                .email("john@doe.com")
                .build();
    }

    @Test
    public void reportThought_shouldSaveReport_whenFirstReport() {
        when(thoughtRepository.findById("thought-1")).thenReturn(Optional.of(thought));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("thought-1", "reporter-1"))
                .thenReturn(Optional.empty());
        when(userService.getUserByUserId("reporter-1")).thenReturn(reporter);
        when(thoughtReportRepository.countByPostId("thought-1")).thenReturn(1L);

        assertDoesNotThrow(() -> thoughtReportService.reportThought("reporter-1", "thought-1", "Spam"));

        verify(thoughtReportRepository, times(1)).save(any(ThoughtReport.class));
        assertFalse(thought.isDisabled());
        verify(thoughtRepository, never()).save(thought);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    public void reportThought_shouldDisableThought_whenReportThresholdReached() {
        when(thoughtRepository.findById("thought-1")).thenReturn(Optional.of(thought));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("thought-1", "reporter-1"))
                .thenReturn(Optional.empty());
        when(userService.getUserByUserId("reporter-1")).thenReturn(reporter);
        when(thoughtReportRepository.countByPostId("thought-1")).thenReturn(3L);

        assertDoesNotThrow(() -> thoughtReportService.reportThought("reporter-1", "thought-1", "Harassment"));

        verify(thoughtReportRepository, times(1)).save(any(ThoughtReport.class));
        assertTrue(thought.isDisabled());
        verify(thoughtRepository, times(1)).save(thought);
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void reportThought_shouldThrow_whenAlreadyReported() {
        ThoughtReport existing = ThoughtReport.builder()
                .postId("thought-1")
                .reporterUserId("reporter-1")
                .build();

        when(thoughtRepository.findById("thought-1")).thenReturn(Optional.of(thought));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("thought-1", "reporter-1"))
                .thenReturn(Optional.of(existing));

        BusinessException.BadRequestException ex = assertThrows(
                BusinessException.BadRequestException.class,
                () -> thoughtReportService.reportThought("reporter-1", "thought-1", "Spam")
        );
        assertEquals("You have already reported this thought.", ex.getMessage());
        verify(thoughtReportRepository, never()).save(any(ThoughtReport.class));
    }

    @Test
    public void getReportedThoughtsForAdmin_shouldGroupAndSortReports() {
        ThoughtReport r1 = ThoughtReport.builder().postId("thought-1").build();
        ThoughtReport r2 = ThoughtReport.builder().postId("thought-1").build();
        ThoughtReport r3 = ThoughtReport.builder().postId("thought-2").build();

        when(thoughtReportRepository.findAllByOrderByReportedAtDesc())
                .thenReturn(List.of(r1, r2, r3));
        when(thoughtRepository.findById("thought-1")).thenReturn(Optional.of(thought));
        Thought thought2 = Thought.builder().id("thought-2").heading("Thought 2").build();
        when(thoughtRepository.findById("thought-2")).thenReturn(Optional.of(thought2));

        List<ReportedThoughtDetailDto> details = thoughtReportService.getReportedThoughtsForAdmin();

        assertEquals(2, details.size());
        assertEquals("thought-1", details.get(0).getThought().getId());
        assertEquals(2, details.get(0).getReportCount());
        assertEquals("thought-2", details.get(1).getThought().getId());
        assertEquals(1, details.get(1).getReportCount());
    }

    @Test
    public void dismissReports_shouldClearReportsAndEnableThought() {
        thought.setDisabled(true);
        when(thoughtRepository.findById("thought-1")).thenReturn(Optional.of(thought));

        thoughtReportService.dismissReports("thought-1");

        verify(thoughtReportRepository, times(1)).deleteByPostId("thought-1");
        assertFalse(thought.isDisabled());
        verify(thoughtRepository, times(1)).save(thought);
    }
}
