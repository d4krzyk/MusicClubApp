package com.musicclubapp.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Wycinek "od N-tego, tyle sztuk" - {@code PageRequest} umie tylko numer strony razy jej rozmiar, a
 * tablica "Dla ciebie" skleja kilka list i potrzebuje zaczac w dowolnym miejscu.
 */
public final class OffsetPageable implements Pageable {

    private final long offset;
    private final int limit;

    public OffsetPageable(long offset, int limit) {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("offset >= 0 i limit >= 1, a jest " + offset + ", " + limit);
        }
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + limit, limit);
    }

    @Override
    public Pageable previousOrFirst() {
        return new OffsetPageable(Math.max(0, offset - limit), limit);
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, limit);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable((long) pageNumber * limit, limit);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
