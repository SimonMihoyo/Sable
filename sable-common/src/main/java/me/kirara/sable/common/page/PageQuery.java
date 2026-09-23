package me.kirara.sable.common.page;

/**
 * 分页查询基类 — 所有列表接口的查询参数可继承此类。
 */
public class PageQuery {

    private static final long MAX_SIZE = 200L;

    /** 页码，从 1 开始。 */
    private long page = 1L;

    /** 每页条数。 */
    private long size = 20L;

    /** 通用关键字模糊查询。 */
    private String keyword;

    public long getPage() {
        return page < 1 ? 1 : page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        if (size < 1) {
            return 20L;
        }
        return Math.min(size, MAX_SIZE);
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
