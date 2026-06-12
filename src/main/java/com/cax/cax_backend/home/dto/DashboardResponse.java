package com.cax.cax_backend.home.dto;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.club.model.Club;
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
    private List<Club> myClubs;
    private List<Event> discoverEvents;
    private List<Map<String, Object>> joinedEvents;
    private long unreadNotificationCount;
    private User profile;
}
