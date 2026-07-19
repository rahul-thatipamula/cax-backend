package com.cax.cax_backend.home.dto;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.user.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private List<Carousel> banners;
    private List<Organization> myOrganizations;
    private List<Event> discoverEvents;
    private List<Map<String, Object>> joinedEvents;
    private List<Map<String, Object>> upcomingEvents;
    private long unreadNotificationCount;
    private User profile;
}
