package com.mikey.reservation.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ApiException.invalid("page must be non-negative and size must be between 1 and 100.");
    }
}

