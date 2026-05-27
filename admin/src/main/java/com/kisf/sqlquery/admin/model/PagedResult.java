package com.kisf.sqlquery.admin.model;

import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

public class PagedResult<T> {

    private List<T> content = new ArrayList<>();
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;

    public static <T> PagedResult<T> of(Page<T> page) {
        PagedResult<T> result = new PagedResult<>();
        result.content = page.hasContent() ? page.getContent() : new ArrayList<>();
        result.totalElements = page.getTotalElements();
        result.totalPages = page.getTotalPages();
        result.number = page.getNumber();
        result.size = page.getSize();
        return result;
    }

    public List<T> getContent() { return content; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int getNumber() { return number; }
    public int getSize() { return size; }
}
