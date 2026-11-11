package com.connectone.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String subject,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 40) String category,
            @NotBlank String priority) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String body, boolean internal) {
    }

    public record TransitionRequest(@NotBlank String status) {
    }

    public record AssignmentRequest(long agentUserId) {
    }
}
