package com.flashgif.favorites.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long total
) {
    public static <S, T> PagedResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PagedResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements());
    }
}
