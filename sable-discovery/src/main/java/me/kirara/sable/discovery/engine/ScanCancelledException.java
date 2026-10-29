package me.kirara.sable.discovery.engine;

/** 扫描被取消 —— 由引擎抛出，服务层据此结束任务且不覆盖 CANCELLED 状态。 */
public class ScanCancelledException extends RuntimeException {

    public ScanCancelledException() {
        super("扫描任务已取消");
    }
}
