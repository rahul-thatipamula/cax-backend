package com.cax.cax_backend.boost.model;

public enum BoostStatus {
    PENDING,   // queued, waiting for next scheduler window
    ACTIVE,    // currently being shown in trending
    COMPLETED  // window expired; eligible for reboost
}
