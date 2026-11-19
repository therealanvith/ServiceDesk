package com.servicedesk.api.dto;

import java.util.List;

public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static <T> PagedResponse<T> of(List<T> allItems, int page, int size) {
    int safeSize = Math.max(1, size);
    int safePage = Math.max(0, page);
    int totalElements = allItems.size();
    int totalPages = (int) Math.ceil((double) totalElements / safeSize);

    int fromIndex = (int) Math.min((long) safePage * safeSize, totalElements);
    int toIndex = (int) Math.min((long) fromIndex + safeSize, totalElements);
    List<T> pageContent = allItems.subList(fromIndex, toIndex);

    return new PagedResponse<>(pageContent, safePage, safeSize, totalElements, totalPages);
  }
}
