package me.kirara.sable.discovery.dto;

/**
 * 数据源连通性测试结果。
 *
 * <p>是"探测结果"而不是错误：连接失败时接口仍然返回 {@code code=0}，由
 * {@link #success} 区分成败 —— 客户端要展示的是"连不上 + 原因"，而不是一个异常弹窗。
 * 数据源不存在才是错误（40201）。</p>
 */
public class ConnectionTestResult {

    private boolean success;

    /** 成功时是简短说明，失败时是给人看的原因（不含口令、不含完整连接串）。 */
    private String message;

    /** 服务端版本，例如 {@code PostgreSQL 16.15}；未连上时为 null。 */
    private String serverVersion;

    /** 本次探测耗时（毫秒）。 */
    private long durationMs;

    public ConnectionTestResult() {
    }

    private ConnectionTestResult(boolean success, String message, String serverVersion, long durationMs) {
        this.success = success;
        this.message = message;
        this.serverVersion = serverVersion;
        this.durationMs = durationMs;
    }

    /** 连接成功。 */
    public static ConnectionTestResult success(String serverVersion, long durationMs) {
        return new ConnectionTestResult(true, "连接成功", serverVersion, durationMs);
    }

    /**
     * 连接失败。
     *
     * @param message 失败原因，调用方保证其中不含口令与完整连接串
     */
    public static ConnectionTestResult failure(String message, long durationMs) {
        return new ConnectionTestResult(false, message, null, durationMs);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
