package me.kirara.sable.sdk;

/** 审计事件上报客户端抽象。 */
public interface SableAuditClient {

    /**
     * 上报一条审计事件。
     *
     * @param event 审计事件
     * @return 是否上报成功
     */
    boolean report(AuditEvent event);
}
