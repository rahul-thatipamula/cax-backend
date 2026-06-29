package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded document stored inside {@link Event#collaborators}.
 * Collaborators are informational only — event management stays with the creator's organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCollaborator {

    private String organizationId;
    private String organizationName;
    private String organizationLogo;

    /** College of the collaborating org — used for cross-college profile navigation gating on the client. */
    private String collegeId;
}
