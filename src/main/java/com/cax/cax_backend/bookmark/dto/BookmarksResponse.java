package com.cax.cax_backend.bookmark.dto;

import com.cax.cax_backend.organization.model.OrganizationPost;
import com.cax.cax_backend.thought.model.Thought;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Hydrated bookmarks — each list ordered most-recently-bookmarked first. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookmarksResponse {
    @Builder.Default
    private List<Thought> thoughts = new ArrayList<>();

    @Builder.Default
    private List<OrganizationPost> organizationPosts = new ArrayList<>();
}
