package com.cax.cax_backend.studentpost.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.studentpost.dto.ReportedPostDetailDto;
import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.model.ThoughtReport;
import com.cax.cax_backend.studentpost.repository.StudentPostRepository;
import com.cax.cax_backend.studentpost.repository.ThoughtReportRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ThoughtReportServiceTest {

    @Mock
    private ThoughtReportRepository thoughtReportRepository;

    @Mock
    private StudentPostRepository studentPostRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ThoughtReportService thoughtReportService;

    private StudentPost post;
    private User reporter;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(thoughtReportService, "reportThreshold", 3);

        post = StudentPost.builder()
                .id("post-1")
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
    public void reportPost_shouldSaveReport_whenFirstReport() {
        when(studentPostRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("post-1", "reporter-1"))
                .thenReturn(Optional.empty());
        when(userService.getUserByUserId("reporter-1")).thenReturn(reporter);
        when(thoughtReportRepository.countByPostId("post-1")).thenReturn(1L);

        assertDoesNotThrow(() -> thoughtReportService.reportPost("reporter-1", "post-1", "Spam"));

        verify(thoughtReportRepository, times(1)).save(any(ThoughtReport.class));
        assertFalse(post.isDisabled());
        verify(studentPostRepository, never()).save(post);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    public void reportPost_shouldDisablePost_whenReportThresholdReached() {
        when(studentPostRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("post-1", "reporter-1"))
                .thenReturn(Optional.empty());
        when(userService.getUserByUserId("reporter-1")).thenReturn(reporter);
        when(thoughtReportRepository.countByPostId("post-1")).thenReturn(3L); // Reaches threshold of 3

        assertDoesNotThrow(() -> thoughtReportService.reportPost("reporter-1", "post-1", "Harassment"));

        verify(thoughtReportRepository, times(1)).save(any(ThoughtReport.class));
        assertTrue(post.isDisabled());
        verify(studentPostRepository, times(1)).save(post);
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void reportPost_shouldThrowException_whenAlreadyReported() {
        ThoughtReport existingReport = ThoughtReport.builder()
                .postId("post-1")
                .reporterUserId("reporter-1")
                .build();

        when(studentPostRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(thoughtReportRepository.findByPostIdAndReporterUserId("post-1", "reporter-1"))
                .thenReturn(Optional.of(existingReport));

        BusinessException.BadRequestException exception = assertThrows(
                BusinessException.BadRequestException.class,
                () -> thoughtReportService.reportPost("reporter-1", "post-1", "Spam")
        );

        assertEquals("You have already reported this thought.", exception.getMessage());
        verify(thoughtReportRepository, never()).save(any(ThoughtReport.class));
    }

    @Test
    public void getReportedPostsForAdmin_shouldGroupAndSortReports() {
        ThoughtReport report1 = ThoughtReport.builder().postId("post-1").build();
        ThoughtReport report2 = ThoughtReport.builder().postId("post-1").build();
        ThoughtReport report3 = ThoughtReport.builder().postId("post-2").build();

        when(thoughtReportRepository.findAllByOrderByReportedAtDesc())
                .thenReturn(List.of(report1, report2, report3));
        when(studentPostRepository.findById("post-1")).thenReturn(Optional.of(post));
        
        StudentPost post2 = StudentPost.builder().id("post-2").heading("Post 2").build();
        when(studentPostRepository.findById("post-2")).thenReturn(Optional.of(post2));

        List<ReportedPostDetailDto> details = thoughtReportService.getReportedPostsForAdmin();

        assertEquals(2, details.size());
        assertEquals("post-1", details.get(0).getPost().getId());
        assertEquals(2, details.get(0).getReportCount()); // Sorts desc: post-1 has 2 reports

        assertEquals("post-2", details.get(1).getPost().getId());
        assertEquals(1, details.get(1).getReportCount());
    }

    @Test
    public void dismissReports_shouldClearReportsAndEnablePost() {
        post.setDisabled(true); // Auto-disabled post
        when(studentPostRepository.findById("post-1")).thenReturn(Optional.of(post));

        thoughtReportService.dismissReports("post-1");

        verify(thoughtReportRepository, times(1)).deleteByPostId("post-1");
        assertFalse(post.isDisabled());
        verify(studentPostRepository, times(1)).save(post);
    }
}
