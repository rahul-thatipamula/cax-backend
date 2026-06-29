package com.cax.cax_backend.home.controller;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.carousel.repository.CarouselRepository;
import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.service.OrganizationService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.service.EventService;
import com.cax.cax_backend.home.dto.DashboardResponse;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final CarouselRepository carouselRepository;
    private final OrganizationService organizationService;
    private final EventService eventService;
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        
        String userId = (String) auth.getPrincipal();
        User user = userService.getUserByUserId(userId);
        String collegeId = (user.getCollegeDetails() != null) ? user.getCollegeDetails().getCollegeId() : null;

        // 1. Fetch Banners
        List<Carousel> banners;
        if (collegeId != null && !collegeId.isBlank()) {
            banners = new ArrayList<>(carouselRepository.findByCollegeIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc());
            banners.addAll(carouselRepository.findByCollegeIdAndIsActiveTrueOrderByDisplayOrderAsc(collegeId));
            banners.sort(Comparator.comparingInt(Carousel::getDisplayOrder));
        } else {
            banners = carouselRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        }

        // 2. Fetch Joined Clubs
        List<OrganizationMember> memberships = organizationService.getUserOrganizationMemberships(userId);
        List<String> organizationIds = memberships.stream()
                .map(OrganizationMember::getOrganizationId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        List<Organization> myOrganizations = organizationService.getOrganizationsByIds(organizationIds);

        // 3. Fetch Discover Events (Limit to top 5)
        List<Event> discoverEvents = eventService.discoverEvents(userId);
        if (discoverEvents.size() > 5) {
            discoverEvents = discoverEvents.subList(0, 5);
        }

        // 4. Fetch Joined Events (Limit to top 5)
        List<Map<String, Object>> joinedEvents = eventService.getJoinedEvents(userId);
        if (joinedEvents.size() > 5) {
            joinedEvents = joinedEvents.subList(0, 5);
        }

        // 5. Fetch Unread Notification Count
        long unreadCount = notificationService.getUnreadCount(userId);

        DashboardResponse response = DashboardResponse.builder()
                .banners(banners)
                .myOrganizations(myOrganizations)
                .discoverEvents(discoverEvents)
                .joinedEvents(joinedEvents)
                .unreadNotificationCount(unreadCount)
                .profile(user)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
