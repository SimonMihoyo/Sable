package me.kirara.sable.common.page;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应结构。
 */
public class PageResult<T> implements Serializable {

    private long page;
    private long size;
    private long total;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long page, long size, long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.records = records == null ? Collections.emptyList() : records;
    }

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(page, size, 0L, Collections.emptyList());
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
