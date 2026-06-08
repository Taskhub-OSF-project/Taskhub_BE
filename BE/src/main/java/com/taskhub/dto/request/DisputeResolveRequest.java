package com.taskhub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body để hirer giải quyết dispute.
 * action: RELEASE_PAYMENT | REQUEST_REVISION | ESCALATE
 */
@Data
public class DisputeResolveRequest {

    @NotNull(message = "action is required")
    private DisputeAction action;

    public enum DisputeAction {
        RELEASE_PAYMENT,
        REQUEST_REVISION,
        ESCALATE
    }
}
