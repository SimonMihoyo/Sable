package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.kirara.sable.common.R;
import me.kirara.sable.system.entity.SysConfig;
import me.kirara.sable.system.service.SysConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置接口 — /system/configs、/system/health
 *
 * <p><b>整类要求 {@code system:manage}</b>。这几条接口读写的 {@code sys_config} 是
 * <b>public schema 下的全局配置</b>（审计日志保留天数、扫描并发数之类，见 {@code FR-6.3.1}），
 * 不是租户级设置 —— 改它会影响所有租户，因此属于平台管理动作。</p>
 *
 * <p>{@code /system/health} 也一并在内。它目前是 TODO 桩，但"系统健康聚合"要暴露的正是
 * 内部组件的连接与积压情况，不该给未认证调用方看 —— 旧版本把整个系统模块的接口对
 * 任何登录用户开放，健康信息也是其中之一。</p>
 *
 * <p>若将来真需要给监控探针免认证访问健康检查，正确做法是把它加进
 * {@code sable.auth.whitelist} <b>并</b>同步 {@code ControllerAuthorizationTest}
 * 的例外清单 —— 那是一次显式的决定，而不是顺手放开。</p>
 */
@RestController
@RequestMapping("/system")
@PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).SYSTEM_MANAGE)")
public class ConfigController {

    private final SysConfigService sysConfigService;

    public ConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/configs")
    public R<List<SysConfig>> list(@RequestParam(required = false) String group) {
        return R.ok(sysConfigService.list(
                new LambdaQueryWrapper<SysConfig>().eq(group != null, SysConfig::getConfigGroup, group)));
    }

    @PutMapping("/configs")
    public R<Void> update(@RequestBody List<SysConfig> configs) {
        sysConfigService.updateBatchById(configs);
        return R.ok();
    }

    @GetMapping("/health")
    public R<Object> health() {
        // TODO 聚合各子系统健康状态
        return R.ok();
    }
}
