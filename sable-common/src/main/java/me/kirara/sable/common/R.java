package me.kirara.sable.common;

public class R<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;

    private R() {
        this.timestamp = System.currentTimeMillis();
        // traceId 取自日志上下文（由 TraceIdFilter 写入），语义是"这次请求在日志里的编号"：
        // 调用方拿到响应后可以直接拿它去日志里定位同一次请求的全部记录。
        // 非请求线程（启动过程、定时任务）构造的 R 没有它，会被 non_null 序列化策略略过。
        this.traceId = TraceContext.getTraceId();
    }

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = ok();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
