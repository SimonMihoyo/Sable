package me.kirara.sable.discovery.dto;

/** 数据源连通性测试结果。 */
public class ConnectionTestResult {

    private boolean success;
    private String message;
    private long durationMs;

    public ConnectionTestResult() {
    }

    public ConnectionTestResult(boolean success, String message, long durationMs) {
        this.success = success;
        this.message = message;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
