package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.kirara.sable.common.R;
import me.kirara.sable.system.entity.SysConfig;
import me.kirara.sable.system.service.SysConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统配置接口 — /system/configs、/system/health */
@RestController
@RequestMapping("/system")
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
