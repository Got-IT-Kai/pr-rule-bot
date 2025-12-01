package com.code.review.domain.model;

public record PrContext(
        PrType type,
        String title,
        String focus
) {
    public static PrContext from(String title) {
        PrType type = PrType.detect(title);
        return new PrContext(type, title, type.focus());
    }
}
