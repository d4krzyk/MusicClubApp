package com.musicclubapp.dto;

import java.util.List;

/** Strona wynikow; {@code next} wraca w kolejnym zapytaniu jako {@code pos} (null = to byla ostatnia strona). */
public record GifPageResponse(List<GifResult> items, String next) {
}
