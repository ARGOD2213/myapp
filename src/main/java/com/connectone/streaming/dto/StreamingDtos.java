package com.connectone.streaming.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StreamingDtos {

    private StreamingDtos() {
    }

    public record ContentRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 40) String contentCode,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank String contentType,
            @NotBlank @Size(max = 12) String rating,
            @Min(1) int durationMinutes,
            @NotBlank @Size(max = 500) String playbackUrl,
            boolean active) {
    }
}
