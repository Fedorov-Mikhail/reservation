package com.mikey.reservation.resource.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResourceRequest(
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String name,
        @Size(max = 2000)
        @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String description,
        @Size(max = 255)
        @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String location) {}

