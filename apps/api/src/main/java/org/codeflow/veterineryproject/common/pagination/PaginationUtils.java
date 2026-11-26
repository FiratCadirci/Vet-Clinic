package org.codeflow.veterineryproject.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtils {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PaginationUtils() {
    }

    public static Pageable createPageable(Integer page, Integer size) {
        return createPageable(page, size, Sort.unsorted());
    }

    public static Pageable createPageable(Integer page, Integer size, Sort sort) {
        int resolvedPage = page == null || page < 0 ? DEFAULT_PAGE : page;
        int resolvedSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(resolvedPage, resolvedSize, sort);
    }
}

