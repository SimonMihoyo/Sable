package me.kirara.sable.classify.controller;

import me.kirara.sable.classify.dto.StandardItem;
import me.kirara.sable.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 分类分级标准接口 — /classify/standards */
@RestController
@RequestMapping("/classify/standards")
public class StandardController {

    @GetMapping
    public R<List<StandardItem>> standards(@RequestParam String type) {
        // TODO 加载内置 PIPL / DSL 分类分级标准
        return R.ok(List.of());
    }
}
