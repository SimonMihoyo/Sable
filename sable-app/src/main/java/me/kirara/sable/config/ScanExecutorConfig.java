package me.kirara.sable.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 扫描任务线程池 —— 供 {@code ScanTaskService} 异步执行元数据扫描。
 *
 * <p><b>为什么是显式线程池而不是 {@code @Async}：</b>扫描任务需要把请求线程里的租户上下文
 * 显式搬到执行线程（见 {@code ScanTaskServiceImpl#runScan}），用显式 {@code Executor}
 * 提交任务能一眼看到"上下文是在哪里搬的"；{@code @Async} 会把这段藏进代理里，
 * 而且自调用失效、异常处理要靠 {@code AsyncUncaughtExceptionHandler}，排查成本更高。</p>
 *
 * <p><b>为什么拒绝策略是 CallerRuns：</b>队列满时让提交线程自己跑，会让那一次请求变慢，
 * 但任务不会丢；换成就地抛错则会出现"库里有一条 PENDING、永远没人执行"的僵尸任务
 * （虽然服务层对拒绝也做了兜底标记 FAILED，能跑就不抛）。</p>
 */
@Configuration
public class ScanExecutorConfig {

    /**
     * 扫描专用线程池。
     *
     * <p>池子刻意小：扫描是重 IO 操作，同时跑太多会挤占数据库连接，
     * 也会让目标库的元数据查询互相拖慢。需要更多并发时改这里，并同时评估
     * "扫描并发 × 每租户连接数" 与数据库 {@code max_connections} 的关系。</p>
     */
    @Bean("scanTaskExecutor")
    public ThreadPoolTaskExecutor scanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("scan-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 停机时给在跑的扫描一点收尾时间，避免留下永远 RUNNING 的任务
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
